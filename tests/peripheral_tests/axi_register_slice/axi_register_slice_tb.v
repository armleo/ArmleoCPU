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



localparam ADDR_WIDTH = 32;
localparam DATA_WIDTH = 32;
localparam DATA_STROBES = DATA_WIDTH/8;
localparam ID_WIDTH = 4;

reg axi_awvalid;
wire axi_awready;
reg [ADDR_WIDTH-1:0] axi_awaddr;
reg [7:0] axi_awlen;
reg [2:0] axi_awsize;
reg [1:0] axi_awburst;
reg [ID_WIDTH-1:0] axi_awid;
reg axi_awlock;

reg axi_wvalid;
wire axi_wready;
reg [DATA_WIDTH-1:0] axi_wdata;
reg [DATA_STROBES-1:0] axi_wstrb;
reg axi_wlast;

wire axi_bvalid;
reg axi_bready;
wire [1:0] axi_bresp;
wire [ID_WIDTH-1:0] axi_bid;


reg axi_arvalid;
wire axi_arready;
reg [ADDR_WIDTH-1:0] axi_araddr;
reg [7:0] axi_arlen;
reg [2:0] axi_arsize;
reg [1:0] axi_arburst;
reg [ID_WIDTH-1:0] axi_arid;
reg axi_arlock;

wire axi_rvalid;
reg axi_rready;
wire [1:0] axi_rresp;
wire [DATA_WIDTH-1:0] axi_rdata;
wire [ID_WIDTH-1:0] axi_rid;
wire axi_rlast;


initial begin
	integer i;
	@(posedge rst_n)

	@(negedge clk)
	
	$finish;
end


endmodule