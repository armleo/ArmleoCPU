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
`define TEST_VECTOR_PASSTHROUGH 0
`define TEST_VECTOR_ADDR_WIDTH 40
`define TEST_VECTOR_DATA_WIDTH 24
`define TEST_VECTOR_ID_WIDTH 6

localparam PASSTHROUGH = `TEST_VECTOR_PASSTHROUGH;

localparam ADDR_WIDTH = `TEST_VECTOR_ADDR_WIDTH;
localparam DATA_WIDTH = `TEST_VECTOR_DATA_WIDTH;
localparam DATA_STROBES = DATA_WIDTH/8;
localparam ID_WIDTH = `TEST_VECTOR_ID_WIDTH;

reg upstream_axi_awvalid;
wire upstream_axi_awready;
reg [ADDR_WIDTH-1:0] upstream_axi_awaddr;
reg [7:0] upstream_axi_awlen;
reg [2:0] upstream_axi_awsize;
reg [2:0] upstream_axi_awprot;
reg [1:0] upstream_axi_awburst;
reg [ID_WIDTH-1:0] upstream_axi_awid;
reg upstream_axi_awlock;

reg upstream_axi_wvalid;
wire upstream_axi_wready;
reg [DATA_WIDTH-1:0] upstream_axi_wdata;
reg [DATA_STROBES-1:0] upstream_axi_wstrb;
reg upstream_axi_wlast;

wire upstream_axi_bvalid;
reg upstream_axi_bready;
wire [1:0] upstream_axi_bresp;
wire [ID_WIDTH-1:0] upstream_axi_bid;


reg upstream_axi_arvalid;
wire upstream_axi_arready;
reg [ADDR_WIDTH-1:0] upstream_axi_araddr;
reg [7:0] upstream_axi_arlen;
reg [2:0] upstream_axi_arsize;
reg [2:0] upstream_axi_arprot;
reg [1:0] upstream_axi_arburst;
reg [ID_WIDTH-1:0] upstream_axi_arid;
reg upstream_axi_arlock;

wire upstream_axi_rvalid;
reg upstream_axi_rready;
wire [1:0] upstream_axi_rresp;
wire [DATA_WIDTH-1:0] upstream_axi_rdata;
wire [ID_WIDTH-1:0] upstream_axi_rid;
wire upstream_axi_rlast;






wire downstream_axi_awvalid;
reg downstream_axi_awready;
wire [ADDR_WIDTH-1:0] downstream_axi_awaddr;
wire [7:0] downstream_axi_awlen;
wire [2:0] downstream_axi_awsize;
wire [2:0] downstream_axi_awprot;
wire [1:0] downstream_axi_awburst;
wire [ID_WIDTH-1:0] downstream_axi_awid;
wire downstream_axi_awlock;

wire downstream_axi_wvalid;
reg downstream_axi_wready;
wire [DATA_WIDTH-1:0] downstream_axi_wdata;
wire [DATA_STROBES-1:0] downstream_axi_wstrb;
wire downstream_axi_wlast;

reg downstream_axi_bvalid;
wire downstream_axi_bready;
reg [1:0] downstream_axi_bresp;
reg [ID_WIDTH-1:0] downstream_axi_bid;


wire downstream_axi_arvalid;
reg downstream_axi_arready;
wire [ADDR_WIDTH-1:0] downstream_axi_araddr;
wire [7:0] downstream_axi_arlen;
wire [2:0] downstream_axi_arsize;
wire [2:0] downstream_axi_arprot;
wire [1:0] downstream_axi_arburst;
wire [ID_WIDTH-1:0] downstream_axi_arid;
wire downstream_axi_arlock;

reg downstream_axi_rvalid;
wire downstream_axi_rready;
reg [1:0] downstream_axi_rresp;
reg [DATA_WIDTH-1:0] downstream_axi_rdata;
reg [ID_WIDTH-1:0] downstream_axi_rid;
reg downstream_axi_rlast;

armleocpu_axi_register_slice #(
	.ADDR_WIDTH(ADDR_WIDTH),
	.DATA_WIDTH(DATA_WIDTH),
	.ID_WIDTH(ID_WIDTH),
	.PASSTHROUGH(0)
) slice (
	.*
);

initial begin
	integer i;
	@(posedge rst_n)

	@(negedge clk)
	
	$finish;
end


endmodule