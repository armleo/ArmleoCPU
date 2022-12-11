package armleocpu

import chisel3._
import chisel3.util._

import Instructions._


class LoadGen(val c: CoreParams) extends Module {
	val io = IO(new Bundle{
    val inword_offset = Input(UInt(3.W))
    // FIXME: Check for top level to set the MSB to zero if xLen == 32
    val instr = Input(UInt(c.archParams.iLen.W))
		val in = Input(UInt(c.archParams.xLen.W))
    val out = Output(UInt(c.archParams.xLen.W))
    val misaligned = Output(Bool())
	})
    
  val rshift  = io.in >> (io.inword_offset << 3.U)

  io.out := rshift

  when(io.instr === LB)   {io.out := rshift( 7, 0).asSInt.pad(c.archParams.xLen).asUInt}
  when(io.instr === LBU)  {io.out := rshift( 7, 0).asUInt.pad(c.archParams.xLen)}
  when(io.instr === LH)   {io.out := rshift(15, 0).asSInt.pad(c.archParams.xLen).asUInt}
  when(io.instr === LHU)  {io.out := rshift(15, 0).asUInt.pad(c.archParams.xLen)}
  when((io.instr === LW)
  || (io.instr === LR_W)) {io.out := rshift(31, 0).asSInt.pad(c.archParams.xLen).asUInt}
  when(io.instr === LWU)  {io.out := rshift(31, 0).asUInt.pad(c.archParams.xLen)}
  
  io.misaligned :=
      ((io.instr === LD || io.instr === LR_D) && (io.inword_offset.orR)) ||
      ((io.instr === LW || io.instr === LWU || io.instr === LR_W) && (io.inword_offset(1, 0).orR)) ||
      ((io.instr === LH || io.instr === LHU) && (io.inword_offset(0)))

}