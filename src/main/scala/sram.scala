package armleocpu


import chisel3._
import chisel3.util._


class sram_1rw_io(addr_width: Int, data_width: Int, mask_width: Int) extends Bundle {
	
	val address = Output(UInt(addr_width.W))
	val read = Output(Bool()) // Active High
	val read_data = Input(Vec(mask_width, UInt((data_width/mask_width).W)))

	val write = Output(Bool()) // Active High
	val write_data = Output(Vec(mask_width, UInt((data_width/mask_width).W)))
	val write_mask = Output(UInt(mask_width.W)) // Data valid if mask is high

	require(data_width % mask_width == 0)
}

// CRITICAL: Assume that read and write is not possible at the same time
// CRITICAL: ReadFirst/WriteFirst is impossible if we need to replace it with sram cells
class sram_1rw(depth_arg: Int, data_width: Int, mask_width: Int) extends Module {

	require(data_width % mask_width == 0)

	val data_depth = depth_arg
	val addr_width = log2Ceil(data_depth)

	val io = IO(Flipped(new sram_1rw_io(addr_width, data_width, mask_width)));
	chisel3.assert(!(io.read && io.write))

	val storage = SyncReadMem(data_depth, Vec(mask_width, UInt((data_width/mask_width).W)))

	val read_reg = RegInit(false.B)
	read_reg := io.read

	val saved_data = RegInit(
		VecInit(
			Seq.fill(mask_width) {0.U((data_width/mask_width).W)}
		)
	)

	val read_data = storage.read(io.address, io.read)
	println(read_data)

	io.read_data := Mux(read_reg, read_data, saved_data)

	saved_data := io.read_data

	when(io.write) {
		storage.write(io.address, io.write_data, VecInit(io.write_mask.asBools()))
	}
}