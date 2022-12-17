package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

import io.AnsiColor._

import Instructions._
import armleocpu.utils._

class rvfi_o(c: CoreParams) extends Bundle {
  val valid = Bool()
  val order = UInt(64.W)
  val insn  = UInt(c.iLen.W)
  val trap  = Bool()
  val halt  = Bool()
  val intr  = Bool()
  val mode  = UInt(2.W) // Privilege mode
  // val ixl   = UInt(2.W) // TODO: RVC

  // Register
  val rs1_addr  = UInt(5.W)
  val rs2_addr  = UInt(5.W)
  val rs1_rdata = UInt(c.xLen.W)
  val rs2_rdata = UInt(c.xLen.W)
  val rd_addr   = UInt(5.W)
  val rd_wdata  = UInt(c.xLen.W)

  // PC
  val pc_rdata  = UInt(c.xLen.W)
  val pc_wdata  = UInt(c.xLen.W)

  // MEM
  val mem_addr  = UInt(c.xLen.W)
  val mem_rmask = UInt((c.xLen_bytes).W)
  val mem_wmask = UInt((c.xLen_bytes).W)
  val mem_rdata = UInt(c.xLen.W)
  val mem_wdata = UInt(c.xLen.W)

}


// DECODE
class decode_uop_t(c: CoreParams) extends fetch_uop_t(c) {
  val rs1_data        = UInt(c.xLen.W)
  val rs2_data        = UInt(c.xLen.W)
}




class Core(val c: CoreParams = new CoreParams) extends Module {
  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val ibus            = IO(new ibus_t(c))
  val dbus            = IO(new dbus_t(c))
  val int             = IO(Input(new InterruptsInputs))
  val debug_req_i     = IO(Input(Bool()))
  val dm_haltaddr_i   = IO(Input(UInt(c.avLen.W))) // FIXME: use this for halting
  //val debug_state_o   = IO(Output(UInt(2.W))) // FIXME: Output the state
  val rvfi            = if(c.rvfi_enabled) IO(Output(new rvfi_o(c))) else Wire(new rvfi_o(c))

  if(!c.rvfi_enabled && c.rvfi_dont_touch) {
    dontTouch(rvfi) // It should be optimized away, otherwise
  }


  

  val dlog = new Logger(c.lp.coreName, f"decod", c.core_verbose)
  val memwblog = new Logger(c.lp.coreName, f"memwb", c.core_verbose)

  /**************************************************************************/
  /*                                                                        */
  /*                Submodules                                              */
  /*                                                                        */
  /**************************************************************************/

  val fetch   = Module(new Fetch(c))
  val execute = Module(new Execute(c))
  val csr = Module(new CSR(c))
  val cu  = Module(new ControlUnit(c))
  
  val dcache  = Module(new Cache(verbose = c.dcache_verbose, c = c, instName = "data$", cp = c.dcache))
  val dtlb    = Module(new TLB(verbose = c.dtlb_verbose, instName = "dtlb ", c = c, tp = c.dtlb))
  val dptw    = Module(new PTW(instName = "dptw ", c = c, tp = c.dtlb))
  val drefill = Module(new Refill(c = c, cp = c.dcache, dcache))
  val dpagefault = Module(new Pagefault(c = c))
  val loadGen = Module(new LoadGen(c))
  val storeGen = Module(new StoreGen(c))
  // TODO: Add PTE storage for RVFI
  

  /**************************************************************************/
  /*                                                                        */
  /*                Submodules permanent connections                        */
  /*                                                                        */
  /**************************************************************************/

  fetch.ibus <> ibus
  // TODO: Add Instruction PTE storage for RVFI
  
  

  
  

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/


  val atomic_lock             = RegInit(false.B)
  val atomic_lock_addr        = Reg(UInt(c.apLen.W))
  val atomic_lock_doubleword  = Reg(Bool()) // Either word 010 and 011

  val dbus_ax_done            = RegInit(false.B)
  val dbus_w_done             = RegInit(false.B)
  val dbus_wait_for_response  = RegInit(false.B)
  

  // Registers

  val regs              = Mem(32, UInt(c.xLen.W))
  val regs_reservation  = RegInit(VecInit.tabulate(32) {f:Int => false.B})

  

