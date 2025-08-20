package armleocpu

import chisel3._
import chisel3.util._

import chisel3.util._
import chisel3.experimental.dataview._

import Instructions._


class Retirement(implicit ccx: CCXParams) extends CCXModule {

  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  // For reset vectors
  val dynRegs       = IO(Input(new DynamicROCsrRegisters))
  val staticRegs    = IO(Input(new StaticCsrRegisters))


  //val dbus            = IO(new Bus)
  val int             = IO(Input(new InterruptsInputs))
  val debugReq     = IO(Input(Bool()))
  val dmHaltAddr   = IO(Input(UInt(ccx.apLen.W))) // FIXME: use this for halting
  //val debug_state_o   = IO(Output(UInt(2.W))) // FIXME: Output the state
  val rvfi            = IO(Output(new rvfi_o))


  val in         = IO(Flipped(DecoupledIO(new ExecuteUop)))


  val regs_retire      = IO(Flipped(new regs_retire_io))
  val csrRegs         = IO(Output (new CsrRegsOutput))

  val ctrl            = IO(Flipped(new PipelineControlIO))


  
  // TODO: Add PTE storage for RVFI

  
  /**************************************************************************/
  /*                                                                        */
  /*                Submodules                                              */
  /*                                                                        */
  /**************************************************************************/

  val csr         = Module(new CSR)
  //val dtlb        = Module(new TLB      (ccx = ccx, tp = c.dtlb,    verbose = c.dtlb_verbose,   instName = "dtlb "))
  //val dptw        = Module(new PTW      (ccx = ccx, tp = c.dtlb,    verbose = c.dptw_verbose,   instName = "dptw "))
  //val dcache      = Module(new Cache    (ccx = ccx, cp = c.dcache,  verbose = c.dcache_verbose, instName = "data$"))
  //val drefill     = Module(new Refill   (ccx = ccx, cp = c.dcache,  dcache))
  //val dpagefault  = Module(new PageFault(ccx = ccx))
  //val loadGen     = Module(new LoadGen  (ccx = ccx))
  //val storeGen    = Module(new StoreGen (ccx = ccx))

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/

  //val atomic_lock             = RegInit(false.B)
  //val atomic_lock_addr        = Reg(UInt(ccx.apLen.W))
  //val atomic_lock_doubleword  = Reg(Bool()) // Either word 010 and 011

  //val dbus_ax_done            = RegInit(false.B)
  //val dbus_w_done             = RegInit(false.B)
  //val dbus_wait_for_response  = RegInit(false.B)

  /*
  val tlb_invalidate_counter = RegInit(0.U(log2Ceil(c.dtlb.l0_sets).W))
  val cache_invalidate_counter = RegInit(0.U(log2Ceil(c.dcache.entries).W))
  */
  val csr_error_happened = RegInit(false.B)
  
  /*
  val saved_tlb_ptag      = Reg(chiselTypeOf(dtlb.s1.read_data.ptag))
  */
  // If load then cache/tlb request. If store then request is sent to dbus
  val WB_REQUEST_WRITE_START  = 0.U(4.W)
  // Compare the tags, the cache tags, and decide where to proceed
  val WB_COMPARE              = 1.U(4.W)
  val WB_TLBREFILL            = 2.U(4.W)
  val WB_CACHEREFILL          = 3.U(4.W)

  val wbstate             = RegInit(WB_REQUEST_WRITE_START)
  val pcNext              = RegInit(0.U(ccx.apLen.W))

  /**************************************************************************/
  /*                                                                        */
  /*                COMB                                                    */
  /*                                                                        */
  /**************************************************************************/
  /*val wb_is_atomic =
        (in.bits.instr === LR_W) ||
        (in.bits.instr === LR_D) ||
        (in.bits.instr === SC_W) ||
        (in.bits.instr === SC_D)
  */
  /**************************************************************************/
  /*                Pipeline combinational signals                          */
  /**************************************************************************/
  in.ready           := false.B
  regs_retire.commit := false.B
  regs_retire.rd_addr  := in.bits.instr(11, 7)
  regs_retire.rd_write := false.B
  regs_retire.rd_wdata := in.bits.aluOut.asUInt

