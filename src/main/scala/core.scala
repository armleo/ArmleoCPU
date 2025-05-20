package armleocpu


import chisel3._
import chisel3.util._

import chisel3.util._
import chisel3.experimental.dataview._

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

  /**************************************************************************/
  /*                                                                        */
  /*                Submodules                                              */
  /*                                                                        */
  /**************************************************************************/

  //val cu      = Module(new ControlUnit(c))
  //val regfile = Module(new Regfile(c)) // All top connections done

  val fetch   = Module(new Fetch(c))

  /*
  val decode  = Module(new Decode(c))
  val execute = Module(new Execute(c))
  val memwb   = Module(new MemoryWriteback(c))

  val l2tlb_gigapage  = Module(new AssociativeMemory(new tlb_entry_t(c, lvl = 2), c.l2tlb.gigapage_sets, c.l2tlb.gigapage_ways, c.l2tlb.gigapage_flushLatency, c.l2tlb_verbose, "L2TLBGIG", c))
  val l2tlb_megapage  = Module(new AssociativeMemory(new tlb_entry_t(c, lvl = 1), c.l2tlb.megapage_sets, c.l2tlb.megapage_ways, c.l2tlb.megapage_flushLatency, c.l2tlb_verbose, "L2TLBMEG", c))
  val l2tlb_kilopage  = Module(new AssociativeMemory(new tlb_entry_t(c, lvl = 0), c.l2tlb.kilopage_sets, c.l2tlb.kilopage_ways, c.l2tlb.kilopage_flushLatency, c.l2tlb_verbose, "L2TLBKIL", c))
  

  // Select between IPTW and DPTW
  val l2tlb_select = UInt(2.W)


  // Select the PTW
  // Select the Cache refill
  val dbus_select = UInt(2.W)
  
  val ibus_select = UInt(2.W)

  */
  fetch.ibus            <> ibus
  /*
  fetch.csr_regs_output <> memwb.csr_regs_output

  fetch.cmd             := cu.cu_to_fetch_cmd
  fetch.csr_regs_output := memwb.csr_regs_output
  fetch.new_pc          := cu.pc_out
  fetch.uop.ready       := decode.fetch_uop_accept 
  */
  // Temporary for the purpose of testing fetch
  fetch.uop.ready       := true.B

  /*
  decode.decode_uop_accept    := execute.decode_uop_accept
  decode.fetch_uop            := fetch.uop.bits
  decode.fetch_uop_valid      := fetch.uop.valid

  execute.decode_uop_valid    := decode.decode_uop_valid
  execute.decode_uop          := decode.decode_uop
  execute.uop_accept          := memwb.accept

  execute.kill                := cu.kill
  decode.kill                 := cu.kill
  cu.wb_io                    <> memwb.cu
  cu.decode_to_cu_ready       := !decode.decode_uop_valid
  cu.execute_to_cu_ready      := !execute.uop_valid_o
  cu.fetch_ready              := !fetch.busy
  
  
  regfile.memwb         <> memwb.regs_memwb
  regfile.decode        <> decode.regs_decode
  
  dbus                  <> memwb.dbus
  rvfi                  := memwb.rvfi
  memwb.int             := int
  memwb.debug_req_i     := debug_req_i
  memwb.dm_haltaddr_i   := dm_haltaddr_i
  memwb.uop             := execute.uop_o
  memwb.valid           := execute.uop_valid_o
  */

}



import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


import chisel3.stage._
object CoreGenerator extends App {
  // Temorary disable memory configs as yosys does not know what to do with them
  // (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  ChiselStage.emitSystemVerilogFile(
    new Core(
        new CoreParams()
      ),
      Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
      Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}


