module armleocpu_cache(
	input clk,
	input rst,
	input  [31:0]		c_address,
	output reg			c_wait,
	output reg			c_pagefault,
    output reg          c_accessfault,
    output reg			c_done,
    `ifdef DEBUG
    output reg			c_miss,
    `endif
    
	input				c_read,
	output	[31:0]		c_readdata,
	output	[7:0]		c_accesstag,

	input	[2:0]		c_width,
	input				c_write,
	input	[31:0]		c_writedata,
	
	input				c_flush,
	output reg			c_flushing,
	output reg			c_flush_done,
	// CACHE <-> CSR
	input				csr_satp_mode, // Mode = 0 -> physical access, 1 -> ppn valid
	input [21:0] 		csr_satp_ppn,
	
	// CACHE <-> MEMORY
	output	reg [33:0]		m_address,
	output	reg [3:0]		m_burstcount,
	input					m_waitrequest,
	
	output	reg				m_read,
	input	[31:0]			m_readdata,
	input					m_readdatavalid,
	
	output	reg				m_write,
	output	reg [31:0]		m_writedata,
    output  reg [3:0]       m_byteenable
	
	`ifdef DEBUG
	, output trace_error

	`endif
);
`include "armleocpu_defs.sv"

endmodule