  val wdata_select = Wire(UInt((ccx.xLen).W))
  if(ccx.busBytes == (ccx.xLenBytes)) {
    wdata_select := 0.U
  } else {
    wdata_select := in.bits.aluOut.asUInt(log2Ceil(ccx.busBytes) - 1, log2Ceil(ccx.xLenBytes))
  }
  
  
  /**************************************************************************/
  /*                CSR Signals                                             */
  /**************************************************************************/
  csr.int           <> int
  csrRegs           := csr.regs_output
  csr.instret_incr  := false.B //
  csr.addr          := in.bits.instr(31, 20) // Constant
  csr.cause         := 0.U // FIXME: Need to be properly set
  csr.cmd           := csr_cmd.none
  csr.epc           := in.bits.pc
  csr.in            := 0.U // FIXME: Needs to be properly connected
  
  csr.dynRegs <> dynRegs
  csr.staticRegs <> staticRegs
  
  /**************************************************************************/
  /*                Dbus combinational signals                              */
  /**************************************************************************/
  /*
  dbus.aw.valid := false.B
  dbus.aw.bits.addr  := in.bits.aluOut.asSInt.pad(ccx.apLen) // FIXME: Mux depending on vm enabled
  // FIXME: Needs to depend on dbus_len
  dbus.aw.bits.size  := in.bits.instr(13, 12) // FIXME: Needs to be set properly
  dbus.aw.bits.len   := 0.U
  dbus.aw.bits.lock  := false.B // FIXME: Needs to be set properly

  dbus.w.valid  := false.B
  dbus.w.bits.data   := (VecInit.fill(ccx.busBytes / (ccx.xLenBytes)) (in.bits.rs2)).asUInt // FIXME: Duplicate it
  dbus.w.bits.strb   := (-1.S(dbus.w.bits.strb.getWidth.W)).asUInt // Just pick any number, that is bigger than write strobe
  // FIXME: Strobe needs proper values
  // FIXME: Strobe needs proper value
  dbus.w.bits.last   := true.B // Constant

  dbus.b.ready  := false.B

  dbus.ar.valid := false.B
  dbus.ar.bits.addr  := in.bits.aluOut.asSInt.pad(ccx.apLen) // FIXME: Needs a proper MUX
  // FIXME: Needs to depend on dbus_len
  dbus.ar.bits.size  := in.bits.instr(13, 12) // FIXME: This should be depending on value of ccx.xLen
  dbus.ar.bits.len   := 0.U
  dbus.ar.bits.lock  := false.B

  dbus.r.ready  := false.B
  

  /**************************************************************************/
  /*                                                                        */
  /*                Loadgen/Storegen                                        */
  /*                                                                        */
  /**************************************************************************/
  loadGen.io.in := frombus(c, dbus.ar.bits.addr.asUInt, dbus.r.bits.data) // Muxed between cache and dbus
  loadGen.io.instr := in.bits.instr // Constant
  loadGen.io.vaddr := in.bits.aluOut.asUInt // Constant

  storeGen.io.in    := in.bits.rs2
  storeGen.io.instr := in.bits.instr // Constant
  storeGen.io.vaddr := in.bits.aluOut.asUInt // Constant

  /**************************************************************************/
  /*                                                                        */
  /*                DPageFault                                              */
  /*                                                                        */
  /**************************************************************************/
  
  dpagefault.csrRegs  := csr.regs_output // Constant
  dpagefault.tlbdata          := dtlb.s1.read_data // Constant

  dpagefault.cmd              := pagefault_cmd.none


  /**************************************************************************/
  /*                                                                        */
  /*                DTLB                                                    */
  /*                                                                        */
  /**************************************************************************/
  
