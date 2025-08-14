package armleocpu


import chisel3._
import chisel3.util._

import chisel3.util._
import chisel3.experimental.dataview._

import Instructions._


class rvfi_o(implicit val ccx: CCXParams) extends Bundle {
  val valid = Bool()
  val order = UInt(64.W)
  val insn  = UInt(ccx.iLen.W)
  val trap  = Bool()
  val halt  = Bool()
  val intr  = Bool()
  val mode  = UInt(2.W) // Privilege mode
  // val ixl   = UInt(2.W) // TODO: RVC

  // Register
  val rs1_addr  = UInt(5.W)
  val rs2_addr  = UInt(5.W)
  val rs1_rdata = UInt(ccx.xLen.W)
  val rs2_rdata = UInt(ccx.xLen.W)
  val rd_addr   = UInt(5.W)
  val rd_wdata  = UInt(ccx.xLen.W)

  // PC
  val pc_rdata  = UInt(ccx.xLen.W)
  val pc_wdata  = UInt(ccx.xLen.W)

  // MEM
  val mem_addr  = UInt(ccx.xLen.W)
  val mem_rmask = UInt((ccx.xLenBytes).W)
  val mem_wmask = UInt((ccx.xLenBytes).W)
  val mem_rdata = UInt(ccx.xLen.W)
  val mem_wdata = UInt(ccx.xLen.W)

  // TODO: Add CSRs
}

class Core(implicit ccx: CCXParams) extends CCXModule {
  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val ibus            = IO(new dbus_t)

  //val dbus            = IO(new dbus_t)
  
  val int             = IO(Input(new InterruptsInputs))
  val debug_req_i     = IO(Input(Bool()))
  val dm_haltaddr_i   = IO(Input(UInt(ccx.avLen.W))) // FIXME: use this for halting
  //val debug_state_o   = IO(Output(UInt(2.W))) // FIXME: Output the state

  // For reset vectors
  val dynRegs       = IO(Input(new DynamicROCsrRegisters))
  val staticRegs    = IO(Input(new StaticCsrRegisters))

  val rvfi            = if(ccx.rvfi_enabled) IO(Output(new rvfi_o)) else Wire(new rvfi_o)

  
  if(!ccx.rvfi_enabled && ccx.rvfi_dont_touch) {
    dontTouch(rvfi) // It should be optimized away, otherwise
  }


  /**************************************************************************/
  /*                                                                        */
  /*                Submodules                                              */
  /*                                                                        */
  /**************************************************************************/
  
  val regfile   = Module(new Regfile)
  val prefetch  = Module(new Prefetch)
  val fetch     = Module(new Fetch)
  val decode    = Module(new Decode)
  val execute   = Module(new Execute)
  val retire    = Module(new Retirement)
  val icache    = Module(new Cache()(ccx = ccx, cp = ccx.core.icache))


  /*
  val l2tlb_gigapage  = Module(new AssociativeMemory(new tlb_entry_t(c, lvl = 2), ccx.core.l2tlb.gigapage_sets, ccx.core.l2tlb.gigapage_ways, ccx.core.l2tlb.gigapage_flushLatency, ccx.core.l2tlb_verbose, "L2TLBGIG", c))
  val l2tlb_megapage  = Module(new AssociativeMemory(new tlb_entry_t(c, lvl = 1), ccx.core.l2tlb.megapage_sets, ccx.core.l2tlb.megapage_ways, ccx.core.l2tlb.megapage_flushLatency, ccx.core.l2tlb_verbose, "L2TLBMEG", c))
  val l2tlb_kilopage  = Module(new AssociativeMemory(new tlb_entry_t(c, lvl = 0), ccx.core.l2tlb.kilopage_sets, ccx.core.l2tlb.kilopage_ways, ccx.core.l2tlb.kilopage_flushLatency, ccx.core.l2tlb_verbose, "L2TLBKIL", c))
  

  // Select between IPTW and DPTW
  val l2tlb_select = UInt(2.W)


  // Select the PTW
  // Select the Cache refill
  val dbus_select = UInt(2.W)
  
  val ibus_select = UInt(2.W)
  */
  
  /**************************************************************************/
  /*                                                                        */
  /*                UOP pipeline                                            */
  /*                                                                        */
  /**************************************************************************/
  prefetch.uop_o  <> fetch.uop_i
  fetch.uop_o     <> decode.uop_i
  decode.uop_o    <> execute.uop_i
  execute.uop_o   <> retire.uop
  
  /**************************************************************************/
  /*                                                                        */
  /*                bus                                                     */
  /*                                                                        */
  /**************************************************************************/
  //fetch.ibus            <> ibus
  //dbus                  <> retire.dbus
  
  retire.dynRegs    <> dynRegs
  retire.staticRegs <> staticRegs
  fetch.dynRegs     <> dynRegs
  prefetch.dynRegs  <> dynRegs
  fetch.csr         <> retire.csrRegs
  prefetch.csr      <> retire.csrRegs


  /**************************************************************************/
  /*                                                                        */
  /*                ICACHE                                                  */
  /*                                                                        */
  /**************************************************************************/
  fetch.CacheS1     <> icache.s1
  prefetch.CacheS0  <> icache.s0

  ibus              <> icache.corebus

  /**************************************************************************/
  /*                                                                        */
  /*                regfile                                                 */
  /*                                                                        */
  /**************************************************************************/
  regfile.retire          <> retire.regs_retire
  regfile.decode          <> decode.regs_decode
  
  /**************************************************************************/
  /*                                                                        */
  /*                AUX signals                                             */
  /*                                                                        */
  /**************************************************************************/
  rvfi                    := retire.rvfi
  retire.int              := int
  retire.debug_req_i      := debug_req_i
  retire.dm_haltaddr_i    := dm_haltaddr_i

  prefetch.ctrl               <> retire.ctrl
  fetch.ctrl                  <> retire.ctrl
  decode.ctrl                 <> retire.ctrl
  execute.ctrl                <> retire.ctrl
  icache.ctrl                 <> retire.ctrl
  


  retire.ctrl.busy := prefetch.ctrl.busy || fetch.ctrl.busy || decode.ctrl.busy || execute.ctrl.busy || icache.ctrl.busy
}



import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


import chisel3.stage._
object CoreGenerator extends App {
  // Temorary disable memory configs as yosys does not know what to do with them
  // (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  implicit val ccx: CCXParams = new CCXParams()

  ChiselStage.emitSystemVerilogFile(
    new Core(),
      Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
      Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}


