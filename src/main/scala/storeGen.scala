package armleocpu

import chisel3._
import chisel3.util._

import Instructions._

class StoreGen(val c: CoreParams) extends Module {
  val io = IO(new Bundle{
    val inword_offset = Input(UInt(3.W))
    val instr = Input(UInt(c.archParams.iLen.W))
    val in = Input(UInt(c.archParams.xLen.W))
		val out = Output(UInt(c.archParams.xLen.W))
    val mask = Output(UInt(8.W))
    val misaligned = Output(Bool())
	})
    
  val bitoffset = (io.inword_offset << 3.U)

  when(io.instr === SD || io.instr === SC_D) {
    io.mask       := "b11111111".U
    io.out        := io.in
    io.misaligned := io.inword_offset.orR
  } .elsewhen (io.instr === SW || io.instr === SC_W) {
    io.mask       := ("b1111".U << io.inword_offset)
    io.out        := io.in >> bitoffset
    io.misaligned := io.inword_offset(1, 0).orR
  } .elsewhen (io.instr === SH) {
    io.mask       := ("b11".U << io.inword_offset)
    io.out        := io.in >> bitoffset
    io.misaligned := io.inword_offset(0).orR
  } .elsewhen (io.instr === SB) {
    io.mask       := "b1".U  << io.inword_offset
    io.out        := io.in >> bitoffset
    io.misaligned := false.B
  } .otherwise {
    io.mask       := "b00000000".U
    io.out        := io.in
    io.misaligned := false.B
  }
}