  dtlb.s0.write_data.meta     := dptw.meta
  dtlb.s0.write_data.ptag     := dptw.physical_address_top
  dtlb.s0.cmd                 := tlb_cmd.none
  dtlb.s0.virt_address_top    := in.bits.aluOut(ccx.avLen - 1, c.pgoff_len)


  /**************************************************************************/
  /*                                                                        */
  /*                DPTW                                                    */
  /*                                                                        */
  /**************************************************************************/
  
  dptw.vaddr            := in.bits.aluOut.asUInt
  dptw.csrRegs  := csr.regs_output
  dptw.resolve_req      := false.B // Not constant
  dptw.bus              <> dbus.viewAsSupertype(new ibus_t)
  // We MUX this depending on state, so not constant.
  // We just use it so the input signals will be connected

  /**************************************************************************/
  /*                                                                        */
  /*                DRefill                                                 */
  /*                                                                        */
  /**************************************************************************/
  val (vm_enabled, vm_privilege) = csr.regs_output.getVmSignals()

  drefill.req   := false.B // FIXME: Change as needed
  drefill.vaddr := in.bits.aluOut.asUInt // FIXME: Change as needed
  drefill.paddr := Mux(vm_enabled, // FIXME: Change as needed
    Cat(saved_tlb_ptag, in.bits.aluOut.asUInt(11, 0)), // Virtual addressing use tlb data
    Cat(in.bits.aluOut.pad(ccx.apLen))
  )
  drefill.ibus          <> dbus.viewAsSupertype(new ibus_t)

  // FIXME: Save the saved_tlb_ptag
  
  /**************************************************************************/
  /*                                                                        */
  /*                Dcache             */
  /*                                                                        */
  /**************************************************************************/

  val s1_paddr = Mux(vm_enabled, 
    Cat(dtlb.s1.read_data.ptag, in.bits.aluOut(11, 0)), // Virtual addressing use tlb data
    Cat(in.bits.aluOut.pad(ccx.apLen))
  )
  
  dcache.s0                   <> drefill.s0
  dcache.s0.cmd               := cache_cmd.none
  dcache.s0.vaddr             := in.bits.aluOut.asUInt
  dcache.s1.paddr             := s1_paddr


  val (pma_defined, pma_memory) = PMA(c, s1_paddr)
  */

  /**************************************************************************/
  /*                RVFI                                                    */
  /**************************************************************************/

  rvfi.valid := false.B
  rvfi.halt := false.B
  // rvfi.ixl  := Mux(ccx.xLen.U === 32.U, 1.U, 2.U) // TODO: RVC
  rvfi.mode := csr.regs_output.privilege

  rvfi.trap := false.B // FIXME: rvfi.trap
  rvfi.halt := false.B // FIXME: rvfi.halt
  rvfi.intr := false.B // FIXME: rvfi.intr
  
  val order = RegInit(0.U(64.W))
  rvfi.order := order
  when(rvfi.valid) {
    order := order + 1.U
  }
  
  rvfi.insn := in.bits.instr

  rvfi.rs1_addr := in.bits.instr(19, 15)
  rvfi.rs1_rdata := Mux(rvfi.rs1_addr === 0.U, 0.U, in.bits.rs1)
  rvfi.rs2_addr := in.bits.instr(24, 20)
  rvfi.rs2_rdata := Mux(rvfi.rs2_addr === 0.U, 0.U, in.bits.rs2)
  
  rvfi.rd_addr  := 0.U // No write === 0 addr
  rvfi.rd_wdata := 0.U // Do not write unless valid


  rvfi.pc_rdata := in.bits.pc
  rvfi.pc_wdata := pcNext

  // This probably needs updating. Use virtual addresses instead


  // FIXME: s1_paddr cannot be zero
  val s1_paddr = 0.U(1.W)
  rvfi.mem_addr := s1_paddr // FIXME: Need to be muxed depending on vm_enabled
  rvfi.mem_rmask := 0.U // FIXME: rvfi.mem_rmask
  rvfi.mem_wmask := 0.U // FIXME: rvfi.mem_wmask
  rvfi.mem_rdata := regs_retire.rd_wdata // FIXME: rvfi.mem_rdata
  // FIXME: Need proper value based on bus, not on something else

// TODO: Add ptes