  val decode_uop        = Reg(new decode_uop_t(c))
  val decode_uop_valid  = RegInit(false.B)
  
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
  /*                COMBINATIONAL                                           */
  /*                                                                        */
  /**************************************************************************/
  val decode_uop_accept   = Wire(Bool())
  
  
  /**************************************************************************/
  /*                Pipeline combinational signals                          */
  /**************************************************************************/
  val should_rd_reserve       = Wire(Bool())
  should_rd_reserve           := false.B

  val rd_write = Wire(Bool())
  val rd_wdata = Wire(UInt(c.xLen.W))

  rd_write := false.B
  rd_wdata := execute.uop_o.alu_out.asUInt()

  val wdata_select = Wire(UInt((c.xLen).W))
  if(c.bp.data_bytes == (c.xLen_bytes)) {
    wdata_select := 0.U
  } else {
    wdata_select := execute.uop_o.alu_out.asUInt(log2Ceil(c.bp.data_bytes) - 1, log2Ceil(c.xLen_bytes))
  }

  val wb_is_atomic =
        (execute.uop_o.instr === LR_W) ||
        (execute.uop_o.instr === LR_D) ||
        (execute.uop_o.instr === SC_W) ||
        (execute.uop_o.instr === SC_D)


  
  
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


  /**************************************************************************/
  /*                ControlUnit Signals                                     */
  /**************************************************************************/

  cu.cmd := controlunit_cmd.none
  cu.pc_in := execute.uop_o.pc_plus_4
  cu.decode_to_cu_ready := !decode_uop_valid
  cu.execute_to_cu_ready := !execute.uop_valid_o
  cu.fetch_ready := !fetch.busy
  cu.wb_ready := true.B
  
  /**************************************************************************/
  /*                                                                        */
  /*                Fetch combinational signals                             */
  /*                                                                        */
  /**************************************************************************/
  fetch.uop_accept      := false.B
  fetch.cmd             := cu.cu_to_fetch_cmd
  fetch.csr_regs_output := csr.regs_output
  fetch.new_pc          := cu.pc_out

  
  /**************************************************************************/
  /*                Dbus combinational signals                              */
  /**************************************************************************/
  dbus.aw.valid := false.B
  dbus.aw.addr  := execute.uop_o.alu_out.asSInt.pad(c.apLen) // FIXME: Mux depending on vm enabled
  // FIXME: Needs to depend on dbus_len
  dbus.aw.size  := execute.uop_o.instr(13, 12) // FIXME: Needs to be set properly
  dbus.aw.len   := 0.U
  dbus.aw.lock  := false.B // FIXME: Needs to be set properly

  dbus.w.valid  := false.B
  dbus.w.data   := (VecInit.fill(c.bp.data_bytes / (c.xLen_bytes)) (execute.uop_o.rs2_data)).asUInt // FIXME: Duplicate it
  dbus.w.strb   := (-1.S(dbus.w.strb.getWidth.W)).asUInt() // Just pick any number, that is bigger than write strobe
  // FIXME: Strobe needs proper values
  // FIXME: Strobe needs proper value
  dbus.w.last   := true.B // Constant

  dbus.b.ready  := false.B

  dbus.ar.valid := false.B
  dbus.ar.addr  := execute.uop_o.alu_out.asSInt.pad(c.apLen) // FIXME: Needs a proper MUX
  // FIXME: Needs to depend on dbus_len
  dbus.ar.size  := execute.uop_o.instr(13, 12) // FIXME: This should be depending on value of c.xLen
  dbus.ar.len   := 0.U
  dbus.ar.lock  := false.B

  dbus.r.ready  := false.B
  

  /**************************************************************************/
  /*                                                                        */
  /*                Loadgen/Storegen                                        */
  /*                                                                        */
  /**************************************************************************/
  loadGen.io.in := frombus(c, dbus.ar.addr.asUInt, dbus.r.data) // Muxed between cache and dbus
  loadGen.io.instr := execute.uop_o.instr // Constant
  loadGen.io.vaddr := execute.uop_o.alu_out.asUInt // Constant

