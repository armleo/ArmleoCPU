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
		val resolve_req = Input(Bool())
		val resolve_ack = Output(Bool())
		
		// CSR values
		val matp_mode = Input(Bool())
		val matp_ppn = Input(UInt(22.W))
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
	val saved_offset = RegInit(0.U(12.W))
	val virtual_address_vpn = Wire(Vec(2, UInt(10.W)))
	virtual_address_vpn(0) := saved_virtual_address(9, 0)
	virtual_address_vpn(1) := saved_virtual_address(19, 10)
	
	// internal
	val pte_valid = io.memory.readdata(0)
	val pte_read = io.memory.readdata(1)
	val pte_write = io.memory.readdata(2)
	val pte_execute = io.memory.readdata(3)

	val pte_ppn1 = io.memory.readdata(31, 20)
	val pte_ppn0 = io.memory.readdata(19, 10)

	val pte_invalid = !pte_valid || (!pte_read && pte_write)
	val pte_isLeaf = pte_read || pte_execute
	val pte_leafMissaligned = Mux(current_level === 1.U,
												io.memory.readdata(19, 10) =/= 0.U, // level == megapage
												false.B)								// level == page => impossible missaligned
	val pte_pointer = io.memory.readdata(3, 0) === "b0001".U
	// outputs
	io.memory.burstcount := 1.U;
	io.memory.address := Cat(current_table_base, virtual_address_vpn(current_level), "b00".U(2.W));
	io.memory.read := !read_issued && state === STATE_TABLE_WALKING
	io.physical_address_top := Cat(io.memory.readdata(31, 20), Mux(current_level === 1.U, saved_virtual_address(9, 0), io.memory.readdata(19, 10)))


	io.done := false.B
	io.pagefault := false.B
	io.access_fault := false.B
	io.access_bits := io.memory.readdata(7, 0)
	io.resolve_ack := false.B
	switch(state) {
		is(STATE_IDLE) {
			io.done := false.B
			io.pagefault := false.B
			io.access_fault := false.B
			read_issued := false.B
			current_level := 1.U;
			saved_virtual_address := io.virtual_address(31, 12)
			saved_offset := io.virtual_address(11, 0) // used for debug purposes only
			current_table_base := io.matp_ppn;
			when(io.resolve_req) { // assumes io.matp_mode -> 1 
								//because otherwise tlb would respond with hit and ptw request would not happen
				state := STATE_TABLE_WALKING
				io.resolve_ack := true.B
				if(debug)
					printf("[PTW] Resolve requested for virtual address 0x%x, io.matp_mode is 0x%x\n", io.virtual_address, io.matp_mode)
			}
		}
		is(STATE_TABLE_WALKING) {
			io.done := false.B
			io.pagefault := false.B
			io.access_fault := false.B

			when(!io.memory.waitrequest) {
				read_issued := true.B;
			}
			when(!io.memory.waitrequest && io.memory.readdatavalid) {
				when(io.memory.response =/= MemHostIfResponse.OKAY) {
					io.access_fault := true.B
					io.pagefault := false.B
					io.done := true.B
					state := STATE_IDLE
					if(debug)
						printf("[PTW] Resolve failed because io.memory.response is 0x%x for address 0x%x\n", io.memory.response, io.memory.address)
				} .elsewhen(pte_invalid) {
					io.done := true.B
					io.pagefault := true.B
					io.access_fault := false.B
					state := STATE_IDLE
					if(debug)
						printf("[PTW] Resolve failed because pte 0x%x is invalid is 0x%x for address 0x%x\n", io.memory.readdata(7, 0), io.memory.readdata, io.memory.address)
				} .elsewhen(pte_isLeaf) {
					when(!pte_leafMissaligned) {
						io.done := true.B
						io.pagefault := false.B
						io.access_fault := false.B
						state := STATE_IDLE
						if(debug)
							printf("[PTW] Resolve done 0x%x for address 0x%x\n", Cat(io.physical_address_top, saved_offset), Cat(saved_virtual_address, saved_offset))
					} .elsewhen (pte_leafMissaligned){
						io.done := true.B
						io.pagefault := true.B
						io.access_fault := false.B
						state := STATE_IDLE
						if(debug)
							printf("[PTW] Resolve missaligned 0x%x for address 0x%x, level = 0x%x\n", Cat(io.physical_address_top, saved_offset), Cat(saved_virtual_address, saved_offset), current_level)
					}
				} .elsewhen (pte_pointer) { // pte is pointer to next level
					when(current_level === 0.U) {
						io.done := true.B
						io.pagefault := true.B
						io.access_fault := false.B
						state := STATE_IDLE
						if(debug)
							printf("[PTW] Resolve pagefault for address 0x%x\n", Cat(saved_virtual_address, saved_offset))
					} .elsewhen(current_level === 1.U) {
						io.access_fault := false.B
						io.done := false.B
						io.pagefault := false.B
						current_level := current_level - 1.U
						current_table_base := io.memory.readdata(31, 10)
						read_issued := false.B
						if(debug)
							printf("[PTW] Resolve going to next level for address 0x%x, pte = %x\n", Cat(saved_virtual_address, saved_offset), io.memory.readdata)
					}
				}
			}
		}
	}
}
