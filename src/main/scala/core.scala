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

  val fetch   = Module(new Fetch(c))
  val decode  = Module(new Decode(c))
  val execute = Module(new Execute(c))
  val memwb   = Module(new MemoryWriteback(c))
  val cu      = Module(new ControlUnit(c))
  
  dbus <> memwb.dbus
  int <> memwb.int
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

  val decode_uop        = Reg(new decode_uop_t(c))
  val decode_uop_valid  = RegInit(false.B)
  
  
  
  /**************************************************************************/
  /*                                                                        */
  /*                COMBINATIONAL                                           */
  /*                                                                        */
  /**************************************************************************/
  val decode_uop_accept   = Wire(Bool())
  
  
  
  
  

  /**************************************************************************/
  /*                ControlUnit Signals                                     */
  /**************************************************************************/

  // FIXME: cu.cmd := controlunit_cmd.none
  // FIXME: cu.pc_in := execute.uop_o.pc_plus_4
  cu.decode_to_cu_ready := !decode_uop_valid
  cu.execute_to_cu_ready := !execute.uop_valid_o
  cu.fetch_ready := !fetch.busy
  // FIXME: cu.wb_ready := true.B
  
  /**************************************************************************/
  /*                                                                        */
  /*                Fetch combinational signals                             */
  /*                                                                        */
  /**************************************************************************/
  fetch.uop_accept      := false.B
  fetch.cmd             := cu.cu_to_fetch_cmd
  fetch.csr_regs_output := memwb.csr_regs_output
  fetch.new_pc          := cu.pc_out

  
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


