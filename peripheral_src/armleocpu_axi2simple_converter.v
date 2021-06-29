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
// Filename:    armleocpu_axi2simple.v
// Project:	ArmleoCPU
//
// Purpose:	Converts AXI4 to simple interface.
// Deps: armleocpu_defines.vh/undef.vh for AXI4 declarations
// Notes:
//      This implementation does not fully implement AXI4, but fully implements AXI4Lite.
//      It is assumed that this controller is behind a router that converts address to zero base address
// Parameters:
//          
//          
// Description:
//       It can be configured for 64 bit or 32 bit data bus.
////////////////////////////////////////////////////////////////////////////////

module armleocpu_axi2simple_converter #(
    parameter ADDR_WIDTH = 34, // can be anything reasonable
    parameter ID_WIDTH = 4, // can be anything reasonable
    parameter DATA_WIDTH = 32, // 32 or 64
    localparam DATA_STROBES = DATA_WIDTH / 8, // fixed
    localparam SIZE_WIDTH = 3, // fixed
) (
    input               clk,
    input               rst_n,

    input wire          axi_awvalid,
    output reg          axi_awready,
    input wire  [ID_WIDTH-1:0]
                        axi_awid,
    input wire  [ADDR_WIDTH-1:0]
                        axi_awaddr,
    input wire  [7:0]   axi_awlen,
    input wire  [SIZE_WIDTH-1:0]
                        axi_awsize,
    input wire  [1:0]   axi_awburst,
    

    // AXI W Bus
    input wire          axi_wvalid,
    output reg          axi_wready,
    input wire  [DATA_WIDTH-1:0]
                        axi_wdata,
    input wire  [DATA_STROBES-1:0]
                        axi_wstrb,
    input wire          axi_wlast,
    
    // AXI B Bus
    output reg          axi_bvalid,
    input wire          axi_bready,
    output reg [1:0]    axi_bresp,
    output reg [ID_WIDTH-1:0]
                        axi_bid,
    
    
    input wire          axi_arvalid,
    output reg          axi_arready,
    input wire  [ID_WIDTH-1:0]
                        axi_arid,
    input wire  [ADDR_WIDTH-1:0]
                        axi_araddr,
    input wire  [7:0]   axi_arlen,
    input wire  [SIZE_WIDTH-1:0]
                        axi_arsize,
    input wire  [1:0]   axi_arburst,
    
    

    output reg          axi_rvalid,
    input wire          axi_rready,
    output reg  [1:0]   axi_rresp,
    output reg          axi_rlast,
    output reg  [DATA_WIDTH-1:0]
                        axi_rdata,
    output reg [ID_WIDTH-1:0]
                        axi_rid,

    input               address_error, // AXI4 Response = 11
    input               write_error, // AXI4 Response = 10

    output reg [31:0]	address; // address

    output reg		    write;
    output [31:0]	write_data;
    output [3:0]    write_byteenable;
    

    output reg		read; // used to retire read from register
    input  [31:0]	read_data; // should not care about read request, always contains data accrding to read_address or address_error is asserted
);




endmodule

`include "armleocpu_undef.vh"