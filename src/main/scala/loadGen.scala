package armleocpu

import chisel3._
import chisel3.util._

import Control._
import Consts._

class LoadGen extends Module {
	val io = IO(new Bundle{
        val inword_offset = Input(UInt(3.W))
        val ld_type = Input(UInt(3.W))
		val raw_data = Input(UInt(xLen.W))
        val result_data = Output(UInt(xLen.W))
        val miss_alligned = Output(Bool())
	})

    val roffset = (io.inword_offset << 3.U)
    val rshift  = io.raw_data >> roffset

    io.result_data := MuxLookup(io.ld_type, io.raw_data.asSInt, Seq(
        LD_LH  -> rshift(15, 0).asSInt, LD_LB  -> rshift(7, 0).asSInt, LD_LW  -> rshift(31, 0).asSInt,
        LD_LHU -> rshift(15, 0).zext,   LD_LBU -> rshift(7, 0).zext,   LD_LWU -> rshift(31, 0).zext, 
        )
    ).asUInt

    io.miss_alligned :=
        ((io.ld_type === LD_LD) && (io.inword_offset.orR)) ||
        ((io.ld_type === LD_LW || io.ld_type === LD_LWU) && (io.inword_offset(1, 0).orR)) ||
        ((io.ld_type === LD_LH || io.ld_type === LD_LHU) && (io.inword_offset(0)))

}
