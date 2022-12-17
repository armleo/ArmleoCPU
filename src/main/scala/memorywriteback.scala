package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

import io.AnsiColor._

import Instructions._
import armleocpu.utils._

class MemoryWriteback(c: CoreParams) extends Module {
  val dbus            = IO(new dbus_t(c))
  val int             = IO(Input(new InterruptsInputs))
  val debug_req_i     = IO(Input(Bool()))
  val dm_haltaddr_i   = IO(Input(UInt(c.avLen.W))) // FIXME: use this for halting
  //val debug_state_o   = IO(Output(UInt(2.W))) // FIXME: Output the state
  val rvfi            = IO(Output(new rvfi_o(c)))


  val execute_uop         = IO(Input (new execute_uop_t(c)))
  val execute_uop_valid   = IO(Input (Bool()))
  val execute_uop_accept  = IO(Output(Bool()))

  val cu_pc_in            = IO(Output(UInt(c.avLen.W)))
  val cu_cmd              = IO(Output(chiselTypeOf(controlunit_cmd.none)))
  val cu_wb_flush         = IO(Input (Bool()))
  val cu_wb_ready         = IO(Output(Bool()))

  val regs_memwb          = IO(new regs_memwb_io(c))


  val memwblog = new Logger(c.lp.coreName, f"memwb", c.core_verbose)
  // TODO: Add PTE storage for RVFI





  val tlb_invalidate_counter = RegInit(0.U(log2Ceil(c.dtlb.entries).W))
  val cache_invalidate_counter = RegInit(0.U(log2Ceil(c.dcache.entries).W))

  val csr_error_happened = RegInit(false.B)
  
  val saved_tlb_ptag      = Reg(chiselTypeOf(dtlb.s1.read_data.ptag))
  // If load then cache/tlb request. If store then request is sent to dbus
  val WB_REQUEST_WRITE_START  = 0.U(4.W)
  // Compare the tags, the cache tags, and decide where to proceed
  val WB_COMPARE              = 1.U(4.W)
  val WB_TLBREFILL            = 2.U(4.W)
  val WB_CACHEREFILL          = 3.U(4.W)

  val wbstate             = RegInit(WB_REQUEST_WRITE_START)

  /**************************************************************************/
  /*                                                                        */
  /*                Submodules                                              */
  /*                                                                        */
  /**************************************************************************/

  val csr = Module(new CSR(c))
  
  
  val dcache  = Module(new Cache(verbose = c.dcache_verbose, c = c, instName = "data$", cp = c.dcache))
  val dtlb    = Module(new TLB(verbose = c.dtlb_verbose, instName = "dtlb ", c = c, tp = c.dtlb))
  val dptw    = Module(new PTW(instName = "dptw ", c = c, tp = c.dtlb))
  val drefill = Module(new Refill(c = c, cp = c.dcache, dcache))
  val dpagefault = Module(new Pagefault(c = c))
  val loadGen = Module(new LoadGen(c))
  val storeGen = Module(new StoreGen(c))





  val wb_is_atomic =
        (execute_uop.instr === LR_W) ||
        (execute_uop.instr === LR_D) ||
        (execute_uop.instr === SC_W) ||
        (execute_uop.instr === SC_D)


  /**************************************************************************/
  /*                CSR Signals                                             */
  /**************************************************************************/
  csr.int <> int
  csr.instret_incr := false.B //
  csr.addr := execute.uop_o.instr(31, 20) // Constant
  csr.cause := 0.U // FIXME: Need to be properly set
  csr.cmd := csr_cmd.none
  csr.epc := execute.uop_o.pc
  csr.in := 0.U // FIXME: Needs to be properly connected

  


  // Used to complete the instruction
  // If br_pc_valid is set then it means that fetch needs to start from br_pc
  // Therefore command control unit to start killing the pipeline
  // and restarting from br_pc
  // We also retire instructions here, so set the rvfi_valid
  // and instret_incr
  def instr_cplt(br_pc_valid: Bool = false.B, br_pc: UInt = execute_uop.pc_plus_4): Unit = {
    execute_uop_accept := true.B
    rvfi.valid := true.B
    csr.instret_incr := true.B
    
    when(br_pc_valid) {
      cu_pc_in := br_pc
      cu_cmd := controlunit_cmd.branch
      regs_reservation := 0.U.asTypeOf(chiselTypeOf(regs_reservation)) // FIXME:
    } .otherwise {
      cu_cmd := controlunit_cmd.retire
      cu_pc_in := execute_uop.pc_plus_4
    }

    

    csr_error_happened := false.B
    wbstate := WB_REQUEST_WRITE_START // Reset the internal states
  }

