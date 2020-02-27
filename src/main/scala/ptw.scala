package armleocpu

import chisel3._
import chisel3.util._


class PTW(debug: Boolean) extends Module {
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
	// constant outputs
	io.memory.write := false.B
	io.memory.writedata := 0.U
	

	val current_table_base = RegInit(0.U(22.W)) // a from spec
	val current_level = RegInit(0.U(2.W)) // i from spec
	val STATE_IDLE = 0.U(2.W);
	val STATE_TABLE_WALKING = 1.U(2.W);
	val state = RegInit(STATE_IDLE)
	val read_issued = RegInit(true.B)
	val saved_virtual_address = RegInit(0.U(20.W))
	val virtual_address_vpn = Wire(Vec(2, UInt(10.W)))
	virtual_address_vpn(0) := saved_virtual_address(9, 0)
	virtual_address_vpn(1) := saved_virtual_address(19, 10)
	
	// internal

	val pte_invalid = Wire(Bool())
	pte_invalid := !io.memory.readdata(0) || (!io.memory.readdata(1) && io.memory.readdata(2))
	val pte_isLeaf = Wire(Bool())
	pte_isLeaf := io.memory.readdata(1) || io.memory.readdata(2)
	
	// outputs
	io.memory.burstcount := 1.U;
	io.memory.address := Cat(current_table_base, virtual_address_vpn(current_level), "b00".U(2.W));
	io.memory.read := !read_issued && state === STATE_TABLE_WALKING
	io.physical_address_top := Cat(io.memory.readdata(31, 20), Mux(current_level === 1.U, saved_virtual_address(9, 0), io.memory.readdata(19, 10)))


	io.done := false.B
	io.pagefault := false.B
	io.access_fault := false.B
	io.access_bits := io.memory.readdata(7, 0)
	switch(state) {
		is(STATE_IDLE) {
			io.done := false.B
			read_issued := false.B
			current_level := 1.U;
			saved_virtual_address := io.virtual_address(31, 12)
			current_table_base := io.satp_ppn;
			when(io.request) { // asumes io.satp_mode -> 1 
								//because otherwise tlb would respond with hit
				state := STATE_TABLE_WALKING
				if(debug)
					printf("[PTW] Resolve requested for virtual address 0x%x", io.virtual_address)
			}
		}
		is(STATE_TABLE_WALKING) {
			io.done := false.B
			when(!io.memory.waitrequest) {
				read_issued := true.B;
			}
			when(!io.memory.waitrequest && io.memory.readdatavalid) {
				when(io.memory.response =/= MemHostIfResponse.OKAY) {
					io.access_fault := true.B
					io.done := true.B
					state := STATE_IDLE
					if(debug)
						printf("[PTW] Resolve failed because io.memory.response is 0x%x for address 0x%x", io.memory.response, io.memory.address)
				} .elsewhen(pte_invalid) {
					io.done := true.B
					io.pagefault := true.B
					state := STATE_IDLE
					if(debug)
						printf("[PTW] Resolve failed because pte 0x%x is invalid is 0x%x for address 0x%x", io.memory.readdata(7, 0), io.memory.response, io.memory.address)
				} .elsewhen(pte_isLeaf) {
					io.done := true.B
					state := STATE_IDLE
					if(debug)
						printf("[PTW] Resolve done 0x%x for address 0x%x", Cat(io.physical_address_top, 0.U(10.W)), Cat(saved_virtual_address, 0.U(12.W)))
				} .otherwise { // pte is pointer to next level
					when(current_level === 0.U) {
						io.done := true.B
						io.pagefault := true.B
						state := STATE_IDLE
						if(debug)
							printf("[PTW] Resolve pagefault for address 0x%x", Cat(saved_virtual_address, 0.U(12.W)))
					} .otherwise {
						current_level := current_level - 1.U
						current_table_base := io.memory.readdata(31, 10)
						read_issued := false.B
						if(debug)
							printf("[PTW] Resolve going to next level for address 0x%x", Cat(saved_virtual_address, 0.U(12.W)))
					}
				}
			}
		}
	}

}
