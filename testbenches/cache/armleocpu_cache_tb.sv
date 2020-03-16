`timescale 1ns/1ns
module cache_testbench;

`include "../sync_clk_gen_template.svh"

`include "../../src/armleocpu_defs.sv"




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
logic c_flushing, c_flush_done, c_miss;

logic csr_matp_mode;
logic [21:0] csr_matp_ppn;

logic [33:0] m_address;
logic [4:0] m_burstcount;

logic m_waitrequest;
logic [1:0] m_response;

logic m_read, m_write, m_readdatavalid;
logic [31:0] m_readdata, m_writedata;
logic [3:0] m_byteenable;


armleocpu_cache cache(
    .*
);

// 1st 4KB is not used
// 2nd 4KB is megapage table
// 3rd 4KB is page table
// 4th 4KB is data page 0
// 5th 4KB is data page 1
// 6th 4KB is data page 2
// 7th 4KB is data page 3
// Remember: mem addressing is word based


reg [31:0] mem [32*1024-1:0];
reg [32*1024-1:0] pma_error = 0;

initial begin
    m_response = 2'b11;

    m_readdata = 0;
    m_readdatavalid = 0;
end



assign m_waitrequest = !m_read && !m_readdatavalid;


wire k = pma_error[m_address >> 2];
wire [31:0] m = m_address >> 2;
always @(posedge clk) begin
	if(m_read) begin
		m_readdata <= mem[m_address >> 2];
		m_readdatavalid <= 1;
		
		if(pma_error[m_address >> 2] === 1) begin
			m_response <= 2'b11;
		end else begin
			m_response <= 2'b00;
		end
	end else begin
		m_readdatavalid <= 0;
		m_response <= 2'b11;
	end
end






initial begin
    
    c_address = 0;
    c_execute = 0;

    c_load = 0;
    c_load_type = LOAD_WORD;
    
    c_store = 0;
    c_store_type = STORE_WORD;
    c_store_data = 0;
    
    c_flush = 0;

    csr_matp_mode = 0;
    csr_matp_ppn = 1;

    // PTW Megapage Access fault
    @(negedge clk)
	//mem[];
	//csr_matp_mode = 1;
	
    //c_address = {10'h1, 10'h0, 12'h0};
    //c_load = 1;
	c_flush = 1;
    @(posedge clk)
    @(posedge clk)
    @(posedge clk)
    @(posedge clk)
    repeat(16) @(posedge clk);
	@(negedge clk)
    c_load = 0;
	@(posedge clk)
    #1000
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