////////////////////////////////////////////////////////////////////////////////
// 
// This file is part of ArmleoCPU.
// ArmleoCPU is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// ArmleoCPU is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with ArmleoCPU.  If not, see <https://www.gnu.org/licenses/>.
// 
// Copyright (C) 2016-2021, Arman Avetisyan, see COPYING file or LICENSE file
// SPDX-License-Identifier: GPL-3.0-or-later
// 

`define TIMEOUT 1000000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"



localparam ADDR_WIDTH = 32;
localparam DATA_WIDTH = 32;
localparam DATA_STROBES = DATA_WIDTH/8;
localparam DEPTH = 10;

reg axi_awvalid;
wire axi_awready;
reg [ADDR_WIDTH-1:0] axi_awaddr;
reg [7:0] axi_awlen;
reg [2:0] axi_awsize;
reg [1:0] axi_awburst;
reg [3:0] axi_awid;

reg axi_wvalid;
wire axi_wready;
reg [DATA_WIDTH-1:0] axi_wdata;
reg [DATA_STROBES-1:0] axi_wstrb;
reg axi_wlast;

wire axi_bvalid;
reg axi_bready;
wire [1:0] axi_bresp;
wire [3:0] axi_bid;


reg axi_arvalid;
wire axi_arready;
reg [ADDR_WIDTH-1:0] axi_araddr;
reg [7:0] axi_arlen;
reg [2:0] axi_arsize;
reg [1:0] axi_arburst;
reg [3:0] axi_arid;

wire axi_rvalid;
reg axi_rready;
wire [1:0] axi_rresp;
wire [DATA_WIDTH-1:0] axi_rdata;
wire [3:0] axi_rid;
wire axi_rlast;


reg [31:0] mem [9:0];



armleocpu_axi_bram #(DEPTH) bram (
	.*
);

	// Test cases:
	// 	AW start w/ AR
	// 	AW data w/ AR
	// 	AW response w/ AR

	// 	AR start w/ len = 0
	// 	AR result

	// 	AR start w/ len = 1, WRAP
	// 		AR result
	// 		AR result, last

	// 	AR start w/ len = 1, INCR
	// 		AR result
	// 		AR result, last
		
	// 	Generate random actions and
	// 	make sure that it's consistent with current memory state

//-------------AW---------------
task aw_noop; begin
	axi_awvalid = 0;
end endtask

task aw_op;
input [ADDR_WIDTH-1:0] addr;
input [2:0] id;
begin
	axi_awvalid = 1;
	axi_awaddr = addr;
	axi_awlen = 0;
	axi_awsize = 2; // 4 bytes
	axi_awburst = 2'b01; // Increment
	axi_awid = id;
end endtask

task aw_expect;
input awready;
begin
	`assert_equal(axi_awready, awready);
end endtask

//-------------W---------------
task w_noop; begin
	axi_wvalid = 0;
end endtask

task w_op;
input [DATA_WIDTH-1:0] wdata;
input [DATA_STROBES-1:0] wstrb;
begin
	axi_wvalid = 1;
	axi_wdata = wdata;
	axi_wstrb = wstrb;
	axi_wlast = 1;
end endtask

task w_expect;
input wready;
begin
	`assert_equal(axi_wready, wready)
end endtask

//-------------B---------------
task b_noop; begin
	axi_bready = 0;
end endtask

task b_expect;
input valid;
input [1:0] resp;
input [3:0] id;
begin
	`assert_equal(axi_bvalid, valid)
	if(valid) begin
		`assert_equal(axi_bresp, resp)
	end
end endtask

//-------------AR---------------
task ar_noop; begin
	axi_arvalid = 0;
end endtask

task ar_op; 
input [ADDR_WIDTH-1:0] addr;
input [3:0] id;
input [1:0] burst;
input [7:0] len;
begin
	axi_arvalid = 1;
	axi_araddr = addr;
	axi_arlen = len;
	axi_arsize = 2; // 4 bytes
	axi_arburst = burst; // Increment
	axi_arid = id;
end endtask

task ar_expect;
input ready;
begin
	`assert_equal(axi_arready, ready)
end endtask

//-------------R---------------
task r_noop; begin
	axi_rready = 0;
end endtask

