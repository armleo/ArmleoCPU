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
////////////////////////////////////////////////////////////////////////////////


`define TIMEOUT 1000000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"


// TODO: Test vector definitions and multi configuration vector makefile

// Note: Test should not rely on this, but on localparams instead
`ifndef TEST_VECTOR_PASSTHROUGH
	`define TEST_VECTOR_PASSTHROUGH 0
`endif


localparam PASSTHROUGH = `TEST_VECTOR_PASSTHROUGH;

localparam ADDR_WIDTH = 40;
localparam DATA_WIDTH = 24;
localparam DATA_STROBES = DATA_WIDTH/8;
localparam ID_WIDTH = 6;

`AXI_FULL_SIGNALS(upstream_axi_, ADDR_WIDTH, DATA_WIDTH, ID_WIDTH)
`AXI_FULL_SIGNALS(downstream_axi_, ADDR_WIDTH, DATA_WIDTH, ID_WIDTH)

armleocpu_axi_register_slice #(
	.ADDR_WIDTH(ADDR_WIDTH),
	.DATA_WIDTH(DATA_WIDTH),
	.ID_WIDTH(ID_WIDTH),
	.PASSTHROUGH(PASSTHROUGH)
) slice (
	.*
);

`define ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(signal) assert(upstream_axi_``signal`` === downstream_axi_``signal``);
generate if(PASSTHROUGH) begin : PASSTHROUGH_ASSERTS
	always @(posedge clk) begin
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(awvalid);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(awready);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(awaddr);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(awlen);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(awsize);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(awprot);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(awburst);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(awid);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(awlock);

		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(wvalid);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(wready);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(wdata);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(wstrb);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(wlast);

		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(bvalid);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(bready);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(bresp);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(bid);


		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(arvalid);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(arready);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(araddr);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(arlen);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(arsize);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(arprot);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(arburst);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(arid);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(arlock);

		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(rvalid);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(rready);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(rresp);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(rid);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(rlast);
		`ASSERT_EQUAL_UPSTREAM_DOWNSTREAM(rdata);
	end
end endgenerate

`define ASSERT_EQUAL_FOR_NON_PASSTHROUGH(a, b) if(`TEST_VECTOR_PASSTHROUGH==0) begin `assert_equal(a, b) end


initial begin
	integer i;
	@(posedge rst_n)

	@(negedge clk)
	upstream_axi_awvalid = 1;
	upstream_axi_awaddr = 100;
	#1;

	`ASSERT_EQUAL_FOR_NON_PASSTHROUGH(upstream_axi_awready, 1)
	`ASSERT_EQUAL_FOR_NON_PASSTHROUGH(downstream_axi_awvalid, 0)

	@(negedge clk)
	upstream_axi_awvalid = 0;
	upstream_axi_awaddr = 101;

	#1

	`ASSERT_EQUAL_FOR_NON_PASSTHROUGH(downstream_axi_awvalid, 1)
	`ASSERT_EQUAL_FOR_NON_PASSTHROUGH(downstream_axi_awaddr, 100)


	$finish;
end


endmodule