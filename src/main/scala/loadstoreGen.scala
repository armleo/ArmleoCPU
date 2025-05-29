package armleocpu

import chisel3._
import chisel3.util._

import Instructions._


class StoreGen(ccx: CCXParams) extends Module {
  val io = IO(new Bundle{
    val vaddr = Input(UInt(ccx.avLen.W))
    val instr = Input(UInt(ccx.iLen.W))
    
    val in = Input(UInt(ccx.xLen.W))
		val out = Output(UInt(ccx.xLen.W))
    val mask = Output(UInt(8.W))
    val misaligned = Output(Bool())
	})
  
  val inword_offset = io.vaddr(2, 0)
  val bitoffset = (inword_offset << 3.U)

  when(io.instr === SD || io.instr === SC_D) {
    io.mask       := "b11111111".U
    io.out        := io.in
    io.misaligned := inword_offset.orR
  } .elsewhen (io.instr === SW || io.instr === SC_W) {
    io.mask       := ("b1111".U << inword_offset)
    io.out        := io.in >> bitoffset
    io.misaligned := inword_offset(1, 0).orR
  } .elsewhen (io.instr === SH) {
    io.mask       := ("b11".U << inword_offset)
    io.out        := io.in >> bitoffset
    io.misaligned := inword_offset(0).orR
  } .elsewhen (io.instr === SB) {
    io.mask       := "b1".U  << inword_offset
    io.out        := io.in >> bitoffset
    io.misaligned := false.B
  } .otherwise {
    io.mask       := "b00000000".U
    io.out        := io.in
    io.misaligned := false.B
  }
}


class LoadGen(ccx: CCXParams) extends Module {
	val io = IO(new Bundle{
    val vaddr = Input(UInt(ccx.avLen.W))
    val instr = Input(UInt(ccx.iLen.W))

		val in = Input(UInt(ccx.xLen.W))
    val out = Output(UInt(ccx.xLen.W))
    val misaligned = Output(Bool())
	})
  
  require(ccx.xLen == 64)
  val inword_offset = io.vaddr(2, 0)

  val rshift  = io.in >> (inword_offset << 3.U)

  io.out := rshift

  when(io.instr === LB)   {io.out := rshift( 7, 0).asSInt.pad(ccx.xLen).asUInt}
  when(io.instr === LBU)  {io.out := rshift( 7, 0).asUInt.pad(ccx.xLen)}
  when(io.instr === LH)   {io.out := rshift(15, 0).asSInt.pad(ccx.xLen).asUInt}
  when(io.instr === LHU)  {io.out := rshift(15, 0).asUInt.pad(ccx.xLen)}
  when((io.instr === LW)
  || (io.instr === LR_W)) {io.out := rshift(31, 0).asSInt.pad(ccx.xLen).asUInt}
  when(io.instr === LWU)  {io.out := rshift(31, 0).asUInt.pad(ccx.xLen)}
  
  io.misaligned :=
      ((io.instr === LD || io.instr === LR_D) && (inword_offset.orR)) ||
      ((io.instr === LW || io.instr === LWU || io.instr === LR_W) && (inword_offset(1, 0).orR)) ||
      ((io.instr === LH || io.instr === LHU) && (inword_offset(0)))

}