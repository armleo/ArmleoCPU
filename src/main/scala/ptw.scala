package armleocpu

import chisel3._
import chisel3.util._


class PTW() extends Module {
	val io = IO(new Bundle {
		// memory access bus
		val memory = new MemHostIf()

		
		// result
		val done = Output(Bool())
		val pagefault = Output(Bool())
	 	val access_fault = Output(Bool())
		val physical_address_top = Output(UInt(22.W))
		val access_bits = Output(UInt(8.W))

		// request
		val virtual_address = Input(UInt(32.W))
		val request = Input(Bool())
		
		// CSR values
		val satp_mode = Input(Bool())
		val satp_ppn = Input(UInt(22.W))
	})
	
	io.memory.write := false.B
	io.memory.writedata := 0.U


	io.done := false.B
	io.pagefault := false.B
	io.access_bits := io.memory.readdata(7, 0)
	io.physical_address_top := Cat(io.memory.readdata(31, 20), Mux(current_level, virtual_address(21, 12), io.memory.readdata(19, 10)))
	val current_table_base = Reg(UInt(22.W)) // a from spec
	val current_level = Reg(UInt(2.W)) // i from spec
	val STATE_IDLE = 0.U(2.W);
	val STATE_TABLE_WALKING = 1.U(2.W);
	val state = RegInit(STATE_IDLE)
	val read_issued = RegInit(true.B)
	val saved_virtual_address = Reg(UInt(20.W))
	val virtual_address_vpn = Wire(Vec(2, UInt(10.W)))
	virtual_address_vpn(0) := saved_virtual_address(9, 0)
	virtual_address_vpn(1) := saved_virtual_address(19, 10)
	val pte_invalid = !io.memory.readdata(0) || (!io.memory.readdata(1) && io.memory.readdata(2))
	val pte_isLeaf = !io.memory.readdata(1) || !io.memory.readdata(2)
	io.memory.burstcount := 1.U;
	io.memory.address := Cat(current_table_base, virtual_address_vpn(current_level), "b00".U(2.W));
	io.memory.read := !read_issued && state === STATE_TABLE_WALKING

	switch(state) {
		is(STATE_IDLE) {
			when(io.read_req || io.write_req) { // asumes io.satp_mode -> 1 
												//because otherwise tlb would respond with hit
				
				read_issued := false.B
				saved_virtual_address := io.virtual_address
				current_table_base := io.satp_ppn;
				current_level := 1.U;
			}
		}
		is(STATE_TABLE_WALKING) {
			when(!io.memory.waitrequest) {
				read_issued := true.B;
			}
			when(!io.memory.waitrequest && io.memory.readdatavalid) {
				when(io.memory.status != MemHostIf.OKAY) {
					io.access_fault := true.B
					io.done := true.B
					state := STATE_IDLE
				} .elsewhen(pte_invalid) {
					io.done := true.B
					io.pagefault := true.B
					state := STATE_IDLE
				} .elsewhen(pte_isLeaf) {
					io.done := true.B
					state := STATE_IDLE
				} .otherwise { // pte is pointer to next level
					when(current_level === 0) {
						io.done := true.B
						io.pagefault := true.B
						state := STATE_IDLE
					} .otherwise {
						current_level := current_level - 1.U
						current_table_base := io.memory.readdata(31, 10)
						read_issued := false.B
					}
				}
			}
		}
	}

}
