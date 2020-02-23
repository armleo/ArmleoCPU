package armleocpu

import chisel3._
import chisel3.util._


class Cache(LANES_W : Int, TLB_ENTRIES_W: Int, debug: Boolean, mememulate: Boolean) extends Module {
	val io = IO(new Bundle {
		val memory = new MemHostIf()

		val address = Input(UInt(32.W))
		
		val done = Output(Bool())
		val pagefault = Output(Bool())
	 	
		val write = Input(Bool())

		val read = Input(Bool())
		
		val satp_mode = Input(Bool())
		val satp_ppn = Input(UInt(22.W))
	})
    memory.burstcount := 1;
	m_address = {table_base, va_vpn[ptw_level], 2'b00};
}
