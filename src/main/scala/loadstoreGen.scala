package armleocpu

import chisel3._
import chisel3.util._

import Instructions._

object tobus {
  def apply(c: CoreParams, regvalue: UInt): UInt = {
    return Fill((c.xLen_bytes) / c.busBytes, regvalue)
  }
}

object frombus {
  def apply(c: CoreParams, addr: UInt, busvalue: UInt): UInt = {
    // Selector e.g. for xLen = 64 => 8 bytes, dataBytes = 16 => 1 bit selector
    // => addr(3, 3)
    // xLen = 64, dataBytes = 8 => selector 0 bits => use busvalue
    
    assert(false, "BUGGED")
    return if(c.busBytes == c.xLen_bytes) busvalue 
          else busvalue.asTypeOf(Vec((c.xLen_bytes) / c.busBytes, UInt(c.xLen.W)))(addr(log2Ceil(c.busBytes) - 1, log2Ceil(c.xLen_bytes)))
  }
}


class StoreGen(val c: CoreParams) extends Module {
  val io = IO(new Bundle{
    val vaddr = Input(UInt(c.avLen.W))
    val instr = Input(UInt(c.iLen.W))
    
    val in = Input(UInt(c.xLen.W))
		val out = Output(UInt(c.xLen.W))
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


class LoadGen(val c: CoreParams) extends Module {
	val io = IO(new Bundle{
    val vaddr = Input(UInt(c.avLen.W))
    val instr = Input(UInt(c.iLen.W))

		val in = Input(UInt(c.xLen.W))
    val out = Output(UInt(c.xLen.W))
    val misaligned = Output(Bool())
	})
  
  require(c.xLen == 64)
  val inword_offset = io.vaddr(2, 0)

  val rshift  = io.in >> (inword_offset << 3.U)

  io.out := rshift

  when(io.instr === LB)   {io.out := rshift( 7, 0).asSInt.pad(c.xLen).asUInt}
  when(io.instr === LBU)  {io.out := rshift( 7, 0).asUInt.pad(c.xLen)}
  when(io.instr === LH)   {io.out := rshift(15, 0).asSInt.pad(c.xLen).asUInt}
  when(io.instr === LHU)  {io.out := rshift(15, 0).asUInt.pad(c.xLen)}
  when((io.instr === LW)
  || (io.instr === LR_W)) {io.out := rshift(31, 0).asSInt.pad(c.xLen).asUInt}
  when(io.instr === LWU)  {io.out := rshift(31, 0).asUInt.pad(c.xLen)}
  
  io.misaligned :=
      ((io.instr === LD || io.instr === LR_D) && (inword_offset.orR)) ||
      ((io.instr === LW || io.instr === LWU || io.instr === LR_W) && (inword_offset(1, 0).orR)) ||
      ((io.instr === LH || io.instr === LHU) && (inword_offset(0)))

}