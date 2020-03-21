`timescale 1ns/1ns
module cache_testbench;

`include "../sync_clk_gen_template.svh"

`include "../../src/corevx_defs.sv"

initial begin
	#10000
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


corevx_cache cache(
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


wire k = pma_error[m_address >> 2];
wire [31:0] m = m_address >> 2;

always @(posedge clk) begin
	if(m_read && m_waitrequest) begin
		m_readdata <= mem[m_address >> 2];
		m_readdatavalid <= 1;
		m_waitrequest <= 0;
		if(pma_error[m_address >> 2] === 1) begin
			m_response <= 2'b11;
		end else begin
			m_response <= 2'b00;
		end
	end else if(m_write && m_waitrequest) begin
		if(pma_error[m_address >> 2] === 1) begin
			m_response <= 2'b11;
		end else begin
			m_response <= 2'b00;
		end
		if(m_byteenable[3])
			mem[m_address >> 2][31:24] <= m_writedata[31:24];
		if(m_byteenable[2])
			mem[m_address >> 2][23:16] <= m_writedata[23:16];
		if(m_byteenable[1])
			mem[m_address >> 2][15:8] <= m_writedata[15:8];
		if(m_byteenable[0])
			mem[m_address >> 2][7:0] <= m_writedata[7:0];
			
		m_waitrequest <= 0;
		m_readdatavalid <= 0;
	end else begin
		m_waitrequest <= 1;
		m_readdatavalid <= 0;
		m_response <= 2'b11;
	end
end


localparam ISSUE_PHYS_LOAD = 1;
localparam ISSUE_PHYS_STORE = 2;
localparam ISSUE_FLUSH = 3;
reg [31:0] state = 0;

always @* begin
	
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

	case(state)
		0: begin

		end
		ISSUE_PHYS_LOAD: begin
			c_load = !c_done;
			//     VTAG/PTAG, LANE, OFFSET, INWORD_OFSET
			c_address = {20'h0, 6'h0, 4'h0, 2'h0};
		end
		ISSUE_PHYS_STORE: begin
			c_store = !c_done;
			c_address = {20'h0, 6'h1, 4'h0, 2'h0};
			c_store_data <= 32'hABCD1234;
		end
		ISSUE_FLUSH: begin
			c_flush = !c_wait;
		end
		default: begin
			c_load = 0;
			c_store = 0;
			c_flush = 0;
		end
	endcase
end

initial begin
	mem[0] = 32'hBEAFDEAD;
end

always @(posedge clk) begin
	if(!rst_n)
		state <= 0;
	else begin
		case(state)
			0: begin
				state <= ISSUE_PHYS_LOAD;
			end
			ISSUE_PHYS_LOAD: begin
				if(c_done && !c_wait) begin
					state <= ISSUE_PHYS_STORE;
					`assert(c_load_data, 32'hBEAFDEAD);
					`assert(c_load_missaligned, 0);
					`assert(c_load_unknowntype, 0);
					`assert(c_store_missaligned, 0);
					`assert(c_store_unknowntype, 0);
				end
			end
			ISSUE_PHYS_STORE: begin
				if(c_done && !c_wait) begin
					state <= state + 1;
				end
			end
			ISSUE_FLUSH: begin
				if(c_flush_done)
					state <= state + 1;
			end
			4: begin
				state <= state + 1;
			end
			5: begin
				$finish;
			end
		endcase
	end
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