  rvfi.mem_wdata := 0.U  // FIXME: rvfi.mem_wdata

  // TODO: FMAX: Add registerl slice

  ctrl.kill := false.B
  ctrl.jump := false.B
  ctrl.newPc := in.bits.pcPlus4
  

  ctrl.flush := false.B // FIXME: Add flushing logic

  // Used to complete the instruction
  // If br_pc_valid is set then it means that fetch needs to start from br_pc
  // Therefore command control unit to start killing the pipeline
  // and restarting from br_pc
  // We also retire instructions here, so set the rvfi_valid
  // and instret_incr
  def instr_cplt(br_pc_valid: Bool = false.B, br_pc: UInt = in.bits.pcPlus4): Unit = {
    in.ready := true.B
    rvfi.valid := true.B
    csr.instret_incr := true.B
    
    
    ctrl.jump := true.B
    ctrl.newPc := br_pc
    pcNext := br_pc
    rvfi.pc_wdata := br_pc
    regs_retire.commit := true.B

    csr_error_happened := false.B
    wbstate := WB_REQUEST_WRITE_START // Reset the internal states
  }

  def handle_trap_like(cmd: csr_cmd.Type, cause: UInt = 0.U): Unit = {
    csr.cmd := cmd
    instr_cplt(true.B, csr.next_pc)
    assert(csr.err === false.B) // Should not be possible
  }

