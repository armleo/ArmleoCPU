package armleocpu

import chisel3._
import chisel3.util._

import Control._


class StoreGen extends Module {
    val io = IO(new Bundle{
        val inwordOffset = Input(UInt(2.W))
        val st_type = Input(UInt(2.W))
        val rawWritedata = Input(UInt(32.W))
		val resultWritedata = Output(UInt(32.W))
        val mask = Output(UInt(4.W))
        val missAlligned = Output(Bool())
	})
    
    io.mask := MuxLookup(io.st_type, 
              /*default=*/"b0000".U, Seq(
        ST_SW ->  "b1111".U,
        ST_SH -> ("b11".U << io.inwordOffset),
        ST_SB -> ("b1".U  << io.inwordOffset))
    )
    
    val woffset = (io.inwordOffset << 3.U)
    val writedata = Wire(UInt(32.W))
    writedata := io.rawWritedata << woffset
    io.resultWritedata := writedata
    
    io.missAlligned :=
        ((io.st_type === ST_SW) && (io.inwordOffset.orR)) ||
        ((io.st_type === ST_SH) && (io.inwordOffset(0)))

}