`timescale 1ns/1ns
module cache_testbench;

`include "../clk_gen_template.svh"

`include "../../src/armleocpu_defs.sv"

initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
	#100
	$finish;
end

/*

PTW Megapage Access fault
PTW Page access fault
Cache memory access fault

PTW Megapage pagefault
PTW Page pagefault
Cache memory pagefault for each case (read, write, execute, access, dirty, user)

For two independent lanes
	For each csr_satp_mode = 0 and csr_satp_mode = 1
		For address[33:32] = 0 and address[33:32] != 0
			For each load type and store type combination
				Bypassed load
				Bypassed load after load
				Bypassed store
				Bypassed load after store
				Bypassed store after store

				Cached load
				Cached load after load
				Cached store
				Cached load after store
				Cached store after store
		For each unknown type for load
			Bypassed load
			Cached load
		For each unknown type for store
			Bypassed store
			Cached store
		For each missaligned address for each store case
			Bypassed store
			Cached store
		For each missaligned address for each load case
			Bypassed load
			Cached load
	Flush

Generate random access pattern using GLFSR, check for validity
*/


endmodule