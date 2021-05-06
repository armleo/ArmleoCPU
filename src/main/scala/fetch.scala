package armleocpu


import chisel3._
import chisel3.util._

import Control._

/*
class Fetch(PC_START: Int, LANES_W: Int, TLB_ENTRIES_W:Int, debug: Boolean, mememulate: Boolean) extends Module {
	val io = IO(new Bundle{
			val instr = Output(UInt(32.W))
			val pc = Output(UInt(32.W))
			val pipeline_wait = Input(Bool())
			val pipeline_kill = Input(Bool())
			val cache_flush = Input(Bool())
			val pc_sel = Input(UInt(2.W))
      val expt = Input(Bool())
      val evec = Input(UInt(32.W))
      val epc = Input(UInt(32.W))
      val alu_sum = Input(UInt(32.W))
	    val br_taken = Input(Bool())
      val memhost = new MemHostIf()
	})
  val icache = Module(new Cache(LANES_W, TLB_ENTRIES_W, debug, mememulate))
  icache.io.memory <> io.memhost
  val started = RegNext(reset.toBool)
  val stall = io.pipeline_wait || !icache.io.done
  val pc   = RegInit(PC_START.U(32.W) - 4.U(32.W))

  val npc  = Mux(stall,                                       pc,             // stall                      
          Mux(io.expt,                                        io.evec,        // exception
          Mux(io.pc_sel === PC_EPC,                           io.epc,         // eret
          Mux(io.pc_sel === PC_ALU || io.br_taken,            io.alu_sum >> 1.U << 1.U,   // branch, jump
          Mux(io.pc_sel === PC_0,                             pc,                      // saved pc
                                                              pc + 4.U)))))            // next instruction
  val inst = Mux(
    started || io.pipeline_kill || io.br_taken || io.expt || icache.io.pipeline_wait,
    Instructions.NOP, icache.io.readdata)
    // if met,          else
  pc                      := npc
  icache.io.address       := npc
  icache.io.writedata     := 0.U
  icache.io.write         := false.B
  icache.io.st_type       := ST_XXX
  icache.io.ld_type       := LD_LW
  icache.io.read          := !io.pipeline_wait
  
  when (!stall) {
    io.pc    := pc
    io.instr := inst
  }
}
	*/