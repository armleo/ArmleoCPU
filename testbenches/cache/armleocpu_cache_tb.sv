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


logic [31:0] c_address;
logic c_wait, c_pagefault, c_accessfault, c_done;


logic c_execute;

logic c_load;
logic [2:0] c_load_type;
logic [31:0] c_load_data;
logic c_load_unknowntype, c_load_missaligned;


logic c_store;
logic [1:0] c_store_type;
logic [31:0] c_store_data;
logic c_store_unknowntype, c_store_missaligned;

logic c_flush;

logic c_flushing, c_flush_done;

armleocpu_cache cache(
    .clk(clk),
    .rst_n(rst_n),

    //                      CACHE <-> EXECUTE/MEMORY
    c_address,
    output logic            c_wait,
    output logic            c_pagefault,
    output logic            c_accessfault,
    output logic            c_done,

    input                   c_execute, // load is for further execution, used by fetch

    input                   c_load,
    input  [2:0]            c_load_type, // enum defined in armleocpu_defs
    output logic [31:0]     c_load_data,
    output logic            c_load_unknowntype,
    output logic            c_load_missaligned,

    input                   c_store,
    input [1:0]             c_store_type, // enum defined in armleocpu_defs
    input [31:0]            c_store_data,
    output logic            c_store_unknowntype,
    output logic            c_store_missaligned,
    
    input                   c_flush,
    output logic            c_flushing,
    output logic            c_flush_done,
    
    `ifdef DEBUG
        output logic        c_miss,
    `endif


    //                      CACHE <-> CSR
    input                   csr_matp_mode, // Mode = 0 -> physical access, 1 -> ppn valid
    input        [21:0]     csr_matp_ppn,
    
    //                      CACHE <-> MEMORY
    output logic [33:0]     m_address,
    output logic [OFFSET_W:0]m_burstcount,
    input                   m_waitrequest,
    input        [1:0]      m_response,
    
    output logic            m_read,
    input        [31:0]     m_readdata,
    input                   m_readdatavalid,
    
    output logic            m_write,
    output logic [31:0]     m_writedata,
    output logic [3:0]      m_byteenable
    
    `ifdef DEBUG
    , output trace_error

    `endif
);


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