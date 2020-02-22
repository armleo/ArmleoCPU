package armleocpu

import chisel3._
import chisel3.util._

import Control._


class LoadGen extends Module {
	val io = IO(new Bundle{
        val inwordOffset = Input(UInt(2.W))
        val ld_type = Input(UInt(3.W))
		val rawData = Input(UInt(32.W))
        val result = Output(UInt(32.W))
        val missAlligned = Output(Bool())
	})
    val roffset = (io.inwordOffset << 3.U)
    val rshift  = io.rawData >> roffset
    io.result := MuxLookup(io.ld_type, io.rawData.asSInt, Seq(
        LD_LH  -> rshift(15, 0).asSInt, LD_LB  -> rshift(7, 0).asSInt,
        LD_LHU -> rshift(15, 0).zext,   LD_LBU -> rshift(7, 0).zext)
    ).asUInt

    io.missAlligned :=
        ((io.ld_type === LD_LW) && (io.inwordOffset.orR)) ||
        ((io.ld_type === LD_LH || io.ld_type === LD_LHU) && (io.inwordOffset(0)))

}