task r_expect;
input valid;
input [1:0] resp;
input [31:0] data;
input [3:0] id;
input last;
begin
	`assert_equal(axi_rvalid, valid)
	if(valid) begin
		`assert_equal(axi_rresp, resp)
		if(resp <= 2'b01)
			`assert_equal(axi_rdata, data)
		`assert_equal(axi_rid, id)
		`assert_equal(axi_rlast, last)
	end
end endtask


//-------------Others---------------
task poke_all;
input aw;
input w;
input b;

input ar;
input r; begin
	if(aw === 1)
		aw_noop();
	if(w === 1)
		w_noop();
	if(b === 1)
		b_noop();
	if(ar === 1)
		ar_noop();
	if(r === 1)
		r_noop();
end endtask

task expect_all;
input aw;
input w;
input b;

input ar;
input r; begin
	if(aw === 1)
		aw_expect(0);
	if(w === 1)
		w_expect(0);
	if(b === 1)
		b_expect(0, 2'bZZ, 4'bZZZZ);
	if(ar === 1)
		ar_expect(0);
	if(r === 1)
		r_expect(0, 2'bZZ, 32'hZZZZ_ZZZZ, 2'bZZ, 1'bZ);
end endtask

integer k;

task write;
input [ADDR_WIDTH-1:0] addr;
input [3:0] id;
input [1:0] resp;
input [DATA_WIDTH-1:0] wdata;
input [DATA_STROBES-1:0] wstrb;
begin
	
	// AW request
	@(negedge clk)
	poke_all(1,1,1, 1,1);
	aw_op(addr, id); // Access word = 9, last word in storage
	@(posedge clk)
	aw_expect(1);
	expect_all(0, 1, 1, 1, 1);

	// W request stalled
	@(negedge clk);
	aw_noop();
	@(posedge clk);
	expect_all(0, 0, 1, 1, 1);

	// W request
	@(negedge clk);
	w_op(wdata, wstrb);
	@(posedge clk)
	w_expect(1);
	expect_all(1, 0, 1, 1, 1);

	// B stalled
	@(negedge clk);
	axi_bready = 0;
	@(posedge clk);
	b_expect(1, resp, id);
	expect_all(1, 1, 0, 1, 1);

	// B done
	@(negedge clk);
	axi_bready = 1;
	w_noop();
	@(posedge clk);
	b_expect(1, resp, id);
	expect_all(1, 1, 0, 1, 1);

	if(wstrb[3])
		mem[addr >> 2][31:24] = wdata[31:24];
	if(wstrb[2])
		mem[addr >> 2][23:16] = wdata[23:16];
	if(wstrb[1])
		mem[addr >> 2][15:8] = wdata[15:8];
	if(wstrb[0])
		mem[addr >> 2][7:0] = wdata[7:0];
	
	@(negedge clk);
	poke_all(1,1,1, 1,1);
end
endtask



reg [ADDR_WIDTH-1:0] mask;


reg [ADDR_WIDTH-1:0] addr_reg;

task read;
input [ADDR_WIDTH-1:0] addr;
input [1:0] burst;
input [7:0] len;
input [3:0] id;
begin
	integer i;

	mask = (len << 2);
	// AR request
	@(negedge clk)
	poke_all(1,1,1, 1,1);
	ar_op(addr, id, burst, len); // Access word = 9, last word in storage
	@(posedge clk)
	ar_expect(1);
	expect_all(1, 1, 1, 0, 1);

	addr_reg = addr;

	for(i = 0; i < len+1; i = i + 1) begin
		// R response stalled
		@(negedge clk);
		axi_rready = 0;
		ar_noop();
		@(posedge clk);
		r_expect(1,
			(addr_reg < (DEPTH << 2)) ? 2'b00 : 2'b11,
			mem[addr_reg >> 2],
			id,
			i == len);
		expect_all(1, 1, 1, 1, 0);

		// R response accepted
		@(negedge clk);
		axi_rready = 1;
		@(posedge clk)
		r_expect(1,
			(addr_reg < (DEPTH << 2)) ? 2'b00 : 2'b11,
			mem[addr_reg >> 2],
			id,
			i == len);
		expect_all(1, 1, 1, 1, 0);

		if(burst == 2'b10) // wrap
			addr_reg = (addr_reg & ~mask) | ((addr_reg + 4) & mask);
		else // incr
			addr_reg = addr_reg + 4;
	end
	@(negedge clk);
	poke_all(1,1,1, 1,1);
	$display("Read done addr = 0x%x", addr);
end
endtask

initial begin
	integer i;
	integer word;
	@(posedge rst_n)

	@(negedge clk)
	poke_all(1,1,1, 1,1);

	write(9 << 2, 4, 2'b00, 32'hFF00FF00, 4'b0111);
	write(9 << 2, 4, 2'b00, 32'hFF00FF00, 4'b1111);
	write(9 << 2, 4, 2'b00, 32'hFE00FF00, 4'b0111);
	

	read(9 << 2, 2'b01, 0, 4); //INCR test

	
	for(i = 0; i < DEPTH; i = i + 1) begin
		write(i << 2, $urandom(), 2'b00, 32'h0000_0000, 4'b1111);
	end
	$display("Full write done");
	
	for(i = 0; i < 100; i = i + 1) begin
		word = $urandom() % (DEPTH * 2);
		
		write(word << 2, //addr
			$urandom() & 4'hF, //id
			(word < DEPTH ? 2'b00 : 2'b11), // resp
			$urandom() & 32'hFFFF_FFFF, // data
			4'b1111);
	end
	$display("Test write done");
	
	$display("Data dump:");
	for(i = 0; i < DEPTH; i = i + 1) begin
		$display("mem[%d] = 0x%x or %d", i, mem[i], mem[i]);
	end


	for(i = 0; i < DEPTH; i = i + 1) begin
		read(i << 2, //addr
			($urandom() & 1) ? 2'b10 : 2'b01, // burst
			(1 << ($urandom() % 8)) - 1, // len
			$urandom() & 4'hF // id
			);
	end
	$display("Test Read done");

	$display("Random read/write test started");
	for(i = 0; i < 1000; i = i + 1) begin
		word = $urandom() % (DEPTH * 2);

		if($urandom() & 1) begin
			write(word << 2, //addr
				$urandom() & 4'hF, //id
				(word < DEPTH ? 2'b00 : 2'b11), // resp
				$urandom() & 32'hFFFF_FFFF, // data
				$urandom() & 4'b1111);
		end else begin
			read(word << 2, //addr
				($urandom() & 1) ? 2'b10 : 2'b01, // burst
				(1 << ($urandom() % 8)) - 1, // len
				$urandom() & 4'hF // id
				);
		end
	end


	@(negedge clk);
	axi_bready = 0;

	@(posedge clk);

	@(negedge clk)
	@(negedge clk)
	$finish;
end


endmodule