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
  

  /**************************************************************************/
  /*                                                                        */
  /*                Submodules                                              */
  /*                                                                        */
  /**************************************************************************/

  val fetch   = Module(new Fetch(c))
  val execute = Module(new Execute(c))
  val cu  = Module(new ControlUnit(c))
  
  
  

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
  

  val decode_uop        = Reg(new decode_uop_t(c))
  val decode_uop_valid  = RegInit(false.B)
  
  
  
  /**************************************************************************/
  /*                                                                        */
  /*                COMBINATIONAL                                           */
  /*                                                                        */
  /**************************************************************************/
  val decode_uop_accept   = Wire(Bool())
  
  
  /**************************************************************************/
  /*                Pipeline combinational signals                          */
  /**************************************************************************/
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

  // TODO: FMAX: Add registerl slice

  /**************************************************************************/
  /*                                                                        */
  /*                DECODE Stage                                            */
  /*                                                                        */
  /**************************************************************************/
  

  

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