  def handle_trap_like(cmd: csr_cmd.Type, cause: UInt = 0.U): Unit = {
    csr.cmd := cmd
    instr_cplt(true.B, csr.next_pc)
    assert(csr.err === false.B) // Should not be possible
  }

  when(cu_wb_flush) {
    // cu_wb_ready := true.B // FIXME: Should be false unless all dcache is invalidated
    
    cu_wb_ready := false.B

    dcache.s0.cmd              := cache_cmd.invalidate
    dcache.s0.vaddr            := cache_invalidate_counter << log2Ceil(c.dcache.entry_bytes)
    val cache_invalidate_counter_ovfl = (((cache_invalidate_counter + 1.U) % ((c.dcache.entries).U)) === 0.U)
    when(!cache_invalidate_counter_ovfl) {
      cache_invalidate_counter  := ((cache_invalidate_counter + 1.U) % ((c.dcache.entries).U))
    }

    

    dtlb.s0.cmd                := tlb_cmd.invalidate
    dtlb.s0.virt_address_top   := tlb_invalidate_counter
    val tlb_invalidate_counter_ovfl = (((tlb_invalidate_counter + 1.U) % c.dtlb.entries.U) === 0.U)
    when(!tlb_invalidate_counter_ovfl) {
      tlb_invalidate_counter    := (tlb_invalidate_counter + 1.U) % c.dtlb.entries.U
    }

    when(tlb_invalidate_counter_ovfl && cache_invalidate_counter_ovfl) {
      cu_wb_ready := true.B
      memwblog("Flush complete")
    }
  } .elsewhen(execute_uop_valid && !cu.wb_kill) {

    assert(execute_uop.pc(1, 0) === 0.U) // Make sure its aligned

    execute_uop_accept := false.B
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Debug enter logic                                 */
    /*                                                                        */
    /**************************************************************************/
    when(debug_req_i) {
      memwblog("Debug interrupt")
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Interrupt logic                                   */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(csr.int_pending_o) {
      memwblog("External Interrupt")
      handle_trap_like(csr_cmd.interrupt)
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: FETCH ERROR LOGIC                                 */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(execute_uop.ifetch_access_fault) {
      memwblog("Instruction fetch access fault")
      handle_trap_like(csr_cmd.exception, new exc_code(c).INSTR_ACCESS_FAULT)
    } .elsewhen (execute_uop.ifetch_page_fault) {
      memwblog("Instruction fetch page fault")
      handle_trap_like(csr_cmd.exception, new exc_code(c).INSTR_PAGE_FAULT)
    
    /**************************************************************************/
    /*                                                                        */
    /*                Alu/Alu-like writeback                                  */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(
      (execute_uop.instr === LUI) ||
      (execute_uop.instr === AUIPC) ||

      (execute_uop.instr === ADD) ||
      (execute_uop.instr === SUB) ||
      (execute_uop.instr === AND) ||
      (execute_uop.instr === OR)  ||
      (execute_uop.instr === XOR) ||
      (execute_uop.instr === SLL) ||
      (execute_uop.instr === SRL) ||
      (execute_uop.instr === SRA) ||
      (execute_uop.instr === SLT) ||
      (execute_uop.instr === SLTU) ||

      (execute_uop.instr === ADDI) ||
      (execute_uop.instr === SLTI) ||
      (execute_uop.instr === SLTIU) ||
      (execute_uop.instr === ANDI) ||
      (execute_uop.instr === ORI) ||
      (execute_uop.instr === XORI) ||
      (execute_uop.instr === SLLI) ||
      (execute_uop.instr === SRLI) ||
      (execute_uop.instr === SRAI)
      // TODO: Add the rest of ALU out write back 
    ) {
      memwblog("ALU-like instruction found instr=0x%x, pc=0x%x", execute_uop.instr, execute_uop.pc)
      
      

      rd_wdata := execute_uop.alu_out.asUInt()
      rd_write := true.B
      instr_cplt()


    
    /**************************************************************************/
    /*                                                                        */
    /*                JAL/JALR                                                */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(
        (execute_uop.instr === JAL) ||
        (execute_uop.instr === JALR)
    ) {
      rd_wdata := execute_uop.pc_plus_4
      rd_write := true.B

      when(execute_uop.instr === JALR) {
        val next_cu_pc = execute_uop.alu_out.asUInt() & (~(1.U(c.avLen.W)))
        instr_cplt(true.B, next_cu_pc)
        memwblog("JALR instr=0x%x, pc=0x%x, rd_wdata=0x%x, target=0x%x", execute_uop.instr, execute_uop.pc, rd_wdata, next_cu_pc)
      } .otherwise {
        instr_cplt(true.B, execute_uop.alu_out.asUInt())
        memwblog("JAL instr=0x%x, pc=0x%x, rd_wdata=0x%x, target=0x%x", execute_uop.instr, execute_uop.pc, rd_wdata, execute_uop.alu_out.asUInt())
      }
      
      // Reset PC to zero
      // TODO: C-ext change to (0) := 0.U
      // FIXME: Add a check for PC to be aligned to 4 bytes or error out
      execute_uop_accept := true.B

    /**************************************************************************/
    /*                                                                        */
    /*               Branching logic                                          */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen (
      (execute_uop.instr === BEQ) || 
      (execute_uop.instr === BNE) || 
      (execute_uop.instr === BLT) || 
      (execute_uop.instr === BLTU) || 
      (execute_uop.instr === BGE) || 
      (execute_uop.instr === BGEU)
    ) {
      when(execute_uop.branch_taken) {
        // TODO: New variant of branching. Always take the branch backwards in decode stage. And if mispredicted in writeback stage branch towards corrected path
        execute_uop_accept := true.B
        instr_cplt(true.B, execute_uop.alu_out.asUInt)
        memwblog("BranchTaken instr=0x%x, pc=0x%x, target=0x%x", execute_uop.instr, execute_uop.pc, execute_uop.alu_out.asUInt())
      } .otherwise {
        instr_cplt()
        memwblog("BranchNotTaken instr=0x%x, pc=0x%x", execute_uop.instr, execute_uop.pc)
        execute_uop_accept := true.B
      }
      // TODO: IMPORTANT! Branch needs to check for misaligment in this stage
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Load logic                                        */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen ( // TODO: RV64 add LDW/LR_W instruction
        (execute_uop.instr === LW)
        || (execute_uop.instr === LH)
        || (execute_uop.instr === LHU)
        || (execute_uop.instr === LB)
        || (execute_uop.instr === LBU)
        || (execute_uop.instr === LR_W)
        
        ) {
      
      
      when(wbstate === WB_REQUEST_WRITE_START) {
        /**************************************************************************/
        /* WB_REQUEST_WRITE_START                                                 */
        /**************************************************************************/
        
        dcache.s0.cmd               := cache_cmd.none
        dtlb.s0.cmd                 := tlb_cmd.resolve

        dcache.s0.vaddr             := execute_uop.alu_out.asUInt
        dtlb.s0.virt_address_top    := execute_uop.alu_out(c.avLen - 1, c.pgoff_len)

        wbstate := WB_COMPARE
        memwblog("LOAD start vaddr=0x%x", execute_uop.alu_out)
      } .elsewhen (wbstate === WB_COMPARE) {
        /**************************************************************************/
        /* WB_COMPARE                                                             */
        /**************************************************************************/
        
        memwblog("LOAD compare vaddr=0x%x", execute_uop.alu_out)
        // FIXME: Misaligned

        when(loadGen.io.misaligned) {
          memwblog("LOAD Misaligned vaddr=0x%x", execute_uop.alu_out)
          handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_MISALIGNED)
        } .elsewhen(vm_enabled && dtlb.s1.miss) {
          /**************************************************************************/
          /* TLB Miss                                                               */
          /**************************************************************************/
          
          wbstate         :=  WB_TLBREFILL
          memwblog("LOAD TLB MISS vaddr=0x%x", execute_uop.alu_out)
        } .elsewhen(vm_enabled && dpagefault.fault) {
          /**************************************************************************/
          /* Pagefault                                                              */
          /**************************************************************************/
          memwblog("LOAD Pagefault vaddr=0x%x", execute_uop.alu_out)
          handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_PAGE_FAULT)
        } .elsewhen(!pma_defined /*|| pmp.fault*/) { // FIXME: PMP
          /**************************************************************************/
          /* PMA/PMP                                                                */
          /**************************************************************************/
          memwblog("LOAD PMA/PMP access fault vaddr=0x%x", execute_uop.alu_out)
          handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_ACCESS_FAULT)
        } .otherwise {
          

          when(wb_is_atomic && !pma_memory) {
            memwblog("LOAD Atomic on non atomic section vaddr=0x%x", execute_uop.alu_out)
            handle_trap_like(csr_cmd.exception, new exc_code(c).STORE_AMO_ACCESS_FAULT)
          } .elsewhen(!wb_is_atomic && pma_memory && dcache.s1.response.miss) {
            /**************************************************************************/
            /* Cache miss and not atomic                                                             */
            /**************************************************************************/
            memwblog("LOAD marked as memory and cache miss vaddr=0x%x", execute_uop.alu_out)
            
            wbstate           := WB_CACHEREFILL
            saved_tlb_ptag    := dtlb.s1.read_data.ptag
          } .elsewhen(!wb_is_atomic && pma_memory && !dcache.s1.response.miss) {
            /**************************************************************************/
            /* Cache hit                                                              */
            /**************************************************************************/
            // FIXME: Generate the load value
            
            rd_write := true.B
            rd_wdata := dcache.s1.response.bus_aligned_data.asTypeOf(Vec(c.bp.data_bytes / (c.xLen_bytes), UInt(c.xLen.W)))(wdata_select)
            memwblog("LOAD marked as memory and cache hit vaddr=0x%x, wdata_select = 0x%x, data=0x%x", execute_uop.alu_out, wdata_select, rd_wdata)
            rvfi.mem_rmask := (-1.S((c.xLen_bytes).W)).asUInt // FIXME: Needs to be properly set
            rvfi.mem_rdata := rd_wdata
            instr_cplt()
            
          } .otherwise {
            /**************************************************************************/
            /*                                                                        */
            /* Non cacheable address or atomic request Complete request on the dbus   */
            /**************************************************************************/
            assert(wb_is_atomic || !pma_memory)
            
            memwblog("LOAD marked as non cacheabble (or is atomic) vaddr=0x%x, wdata_select = 0x%x, data=0x%x", execute_uop.alu_out, wdata_select, rd_wdata)
            dbus.ar.addr  := execute_uop.alu_out.asSInt.pad(c.apLen)
            // FIXME: Mask LSB accordingly
            dbus.ar.valid := !dbus_wait_for_response
            
            dbus.ar.size  := execute_uop.instr(13, 12)
            dbus.ar.len   := 0.U
            dbus.ar.lock  := wb_is_atomic

            dbus.r.ready  := false.B

            when(dbus.ar.ready) {
              dbus_wait_for_response := true.B
            }
            when (dbus.r.valid && dbus_wait_for_response) {
              dbus.r.ready  := true.B
              
              
              rd_wdata := loadGen.io.out
              
              dbus_wait_for_response := false.B
              // FIXME: RVFI
              /**************************************************************************/
              /* Atomic access that failed                                              */
              /**************************************************************************/
              assert(!(wb_is_atomic && dbus.r.resp === bus_resp_t.OKAY), "[BUG] LR_W/LR_D no lock response for lockable region. Implementation bug")
              assert(dbus.r.last, "[BUG] Last should be set for all len=0 returned transactions")
              when(wb_is_atomic && (dbus.r.resp === bus_resp_t.OKAY)) {
                memwblog("LR_W/LR_D no lock response for lockable region. Implementation bug vaddr=0x%x", execute_uop.alu_out)
                handle_trap_like(csr_cmd.exception, new exc_code(c).INSTR_ILLEGAL)
              } .elsewhen(wb_is_atomic && (dbus.r.resp === bus_resp_t.EXOKAY)) {
                /**************************************************************************/
                /* Atomic access that succeded                                            */
                /**************************************************************************/
                rd_write := true.B

                instr_cplt() // Lock completed
                atomic_lock := true.B
                atomic_lock_addr := dbus.ar.addr.asUInt
                atomic_lock_doubleword := (execute_uop.instr === LR_D)
              } .elsewhen(dbus.r.resp =/= bus_resp_t.OKAY) {
                /**************************************************************************/
                /* Non atomic and bus returned error                                      */
                /**************************************************************************/
                handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_ACCESS_FAULT)
              } otherwise {
                /**************************************************************************/
                /* Non atomic and success                                                 */
                /**************************************************************************/
                rd_write := true.B
                instr_cplt()

                assert((dbus.r.resp === bus_resp_t.OKAY) && !wb_is_atomic)
              }
            }
          }
        }
      } .elsewhen(wbstate === WB_CACHEREFILL) {
        drefill.req := true.B
        drefill.ibus <> dbus.viewAsSupertype(new ibus_t(c))
        drefill.s0 <> dcache.s0
        
        when(drefill.cplt) {
          wbstate := WB_REQUEST_WRITE_START
          when(drefill.err) {
            handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_ACCESS_FAULT)
          }
        }
      } .elsewhen(wbstate === WB_TLBREFILL) {
        memwblog("LOAD TLB refill")
        dptw.bus <> dbus.viewAsSupertype(new ibus_t(c))

        dtlb.s0.virt_address_top     := execute_uop.alu_out(c.avLen - 1, c.pgoff_len)
        dptw.resolve_req             := true.B
        
        when(dptw.cplt) {
          dtlb.s0.cmd                          := tlb_cmd.write
          when(dptw.page_fault) {
            handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_PAGE_FAULT)
          } .elsewhen(dptw.access_fault) {
            handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_ACCESS_FAULT)
          } .otherwise {
            wbstate := WB_REQUEST_WRITE_START
          }
        }
      }
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Store logic                                       */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen (
      (execute_uop.instr === SW)
      || (execute_uop.instr === SH)
      || (execute_uop.instr === SB)
      || (execute_uop.instr === SC_W)
      // || (execute_uop.instr === SC_D)
    ) {
      // TODO: Store
      memwblog("STORE vaddr=0x%x", execute_uop.alu_out)
      // FIXME: Misaligned
      when(vm_enabled && dtlb.s1.miss) {
        /**************************************************************************/
        /* TLB Miss                                                               */
        /**************************************************************************/
        
        wbstate         :=  WB_TLBREFILL
        memwblog("STORE TLB MISS vaddr=0x%x", execute_uop.alu_out)
      } .elsewhen(vm_enabled && dpagefault.fault) {
        /**************************************************************************/
        /* Pagefault                                                              */
        /**************************************************************************/
        memwblog("STORE Pagefault vaddr=0x%x", execute_uop.alu_out)
        handle_trap_like(csr_cmd.exception, new exc_code(c).STORE_AMO_PAGE_FAULT)
      } .elsewhen(!pma_defined /*|| pmp.fault*/) { // FIXME: PMP
        /**************************************************************************/
        /* PMA/PMP                                                                */
        /**************************************************************************/
        memwblog("STORE PMA/PMP access fault vaddr=0x%x", execute_uop.alu_out)
        handle_trap_like(csr_cmd.exception, new exc_code(c).STORE_AMO_ACCESS_FAULT)
      } .otherwise {
        when(wb_is_atomic && !pma_memory) {
          memwblog("STORE Atomic on non atomic section vaddr=0x%x", execute_uop.alu_out)
          handle_trap_like(csr_cmd.exception, new exc_code(c).STORE_AMO_ACCESS_FAULT)
        } .elsewhen(!wb_is_atomic && pma_memory) {

        }
      }
      /*
      dbus.aw.valid := !dbus_ax_done
      when(dbus.aw.ready) {
        dbus_ax_done := true.B
      }
      
      dbus.w.valid := !dbus_w_done
      when(dbus.w.ready) {
        dbus_w_done := true.B
      }

      when((dbus.aw.ready || dbus_ax_done) && (dbus.w.ready || dbus_w_done)) {
        dbus_ax_done := false.B
        dbus_w_done := false.B
        dbus_wait_for_response := true.B
      }

      when(dbus_wait_for_response && dbus.b.valid) {
        dbus_wait_for_response := false.B
        
        when(dbus.b.resp =/= bus_resp_t.OKAY) {

        }
        instr_cplt()
        // Write complete
        // TODO: Release the writeback stage and complete the instruction
        // TODO: Error handling

        // FIXME: RVFI
      }*/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: CSRRW/CSRRWI                                      */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen((execute_uop.instr === CSRRW) || (execute_uop.instr === CSRRWI)) {
      when(!csr_error_happened) {
        rd_wdata := csr.out

        when(execute_uop.instr(11,  7) === 0.U) { // RD == 0; => No read
          csr.cmd := csr_cmd.write
        } .otherwise {  // RD != 0; => Read side effects
          csr.cmd := csr_cmd.read_write
          rd_write := true.B
        }
        when(execute_uop.instr === CSRRW) {
          csr.in := execute_uop.rs1_data
          memwblog("CSRRW instr=0x%x, pc=0x%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x, csr.err=%x", execute_uop.instr, execute_uop.pc, csr.cmd.asUInt, csr.addr, csr.in, csr.err)
        } .otherwise { // CSRRWI
          csr.in := execute_uop.instr(19, 15)
          memwblog("CSRRWI instr=0x%x, pc=0x%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x, csr.err=%x", execute_uop.instr, execute_uop.pc, csr.cmd.asUInt, csr.addr, csr.in, csr.err)
        }

        // Need to restart the instruction fetch process
        instr_cplt(true.B)
        when(csr.err) {
          csr_error_happened := true.B
        }
      } .elsewhen(csr_error_happened) {
        handle_trap_like(csr_cmd.exception, new exc_code(c).INSTR_ILLEGAL)
      }
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: CSRRS/CSRRSI                                      */
    /*                                                                        */
    /**************************************************************************/
    //} .elsewhen((execute_uop.instr === CSRRS) || (execute_uop.instr === CSRRSI)) {
    //  printf("[core%x c:%d WritebackMemory] CSRRW instr=0x%x, pc=0x%x, execute_uop.instr, execute_uop.pc)
    /**************************************************************************/
    /*                                                                        */
    /*              FIXME: CSRRC/CSRRCI                                       */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: CSRRC/CSRRCI                                      */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: EBREAK                                            */
    /*                                                                        */
    /**************************************************************************/
    /*} .elsewhen((execute_uop.instr === EBREAK)) {
      when((csr.regs_output.privilege === privilege_t.M) && csr.dcsr.ebreakm) {

      }
    */
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: ECALL                                             */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               Flushing instructions                                    */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen((execute_uop.instr === FENCE) || (execute_uop.instr === FENCE_I) || (execute_uop.instr === SFENCE_VMA)) {
      memwblog("Flushing everything instr=0x%x, pc=0x%x", execute_uop.instr, execute_uop.pc)
      instr_cplt(true.B)
      cu_cmd := controlunit_cmd.flush
    /**************************************************************************/
    /*                                                                        */
    /*               MRET                                                     */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen((csr.regs_output.privilege === privilege_t.M) && (execute_uop.instr === MRET)) {
      handle_trap_like(csr_cmd.mret)
    /**************************************************************************/
    /*                                                                        */
    /*               SRET                                                     */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(((csr.regs_output.privilege === privilege_t.M) || (csr.regs_output.privilege === privilege_t.S)) && !csr.regs_output.tsr && (execute_uop.instr === SRET)) {
      handle_trap_like(csr_cmd.sret)
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: ATOMICS LR                                        */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: ATOMICS SC                                        */
    /*               NOTE: Dont issue atomics store if no active lock         */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: ATOMICS AMOOP                                     */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               UNKNOWN INSTURCTION ERROR                                */
    /*                                                                        */
    /**************************************************************************/
    } .otherwise {
      handle_trap_like(csr_cmd.exception, new exc_code(c).INSTR_ILLEGAL)
      memwblog("UNKNOWN instr=0x%x, pc=0x%x", execute_uop.instr, execute_uop.pc)
    }


    when(rd_write) {
      regs(execute_uop.instr(11,  7)) := rd_wdata
      memwblog("Write rd=0x%x, value=0x%x", execute_uop.instr(11,  7), rd_wdata)
      rvfi.rd_addr := execute_uop.instr(11,  7)
      rvfi.rd_wdata := Mux(execute_uop.instr(11, 7) === 0.U, 0.U, rd_wdata)
    }
    // TODO: Dont unconditionally reset the regs reservation
    
  } .otherwise {
    memwblog("No active instruction")
  }
}