  storeGen.io.in    := execute.uop_o.rs2_data
  storeGen.io.instr := execute.uop_o.instr // Constant
  storeGen.io.vaddr := execute.uop_o.alu_out.asUInt // Constant

  /**************************************************************************/
  /*                                                                        */
  /*                DPagefault                                              */
  /*                                                                        */
  /**************************************************************************/
  
  dpagefault.csr_regs_output  := csr.regs_output // Constant
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
  dtlb.s0.virt_address_top    := execute.uop_o.alu_out(c.avLen - 1, c.pgoff_len)


  /**************************************************************************/
  /*                                                                        */
  /*                DPTW                                                    */
  /*                                                                        */
  /**************************************************************************/
  
  dptw.vaddr            := execute.uop_o.alu_out.asUInt
  dptw.csr_regs_output  := csr.regs_output
  dptw.resolve_req      := false.B // Not constant
  dptw.bus              <> dbus.viewAsSupertype(new ibus_t(c))
  // We MUX this depending on state, so not constant.
  // We just use it so the input signals will be connected

  /**************************************************************************/
  /*                                                                        */
  /*                DRefill                                                 */
  /*                                                                        */
  /**************************************************************************/
  val (vm_enabled, vm_privilege) = csr.regs_output.getVmSignals()

  drefill.req   := false.B // FIXME: Change as needed
  drefill.vaddr := execute.uop_o.alu_out.asUInt // FIXME: Change as needed
  drefill.paddr := Mux(vm_enabled, // FIXME: Change as needed
    Cat(saved_tlb_ptag, execute.uop_o.alu_out.asUInt(c.pgoff_len - 1, 0)), // Virtual addressing use tlb data
    Cat(execute.uop_o.alu_out.pad(c.apLen))
  )
  drefill.ibus          <> dbus.viewAsSupertype(new ibus_t(c))

  // FIXME: Save the saved_tlb_ptag
  
  /**************************************************************************/
  /*                                                                        */
  /*                Dcache             */
  /*                                                                        */
  /**************************************************************************/

  val s1_paddr = Mux(vm_enabled, 
    Cat(dtlb.s1.read_data.ptag, execute.uop_o.alu_out(c.pgoff_len - 1, 0)), // Virtual addressing use tlb data
    Cat(execute.uop_o.alu_out.pad(c.apLen))
  )
  
  dcache.s0                   <> drefill.s0
  dcache.s0.cmd               := cache_cmd.none
  dcache.s0.vaddr             := execute.uop_o.alu_out.asUInt
  dcache.s1.paddr             := s1_paddr


  val (pma_defined, pma_memory) = PMA(c, s1_paddr)
  
  /**************************************************************************/
  /*                RVFI                                                    */
  /**************************************************************************/

  rvfi.valid := false.B
  rvfi.halt := false.B
  // rvfi.ixl  := Mux(c.xLen.U === 32.U, 1.U, 2.U) // TODO: RVC
  rvfi.mode := csr.regs_output.privilege

  rvfi.trap := false.B // FIXME: rvfi.trap
  rvfi.halt := false.B // FIXME: rvfi.halt
  rvfi.intr := false.B // FIXME: rvfi.intr
  
  val order = RegInit(0.U(64.W))
  rvfi.order := order
  when(rvfi.valid) {
    order := order + 1.U
  }
  
  rvfi.insn := execute.uop_o.instr

  rvfi.rs1_addr := execute.uop_o.instr(19, 15)
  rvfi.rs1_rdata := Mux(rvfi.rs1_addr === 0.U, 0.U, execute.uop_o.rs1_data)
  rvfi.rs2_addr := execute.uop_o.instr(24, 20)
  rvfi.rs2_rdata := Mux(rvfi.rs2_addr === 0.U, 0.U, execute.uop_o.rs2_data)
  
  rvfi.rd_addr  := 0.U // No write === 0 addr
  rvfi.rd_wdata := 0.U // Do not write unless valid


  rvfi.pc_rdata := execute.uop_o.pc
  rvfi.pc_wdata := cu.pc_in

  // This probably needs updating. Use virtual addresses instead