  /*
  when(cu.flush) {
    // cu.ready := true.B // FIXME: Should be false unless all dcache is invalidated
    
    cu.ready := false.B

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
      cu.ready := true.B
      log(cf"Flush complete")
    }
  } .else*/
  
  
  when(in.valid) {

    assume(in.bits.pc(1, 0) === 0.U) // Make sure its aligned

    in.ready := false.B
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Debug enter logic                                 */
    /*                                                                        */
    /**************************************************************************/
    when(debugReq) {
      log(cf"Debug interrupt")
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Interrupt logic                                   */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(csr.int_pending_o) {
      log(cf"External Interrupt")
      handle_trap_like(csr_cmd.interrupt)
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: FETCH ERROR LOGIC                                 */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(in.bits.ifetchAccessFault) {
      log(cf"Instruction fetch access fault")
      handle_trap_like(csr_cmd.exception, new exc_code().INSTR_ACCESS_FAULT)
    } .elsewhen (in.bits.ifetchPageFault) {
      log(cf"Instruction fetch page fault")
      handle_trap_like(csr_cmd.exception, new exc_code().INSTR_PAGE_FAULT)
    
    /**************************************************************************/
    /*                                                                        */
    /*                Alu/Alu-like writeback                                  */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(
      // FIXME: Add the RV64
      (in.bits.instr === LUI) ||
      (in.bits.instr === AUIPC) ||

      (in.bits.instr === ADD) ||
      (in.bits.instr === SUB) ||
      (in.bits.instr === AND) ||
      (in.bits.instr === OR)  ||
      (in.bits.instr === XOR) ||
      (in.bits.instr === SLL) ||
      (in.bits.instr === SRL) ||
      (in.bits.instr === SRA) ||
      (in.bits.instr === SLT) ||
      (in.bits.instr === SLTU) ||

      (in.bits.instr === ADDI) ||
      (in.bits.instr === SLTI) ||
      (in.bits.instr === SLTIU) ||
      (in.bits.instr === ANDI) ||
      (in.bits.instr === ORI) ||
      (in.bits.instr === XORI) ||
      (in.bits.instr === SLLI) ||
      (in.bits.instr === SRLI) ||
      (in.bits.instr === SRAI)
      // TODO: Add the rest of ALU out write back 
    ) {
      log(cf"ALU-like instruction found instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x")
      
      

      regs_retire.rd_wdata := in.bits.aluOut.asUInt
      regs_retire.rd_write := true.B
      instr_cplt()


    
    /**************************************************************************/
    /*                                                                        */
    /*                JAL/JALR                                                */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(
        (in.bits.instr === JAL) ||
        (in.bits.instr === JALR)
    ) {
      regs_retire.rd_wdata := in.bits.pcPlus4
      regs_retire.rd_write := true.B

      when(in.bits.instr === JALR) {
        val next_cu_pc = in.bits.aluOut.asUInt & (~(1.U(ccx.avLen.W)))
        instr_cplt(true.B, next_cu_pc)
        log(cf"JALR instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x, regs_retire.rd_wdata=0x${regs_retire.rd_wdata}%x, target=0x${next_cu_pc}%x")
      } .otherwise {
        instr_cplt(true.B, in.bits.aluOut.asUInt)
        log(cf"JAL instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x, regs_retire.rd_wdata=0x${regs_retire.rd_wdata}%x, target=0x${in.bits.aluOut.asUInt}%x")
      }
      
      // Reset PC to zero
      // TODO: C-ext change to (0) := 0.U
      // FIXME: Add a check for PC to be aligned to 4 bytes or error out
      in.ready := true.B

    /**************************************************************************/
    /*                                                                        */
    /*               Branching logic                                          */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen (
      (in.bits.instr === BEQ) || 
      (in.bits.instr === BNE) || 
      (in.bits.instr === BLT) || 
      (in.bits.instr === BLTU) || 
      (in.bits.instr === BGE) || 
      (in.bits.instr === BGEU)
    ) {
      when(in.bits.branchTaken) {
        // TODO: New variant of branching. Always take the branch backwards in decode stage. And if mispredicted in writeback stage branch towards corrected path
        in.ready := true.B
        instr_cplt(true.B, in.bits.aluOut.asUInt)
        log(cf"BranchTaken instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x, target=0x${in.bits.aluOut.asUInt}%x")
      } .otherwise {
        instr_cplt()
        log(cf"BranchNotTaken instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x")
        in.ready := true.B
      }
      // TODO: IMPORTANT! Branch needs to check for misaligment in this stage
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Load logic                                        */
    /*                                                                        */
    /**************************************************************************/
    /*
    } .elsewhen ( // TODO: RV64 add LDW/LR_W instruction
    assert(0.U)
        (in.bits.instr === LW)
        || (in.bits.instr === LH)
        || (in.bits.instr === LHU)
        || (in.bits.instr === LB)
        || (in.bits.instr === LBU)
        || (in.bits.instr === LR_W)
        
        ) {
      
      
      when(wbstate === WB_REQUEST_WRITE_START) {
        /**************************************************************************/
        /* WB_REQUEST_WRITE_START                                                 */
        /**************************************************************************/
        
        dcache.s0.cmd               := cache_cmd.none
        dtlb.s0.cmd                 := tlb_cmd.resolve

        dcache.s0.vaddr             := in.bits.aluOut.asUInt
        dtlb.s0.virt_address_top    := in.bits.aluOut(ccx.avLen - 1, c.pgoff_len)

        wbstate := WB_COMPARE
        log(cf"LOAD start vaddr=0x%x", in.bits.aluOut)
      } .elsewhen (wbstate === WB_COMPARE) {
        /**************************************************************************/
        /* WB_COMPARE                                                             */
        /**************************************************************************/
        
        log(cf"LOAD compare vaddr=0x%x", in.bits.aluOut)
        // FIXME: Misaligned

        when(loadGen.io.misaligned) {
          log(cf"LOAD Misaligned vaddr=0x%x", in.bits.aluOut)
          handle_trap_like(csr_cmd.exception, new exc_code().LOAD_MISALIGNED)
        } .elsewhen(vm_enabled && dtlb.s1.miss) {
          /**************************************************************************/
          /* TLB Miss                                                               */
          /**************************************************************************/
          
          wbstate         :=  WB_TLBREFILL
          log(cf"LOAD TLB MISS vaddr=0x%x", in.bits.aluOut)
        } .elsewhen(vm_enabled && dpagefault.fault) {
          /**************************************************************************/
          /* PageFault                                                              */
          /**************************************************************************/
          log(cf"LOAD PageFault vaddr=0x%x", in.bits.aluOut)
          handle_trap_like(csr_cmd.exception, new exc_code().LOAD_PAGE_FAULT)
        } .elsewhen(!pma_defined /*|| pmp.fault*/) { // FIXME: PMP
          /**************************************************************************/
          /* PMA/PMP                                                                */
          /**************************************************************************/
          log(cf"LOAD PMA/PMP access fault vaddr=0x%x", in.bits.aluOut)
          handle_trap_like(csr_cmd.exception, new exc_code().LOAD_ACCESS_FAULT)
        } .otherwise {
          

          when(wb_is_atomic && !pma_memory) {
            log(cf"LOAD Atomic on non atomic section vaddr=0x%x", in.bits.aluOut)
            handle_trap_like(csr_cmd.exception, new exc_code().STORE_AMO_ACCESS_FAULT)
          } .elsewhen(!wb_is_atomic && pma_memory && dcache.s1.response.miss) {
            /**************************************************************************/
            /* Cache miss and not atomic                                                             */
            /**************************************************************************/
            log(cf"LOAD marked as memory and cache miss vaddr=0x%x", in.bits.aluOut)
            
            wbstate           := WB_CACHEREFILL
            saved_tlb_ptag    := dtlb.s1.read_data.ptag
          } .elsewhen(!wb_is_atomic && pma_memory && !dcache.s1.response.miss) {
            /**************************************************************************/
            /* Cache hit                                                              */
            /**************************************************************************/
            // FIXME: Generate the load value
            
            regs_retire.rd_write := true.B
            regs_retire.rd_wdata := dcache.s1.response.bus_aligned_data.asTypeOf(Vec(ccx.busBytes / (ccx.xLenBytes), UInt(ccx.xLen.W)))(wdata_select)
            log(cf"LOAD marked as memory and cache hit vaddr=0x%x, wdata_select = 0x%x, data=0x%x", in.bits.aluOut, wdata_select, regs_retire.rd_wdata)
            rvfi.mem_rmask := (-1.S((ccx.xLenBytes).W)).asUInt // FIXME: Needs to be properly set
            rvfi.mem_rdata := regs_retire.rd_wdata
            instr_cplt()
            
          } .otherwise {
            /**************************************************************************/
            /*                                                                        */
            /* Non cacheable address or atomic request Complete request on the dbus   */
            /**************************************************************************/
            assert(wb_is_atomic || !pma_memory)
            
            log(cf"LOAD marked as non cacheabble (or is atomic) vaddr=0x%x, wdata_select = 0x%x, data=0x%x", in.bits.aluOut, wdata_select, regs_retire.rd_wdata)
            dbus.ar.bits.addr  := in.bits.aluOut.asSInt.pad(ccx.apLen)
            // FIXME: Mask LSB accordingly
            dbus.ar.valid := !dbus_wait_for_response
            
            dbus.ar.bits.size  := in.bits.instr(13, 12)
            dbus.ar.bits.len   := 0.U
            dbus.ar.bits.lock  := wb_is_atomic

            dbus.r.ready  := false.B

            when(dbus.ar.ready) {
              dbus_wait_for_response := true.B
            }
            when (dbus.r.valid && dbus_wait_for_response) {
              dbus.r.ready  := true.B
              
              
              regs_retire.rd_wdata := loadGen.io.out
              
              dbus_wait_for_response := false.B
              // FIXME: RVFI
              /**************************************************************************/
              /* Atomic access that failed                                              */
              /**************************************************************************/
              assert(!(wb_is_atomic && dbus.r.bits.resp === busConst.OKAY), "[BUG] LR_W/LR_D no lock response for lockable region. Implementation bug")
              assert(dbus.r.bits.last, "[BUG] Last should be set for all len=0 returned transactions")
              when(wb_is_atomic && (dbus.r.bits.resp === busConst.OKAY)) {
                log(cf"LR_W/LR_D no lock response for lockable region. Implementation bug vaddr=0x%x", in.bits.aluOut)
                handle_trap_like(csr_cmd.exception, new exc_code().INSTR_ILLEGAL)
              } .elsewhen(wb_is_atomic && (dbus.r.bits.resp === busConst.EXOKAY)) {
                /**************************************************************************/
                /* Atomic access that succeded                                            */
                /**************************************************************************/
                regs_retire.rd_write := true.B

                instr_cplt() // Lock completed
                atomic_lock := true.B
                atomic_lock_addr := dbus.ar.bits.addr.asUInt
                atomic_lock_doubleword := (in.bits.instr === LR_D)
              } .elsewhen(dbus.r.bits.resp =/= busConst.OKAY) {
                /**************************************************************************/
                /* Non atomic and bus returned error                                      */
                /**************************************************************************/
                handle_trap_like(csr_cmd.exception, new exc_code().LOAD_ACCESS_FAULT)
              } otherwise {
                /**************************************************************************/
                /* Non atomic and success                                                 */
                /**************************************************************************/
                regs_retire.rd_write := true.B
                instr_cplt()

                assert((dbus.r.bits.resp === busConst.OKAY) && !wb_is_atomic)
              }
            }
          }
        }
      } .elsewhen(wbstate === WB_CACHEREFILL) {
        drefill.req := true.B
        drefill.ibus <> dbus.viewAsSupertype(new ibus_t(ccx))
        drefill.s0 <> dcache.s0
        
        when(drefill.cplt) {
          wbstate := WB_REQUEST_WRITE_START
          when(drefill.err) {
            handle_trap_like(csr_cmd.exception, new exc_code().LOAD_ACCESS_FAULT)
          }
        }
      } .elsewhen(wbstate === WB_TLBREFILL) {
        log(cf"LOAD TLB refill")
        dptw.bus <> dbus.viewAsSupertype(new ibus_t(ccx))

        dtlb.s0.virt_address_top     := in.bits.aluOut(ccx.avLen - 1, c.pgoff_len)
        dptw.resolve_req             := true.B
        
        when(dptw.cplt) {
          dtlb.s0.cmd                          := tlb_cmd.write
          when(dptw.pagefault) {
            handle_trap_like(csr_cmd.exception, new exc_code().LOAD_PAGE_FAULT)
          } .elsewhen(dptw.accessFault) {
            handle_trap_like(csr_cmd.exception, new exc_code().LOAD_ACCESS_FAULT)
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
      (in.bits.instr === SW)
      || (in.bits.instr === SH)
      || (in.bits.instr === SB)
      || (in.bits.instr === SC_W)
      // || (in.bits.instr === SC_D)
    ) {
      // TODO: Store
      log(cf"STORE vaddr=0x%x", in.bits.aluOut)
      // FIXME: Misaligned
      when(vm_enabled && dtlb.s1.miss) {
        /**************************************************************************/
        /* TLB Miss                                                               */
        /**************************************************************************/
        
        wbstate         :=  WB_TLBREFILL
        log(cf"STORE TLB MISS vaddr=0x%x", in.bits.aluOut)
      } .elsewhen(vm_enabled && dpagefault.fault) {
        /**************************************************************************/
        /* PageFault                                                              */
        /**************************************************************************/
        log(cf"STORE PageFault vaddr=0x%x", in.bits.aluOut)
        handle_trap_like(csr_cmd.exception, new exc_code().STORE_AMO_PAGE_FAULT)
      } .elsewhen(!pma_defined /*|| pmp.fault*/) { // FIXME: PMP
        /**************************************************************************/
        /* PMA/PMP                                                                */
        /**************************************************************************/
        log(cf"STORE PMA/PMP access fault vaddr=0x%x", in.bits.aluOut)
        handle_trap_like(csr_cmd.exception, new exc_code().STORE_AMO_ACCESS_FAULT)
      } .otherwise {
        when(wb_is_atomic && !pma_memory) {
          log(cf"STORE Atomic on non atomic section vaddr=0x%x", in.bits.aluOut)
          handle_trap_like(csr_cmd.exception, new exc_code().STORE_AMO_ACCESS_FAULT)
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
        
        when(dbus.b.resp =/= busConst.OKAY) {

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
    */
    /*
    } .elsewhen((in.bits.instr === CSRRW) || (in.bits.instr === CSRRWI)) {
      when(!csr_error_happened) {
        regs_retire.rd_wdata := csr.out

        when(in.bits.instr(11,  7) === 0.U) { // RD == 0; => No read
          csr.cmd := csr_cmd.write
        } .otherwise {  // RD != 0; => Read side effects
          csr.cmd := csr_cmd.read_write
          regs_retire.rd_write := true.B
        }
        when(in.bits.instr === CSRRW) {
          csr.in := in.bits.rs1
          log(cf"CSRRW instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x, csr.err=%x", in.bits.instr, in.bits.pc, csr.cmd.asUInt, csr.addr, csr.in, csr.err)
        } .otherwise { // CSRRWI
          csr.in := in.bits.instr(19, 15)
          log(cf"CSRRWI instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x, csr.err=%x", in.bits.instr, in.bits.pc, csr.cmd.asUInt, csr.addr, csr.in, csr.err)
        }

        // Need to restart the instruction fetch process
        instr_cplt(true.B)
        when(csr.err) {
          csr_error_happened := true.B
        }
      } .elsewhen(csr_error_happened) {
        handle_trap_like(csr_cmd.exception, new exc_code().INSTR_ILLEGAL)
      }
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: CSRRS/CSRRSI                                      */
    /*                                                                        */
    /**************************************************************************/
    //} .elsewhen((in.bits.instr === CSRRS) || (in.bits.instr === CSRRSI)) {
    //  printf("[core%x c:%d WritebackMemory] CSRRW instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x, in.bits.instr, in.bits.pc)
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
    /*} .elsewhen((in.bits.instr === EBREAK)) {
      when((csr.regs_output.privilege === Privilege.M) && csr.dcsr.ebreakm) {

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
    } .elsewhen((in.bits.instr === FENCE) || (in.bits.instr === FENCE_I) || (in.bits.instr === SFENCE_VMA)) {
      log(cf"Flushing everything instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x")
      instr_cplt(true.B)
      assert(false.B, "[BUG] FENCE/SFENCE_VMA not implemented yet") // TODO: Implement FENCE/SFENCE_VMA
    /**************************************************************************/
    /*                                                                        */
    /*               MRET                                                     */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen((csr.regs_output.privilege === Privilege.M) && (in.bits.instr === MRET)) {
      handle_trap_like(csr_cmd.mret)
    /**************************************************************************/
    /*                                                                        */
    /*               SRET                                                     */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(((csr.regs_output.privilege === Privilege.M) || (csr.regs_output.privilege === Privilege.S)) && !csr.regs_output.tsr && (in.bits.instr === SRET)) {
      handle_trap_like(csr_cmd.sret)
    */
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
      handle_trap_like(csr_cmd.exception, new exc_code().INSTR_ILLEGAL)
      log(cf"UNKNOWN instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x")
    }


    when(regs_retire.rd_write) {
      log(cf"Write rd=0x${in.bits.instr(11,  7)}%x, value=0x${regs_retire.rd_wdata}%x")
      rvfi.rd_addr := in.bits.instr(11,  7)
      rvfi.rd_wdata := Mux(in.bits.instr(11, 7) === 0.U, 0.U, regs_retire.rd_wdata)
    }
    // TODO: Dont unconditionally reset the regs reservation
    
  } .otherwise {
    //log(cf"No active instruction")
  }
}
