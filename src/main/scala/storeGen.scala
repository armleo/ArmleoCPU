package armleocpu

import chisel3._
import chisel3.util._

import Control._
import Consts._


class StoreGen extends Module {
    val io = IO(new Bundle{
        val inword_offset = Input(UInt(3.W))
        val st_type = Input(UInt(2.W))
        val raw_write_data = Input(UInt(xLen.W))
		val result_write_data = Output(UInt(xLen.W))
        val mask = Output(UInt(8.W))
        val miss_alligned = Output(Bool())
	})
    
    io.mask := MuxLookup(io.st_type, 
              /*default=*/"b00000000".U, Seq(
        ST_SD ->  "b11111111".U,
        ST_SW -> ("b1111".U << io.inword_offset),
        ST_SH -> ("b11".U << io.inword_offset),
        ST_SB -> ("b1".U  << io.inword_offset))
    )
    
    val write_offset = (io.inword_offset << 3.U)
    val write_data = Wire(UInt(xLen.W))
    write_data := io.raw_write_data << write_offset
    io.result_write_data := write_data
    
    io.miss_alligned :=
        ((io.st_type === ST_SD) && (io.inword_offset.orR)) || // If any bits are set then double word is missaligned
        ((io.st_type === ST_SW) && (io.inword_offset(1, 0).orR)) || // if lsb 2 bits are set then word is missaligned
        ((io.st_type === ST_SH) && (io.inword_offset(0))) // if lsb is set then half word is missaligned

}