  rvfi.mem_addr := s1_paddr // FIXME: Need to be muxed depending on vm_enabled
  rvfi.mem_rmask := 0.U // FIXME: rvfi.mem_rmask
  rvfi.mem_wmask := 0.U // FIXME: rvfi.mem_wmask
  rvfi.mem_rdata := rd_wdata // FIXME: rvfi.mem_rdata
  // FIXME: Need proper value based on bus, not on something else


  rvfi.mem_wdata := 0.U  // FIXME: rvfi.mem_wdata

  /**************************************************************************/
  /*                                                                        */
  /*                DECODE Stage                                            */
  /*                                                                        */
  /**************************************************************************/
  when((!decode_uop_valid) || (decode_uop_valid && decode_uop_accept)) {
    when(fetch.uop_valid && !cu.kill) {
      
      
      // IF REGISTER not reserved, then move the Uop downs stage
      // ELSE stall

      // Only send the uop down the stage if no conflict with any of rs1/rs2/rd
      // otherwise the pipeline will issue instructions with old register values
      
      // Also RD is checked so that the register is not overwritten

      val rs1_reserved  = (fetch.uop.instr(19, 15) =/= 0.U) && regs_reservation(fetch.uop.instr(19, 15))
      val rs2_reserved  = (fetch.uop.instr(24, 20) =/= 0.U) && regs_reservation(fetch.uop.instr(24, 20))
      val rd_reserved   = (fetch.uop.instr(11,  7) =/= 0.U) && regs_reservation(fetch.uop.instr(11,  7))

      val stall         = rs1_reserved || rs2_reserved || rd_reserved
      
      when (!stall) {
        // TODO: Dont unconditonally reserve the register
        when(fetch.uop.instr(11, 7) =/= 0.U) {
          regs_reservation(fetch.uop.instr(11, 7)) := true.B
        }
        decode_uop.viewAsSupertype(new fetch_uop_t(c))  := fetch.uop

        // STALL until reservation is reset
        decode_uop.rs1_data                             := regs(fetch.uop.instr(19, 15))
        decode_uop.rs2_data                             := regs(fetch.uop.instr(24, 20))
        
        fetch.uop_accept                                := true.B
        decode_uop_valid                                := true.B
        dlog("Instruction passed to next stage instr=0x%x, pc=0x%x", fetch.uop.instr, fetch.uop.pc)
      } .otherwise {
        dlog("Instruction stalled because of reservation instr=0x%x, pc=0x%x", fetch.uop.instr, fetch.uop.pc)
        decode_uop_valid := false.B
      }
    } .otherwise {
      dlog("Idle")
      decode_uop_valid := false.B
      when(cu.kill) {
        fetch.uop_accept := true.B
      }
    }
  } .elsewhen(cu.kill) {
    fetch.uop_accept := true.B
    decode_uop_valid := false.B
    dlog("Instr killed")
  } .otherwise {
    decode_uop_valid := false.B
  }

  // TODO: FMAX: Add registerl slice

  decode_uop_accept := execute.decode_uop_accept

  
  /**************************************************************************/
  /*                                                                        */
  /*                WRITEBACK/MEMORY                                        */
  /*                                                                        */
  /**************************************************************************/
  // Accept the execute2 uop by default
  // However if execute.uop_valid_o is set then the below lines will work
  execute.uop_accept        := false.B
  execute.kill              := cu.kill
  execute.decode_uop_valid  := decode_uop_valid
  execute.decode_uop        := decode_uop
  
  /**************************************************************************/
  /*                Instruction completion shorthand                        */
  /**************************************************************************/
  // Used to complete the instruction
  // If br_pc_valid is set then it means that fetch needs to start from br_pc
  // Therefore command control unit to start killing the pipeline
  // and restarting from br_pc
  // We also retire instructions here, so set the rvfi_valid
  // and instret_incr
  def instr_cplt(br_pc_valid: Bool = false.B, br_pc: UInt = execute.uop_o.pc_plus_4): Unit = {
    execute.uop_accept := true.B
    rvfi.valid := true.B
    csr.instret_incr := true.B
    
    when(br_pc_valid) {
      cu.pc_in := br_pc
      cu.cmd := controlunit_cmd.branch
      regs_reservation := 0.U.asTypeOf(chiselTypeOf(regs_reservation))
    } .otherwise {
      cu.cmd := controlunit_cmd.retire
      cu.pc_in := execute.uop_o.pc_plus_4
    }

    regs_reservation(execute.uop_o.instr(11, 7)) := false.B

    csr_error_happened := false.B
    wbstate := WB_REQUEST_WRITE_START // Reset the internal states
  }

