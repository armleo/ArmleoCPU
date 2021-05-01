package armleocpu

import chisel3._
import chisel3.util._


object MemHostIfResponse {
	val OKAY = "b00".U(2.W)
	val SLAVEERROR = "b10".U(2.W)
	val DECODEERROR = "b11".U(2.W)
}

class MemHostIf extends Bundle {
	val address = Output(UInt(34.W))
	val response = Input(UInt(2.W))
	val burstcount = Output(UInt(5.W))
	val waitrequest = Input(Bool())

	val read = Output(Bool())
	val readdata = Input(UInt(32.W))
	val readdatavalid = Input(Bool())

	val write = Output(Bool())
	val writedata = Output(UInt(32.W))
}



class sram_1rw_io(addr_width: Int, data_width: Int, mask_width: Int) extends Bundle {
	val address = Output(UInt(addr_width.W))
	val read = Output(Bool()) // Active High
	val read_data = Input(UInt(data_width.W))

	val write = Output(Bool()) // Active High
	val write_data = Output(Vec(mask_width, UInt((data_width/mask_width).W)))
	val write_mask = Output(UInt(mask_width.W)) // Data valid if mask is high

	require(data_width % mask_width == 0)
}


class sram_1rw(addr_width: Int, data_width: Int, mask_width: Int) extends Module {
	require(data_width % mask_width == 0)
	val io = IO(Flipped(new sram_1rw_io(addr_width, data_width, mask_width)));
	val storage = SyncReadMem(1 << addr_width, Vec(mask_width, UInt((data_width/mask_width).W)), SyncReadMem.ReadFirst);
	val read_reg = RegNext(io.read)
	val saved_data = RegNext(io.read_data)
	val read_data = storage.read(io.address, io.read)
	io.read_data := Mux(read_reg, read_data, saved_data)
	when(io.write) {
		storage.write(io.address, io.write_data, VecInit(io.write_mask.asBools()))
	}
}