  def handle_trap_like(cmd: csr_cmd.Type, cause: UInt = 0.U): Unit = {
    csr.cmd := cmd
    instr_cplt(true.B, csr.next_pc)
    assert(csr.err === false.B) // Should not be possible
  }

  when(cu.wb_flush) {
    // cu.wb_ready := true.B // FIXME: Should be false unless all dcache is invalidated
    
    cu.wb_ready := false.B

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
      cu.wb_ready := true.B
      memwblog("Flush complete")
    }
  } .elsewhen(execute.uop_valid_o && !cu.wb_kill) {

    assert(execute.uop_o.pc(1, 0) === 0.U) // Make sure its aligned

    execute.uop_accept := false.B
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
    } .elsewhen(execute.uop_o.ifetch_access_fault) {
      memwblog("Instruction fetch access fault")
      handle_trap_like(csr_cmd.exception, new exc_code(c).INSTR_ACCESS_FAULT)
    } .elsewhen (execute.uop_o.ifetch_page_fault) {
      memwblog("Instruction fetch page fault")
      handle_trap_like(csr_cmd.exception, new exc_code(c).INSTR_PAGE_FAULT)
    
    /**************************************************************************/
    /*                                                                        */
    /*                Alu/Alu-like writeback                                  */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(
      (execute.uop_o.instr === LUI) ||
      (execute.uop_o.instr === AUIPC) ||

      (execute.uop_o.instr === ADD) ||
      (execute.uop_o.instr === SUB) ||
      (execute.uop_o.instr === AND) ||
      (execute.uop_o.instr === OR)  ||
      (execute.uop_o.instr === XOR) ||
      (execute.uop_o.instr === SLL) ||
      (execute.uop_o.instr === SRL) ||
      (execute.uop_o.instr === SRA) ||
      (execute.uop_o.instr === SLT) ||
      (execute.uop_o.instr === SLTU) ||

      (execute.uop_o.instr === ADDI) ||
      (execute.uop_o.instr === SLTI) ||
      (execute.uop_o.instr === SLTIU) ||
      (execute.uop_o.instr === ANDI) ||
      (execute.uop_o.instr === ORI) ||
      (execute.uop_o.instr === XORI) ||
      (execute.uop_o.instr === SLLI) ||
      (execute.uop_o.instr === SRLI) ||
      (execute.uop_o.instr === SRAI)
      // TODO: Add the rest of ALU out write back 
    ) {
      memwblog("ALU-like instruction found instr=0x%x, pc=0x%x", execute.uop_o.instr, execute.uop_o.pc)
      
      

      rd_wdata := execute.uop_o.alu_out.asUInt()
      rd_write := true.B
      instr_cplt()


    
    /**************************************************************************/
    /*                                                                        */
    /*                JAL/JALR                                                */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(
        (execute.uop_o.instr === JAL) ||
        (execute.uop_o.instr === JALR)
    ) {
      rd_wdata := execute.uop_o.pc_plus_4
      rd_write := true.B

      when(execute.uop_o.instr === JALR) {
        val next_cu_pc = execute.uop_o.alu_out.asUInt() & (~(1.U(c.avLen.W)))
        instr_cplt(true.B, next_cu_pc)
        memwblog("JALR instr=0x%x, pc=0x%x, rd_wdata=0x%x, target=0x%x", execute.uop_o.instr, execute.uop_o.pc, rd_wdata, next_cu_pc)
      } .otherwise {
        instr_cplt(true.B, execute.uop_o.alu_out.asUInt())
        memwblog("JAL instr=0x%x, pc=0x%x, rd_wdata=0x%x, target=0x%x", execute.uop_o.instr, execute.uop_o.pc, rd_wdata, execute.uop_o.alu_out.asUInt())
      }
      
      // Reset PC to zero
      // TODO: C-ext change to (0) := 0.U
      // FIXME: Add a check for PC to be aligned to 4 bytes or error out
      execute.uop_accept := true.B

    /**************************************************************************/
    /*                                                                        */
    /*               Branching logic                                          */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen (
      (execute.uop_o.instr === BEQ) || 
      (execute.uop_o.instr === BNE) || 
      (execute.uop_o.instr === BLT) || 
      (execute.uop_o.instr === BLTU) || 
      (execute.uop_o.instr === BGE) || 
      (execute.uop_o.instr === BGEU)
    ) {
      when(execute.uop_o.branch_taken) {
        // TODO: New variant of branching. Always take the branch backwards in decode stage. And if mispredicted in writeback stage branch towards corrected path
        execute.uop_accept := true.B
        instr_cplt(true.B, execute.uop_o.alu_out.asUInt)
        memwblog("BranchTaken instr=0x%x, pc=0x%x, target=0x%x", execute.uop_o.instr, execute.uop_o.pc, execute.uop_o.alu_out.asUInt())
      } .otherwise {
        instr_cplt()
        memwblog("BranchNotTaken instr=0x%x, pc=0x%x", execute.uop_o.instr, execute.uop_o.pc)
        execute.uop_accept := true.B
      }
      // TODO: IMPORTANT! Branch needs to check for misaligment in this stage
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Load logic                                        */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen ( // TODO: RV64 add LDW/LR_W instruction
        (execute.uop_o.instr === LW)
        || (execute.uop_o.instr === LH)
        || (execute.uop_o.instr === LHU)
        || (execute.uop_o.instr === LB)
        || (execute.uop_o.instr === LBU)
        || (execute.uop_o.instr === LR_W)
        
        ) {
      
      
      when(wbstate === WB_REQUEST_WRITE_START) {
        /**************************************************************************/
        /* WB_REQUEST_WRITE_START                                                 */
        /**************************************************************************/
        
        dcache.s0.cmd               := cache_cmd.none
        dtlb.s0.cmd                 := tlb_cmd.resolve

        dcache.s0.vaddr             := execute.uop_o.alu_out.asUInt
        dtlb.s0.virt_address_top    := execute.uop_o.alu_out(c.avLen - 1, c.pgoff_len)

        wbstate := WB_COMPARE
        memwblog("LOAD start vaddr=0x%x", execute.uop_o.alu_out)
      } .elsewhen (wbstate === WB_COMPARE) {
        /**************************************************************************/
        /* WB_COMPARE                                                             */
        /**************************************************************************/
        
        memwblog("LOAD compare vaddr=0x%x", execute.uop_o.alu_out)
        // FIXME: Misaligned

        when(loadGen.io.misaligned) {
          memwblog("LOAD Misaligned vaddr=0x%x", execute.uop_o.alu_out)
          handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_MISALIGNED)
        } .elsewhen(vm_enabled && dtlb.s1.miss) {
          /**************************************************************************/
          /* TLB Miss                                                               */
          /**************************************************************************/
          
          wbstate         :=  WB_TLBREFILL
          memwblog("LOAD TLB MISS vaddr=0x%x", execute.uop_o.alu_out)
        } .elsewhen(vm_enabled && dpagefault.fault) {
          /**************************************************************************/
          /* Pagefault                                                              */
          /**************************************************************************/
          memwblog("LOAD Pagefault vaddr=0x%x", execute.uop_o.alu_out)
          handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_PAGE_FAULT)
        } .elsewhen(!pma_defined /*|| pmp.fault*/) { // FIXME: PMP
          /**************************************************************************/
          /* PMA/PMP                                                                */
          /**************************************************************************/
          memwblog("LOAD PMA/PMP access fault vaddr=0x%x", execute.uop_o.alu_out)
          handle_trap_like(csr_cmd.exception, new exc_code(c).LOAD_ACCESS_FAULT)
        } .otherwise {
          

          when(wb_is_atomic && !pma_memory) {
            memwblog("LOAD Atomic on non atomic section vaddr=0x%x", execute.uop_o.alu_out)
            handle_trap_like(csr_cmd.exception, new exc_code(c).STORE_AMO_ACCESS_FAULT)
          } .elsewhen(!wb_is_atomic && pma_memory && dcache.s1.response.miss) {
            /**************************************************************************/
            /* Cache miss and not atomic                                                             */
            /**************************************************************************/
            memwblog("LOAD marked as memory and cache miss vaddr=0x%x", execute.uop_o.alu_out)
            
            wbstate           := WB_CACHEREFILL
            saved_tlb_ptag    := dtlb.s1.read_data.ptag
          } .elsewhen(!wb_is_atomic && pma_memory && !dcache.s1.response.miss) {
            /**************************************************************************/
            /* Cache hit                                                              */
            /**************************************************************************/
            // FIXME: Generate the load value
            
            rd_write := true.B
            rd_wdata := dcache.s1.response.bus_aligned_data.asTypeOf(Vec(c.bp.data_bytes / (c.xLen_bytes), UInt(c.xLen.W)))(wdata_select)
            memwblog("LOAD marked as memory and cache hit vaddr=0x%x, wdata_select = 0x%x, data=0x%x", execute.uop_o.alu_out, wdata_select, rd_wdata)
            rvfi.mem_rmask := (-1.S((c.xLen_bytes).W)).asUInt // FIXME: Needs to be properly set
            rvfi.mem_rdata := rd_wdata
            instr_cplt()
            
          } .otherwise {
            /**************************************************************************/
            /*                                                                        */
            /* Non cacheable address or atomic request Complete request on the dbus   */
            /**************************************************************************/
            assert(wb_is_atomic || !pma_memory)
            
            memwblog("LOAD marked as non cacheabble (or is atomic) vaddr=0x%x, wdata_select = 0x%x, data=0x%x", execute.uop_o.alu_out, wdata_select, rd_wdata)
            dbus.ar.addr  := execute.uop_o.alu_out.asSInt.pad(c.apLen)
            // FIXME: Mask LSB accordingly
            dbus.ar.valid := !dbus_wait_for_response
            
            dbus.ar.size  := execute.uop_o.instr(13, 12)
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
                memwblog("LR_W/LR_D no lock response for lockable region. Implementation bug vaddr=0x%x", execute.uop_o.alu_out)
                handle_trap_like(csr_cmd.exception, new exc_code(c).INSTR_ILLEGAL)
              } .elsewhen(wb_is_atomic && (dbus.r.resp === bus_resp_t.EXOKAY)) {
                /**************************************************************************/
                /* Atomic access that succeded                                            */
                /**************************************************************************/
                rd_write := true.B

                instr_cplt() // Lock completed
                atomic_lock := true.B
                atomic_lock_addr := dbus.ar.addr.asUInt
                atomic_lock_doubleword := (execute.uop_o.instr === LR_D)
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

        dtlb.s0.virt_address_top     := execute.uop_o.alu_out(c.avLen - 1, c.pgoff_len)
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
      (execute.uop_o.instr === SW)
      || (execute.uop_o.instr === SH)
      || (execute.uop_o.instr === SB)
      || (execute.uop_o.instr === SC_W)
      // || (execute.uop_o.instr === SC_D)
    ) {
      // TODO: Store
      memwblog("STORE vaddr=0x%x", execute.uop_o.alu_out)
      // FIXME: Misaligned
      when(vm_enabled && dtlb.s1.miss) {
        /**************************************************************************/
        /* TLB Miss                                                               */
        /**************************************************************************/
        
        wbstate         :=  WB_TLBREFILL
        memwblog("STORE TLB MISS vaddr=0x%x", execute.uop_o.alu_out)
      } .elsewhen(vm_enabled && dpagefault.fault) {
        /**************************************************************************/
        /* Pagefault                                                              */
        /**************************************************************************/
        memwblog("STORE Pagefault vaddr=0x%x", execute.uop_o.alu_out)
        handle_trap_like(csr_cmd.exception, new exc_code(c).STORE_AMO_PAGE_FAULT)
      } .elsewhen(!pma_defined /*|| pmp.fault*/) { // FIXME: PMP
        /**************************************************************************/
        /* PMA/PMP                                                                */
        /**************************************************************************/
        memwblog("STORE PMA/PMP access fault vaddr=0x%x", execute.uop_o.alu_out)
        handle_trap_like(csr_cmd.exception, new exc_code(c).STORE_AMO_ACCESS_FAULT)
      } .otherwise {
        when(wb_is_atomic && !pma_memory) {
          memwblog("STORE Atomic on non atomic section vaddr=0x%x", execute.uop_o.alu_out)
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
    } .elsewhen((execute.uop_o.instr === CSRRW) || (execute.uop_o.instr === CSRRWI)) {
      when(!csr_error_happened) {
        rd_wdata := csr.out

        when(execute.uop_o.instr(11,  7) === 0.U) { // RD == 0; => No read
          csr.cmd := csr_cmd.write
        } .otherwise {  // RD != 0; => Read side effects
          csr.cmd := csr_cmd.read_write
          rd_write := true.B
        }
        when(execute.uop_o.instr === CSRRW) {
          csr.in := execute.uop_o.rs1_data
          memwblog("CSRRW instr=0x%x, pc=0x%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x, csr.err=%x", execute.uop_o.instr, execute.uop_o.pc, csr.cmd.asUInt, csr.addr, csr.in, csr.err)
        } .otherwise { // CSRRWI
          csr.in := execute.uop_o.instr(19, 15)
          memwblog("CSRRWI instr=0x%x, pc=0x%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x, csr.err=%x", execute.uop_o.instr, execute.uop_o.pc, csr.cmd.asUInt, csr.addr, csr.in, csr.err)
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
    //} .elsewhen((execute.uop_o.instr === CSRRS) || (execute.uop_o.instr === CSRRSI)) {
    //  printf("[core%x c:%d WritebackMemory] CSRRW instr=0x%x, pc=0x%x, execute.uop_o.instr, execute.uop_o.pc)
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
    /*} .elsewhen((execute.uop_o.instr === EBREAK)) {
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
    } .elsewhen((execute.uop_o.instr === FENCE) || (execute.uop_o.instr === FENCE_I) || (execute.uop_o.instr === SFENCE_VMA)) {
      memwblog("Flushing everything instr=0x%x, pc=0x%x", execute.uop_o.instr, execute.uop_o.pc)
      instr_cplt(true.B)
      cu.cmd := controlunit_cmd.flush
    /**************************************************************************/
    /*                                                                        */
    /*               MRET                                                     */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen((csr.regs_output.privilege === privilege_t.M) && (execute.uop_o.instr === MRET)) {
      handle_trap_like(csr_cmd.mret)
    /**************************************************************************/
    /*                                                                        */
    /*               SRET                                                     */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(((csr.regs_output.privilege === privilege_t.M) || (csr.regs_output.privilege === privilege_t.S)) && !csr.regs_output.tsr && (execute.uop_o.instr === SRET)) {
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
      memwblog("UNKNOWN instr=0x%x, pc=0x%x", execute.uop_o.instr, execute.uop_o.pc)
    }


    when(rd_write) {
      regs(execute.uop_o.instr(11,  7)) := rd_wdata
      memwblog("Write rd=0x%x, value=0x%x", execute.uop_o.instr(11,  7), rd_wdata)
      rvfi.rd_addr := execute.uop_o.instr(11,  7)
      rvfi.rd_wdata := Mux(execute.uop_o.instr(11, 7) === 0.U, 0.U, rd_wdata)
    }
    // TODO: Dont unconditionally reset the regs reservation
    
  } .otherwise {
    memwblog("No active instruction")
  }
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object CoreGenerator extends App {
  // Temorary disable memory configs as yosys does not know what to do with them
  (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  (new ChiselStage).execute(
    Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/recommended_conf/"),
    Seq(
      ChiselGeneratorAnnotation(
        () => new Core(
          new CoreParams(
            icache = new CacheParams(ways = 8, entries = 64),
            dcache = new CacheParams(ways = 8, entries = 64),
            itlb = new TlbParams(ways = 8),
            dtlb = new TlbParams(ways = 8),
            bp = new BusParams(data_bytes = 8),
          )
        )
      )
    )
  )
  
}


