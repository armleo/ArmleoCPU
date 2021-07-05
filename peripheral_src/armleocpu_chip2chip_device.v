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


`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_chip2chip_device ();

    parameter [0:0] OPT_HOST = 1;

    parameter BUFFERS = 4;
    parameter UPSTREAM_ID_WIDTH = 4;
    parameter DOWNSTREAM_ID_WIDTH = 8;

    localparam ADDR_WIDTH = 32;
    localparam DATA_WIDTH = 32;
    localparam DATA_STROBES = DATA_WIDTH / 8;
    localparam SIZE_WIDTH = 3;

    input                   clk,
    input                   rst_n,

    // All inputs/outputs are registered
    // client port, connects to CPU or other host

    output reg          io_upstream_irq,

    input wire          io_upstream_axi_awvalid,
    output wire         io_upstream_axi_awready,
    input wire  [ADDR_WIDTH-1:0]
                        io_upstream_axi_awaddr,
    input wire  [7:0]   io_upstream_axi_awlen,
    input wire  [SIZE_WIDTH-1:0]
                        io_upstream_axi_awsize,
    input wire  [1:0]   io_upstream_axi_awburst,
    input wire          io_upstream_axi_awlock,
    input wire  [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_awid,
    input wire  [2:0]   io_upstream_axi_awprot,

    // AXI W Bus
    input wire          io_upstream_axi_wvalid,
    output wire         io_upstream_axi_wready,
    input wire  [DATA_WIDTH-1:0]
                        io_upstream_axi_wdata,
    input wire  [DATA_STROBES-1:0]
                        io_upstream_axi_wstrb,
    input wire          io_upstream_axi_wlast,
    
    // AXI B Bus
    output wire         io_upstream_axi_bvalid,
    input wire          io_upstream_axi_bready,
    output wire [1:0]   io_upstream_axi_bresp,
    output wire [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_bid,
    
    
    input wire          io_upstream_axi_arvalid,
    output reg          io_upstream_axi_arready,
    input wire  [ADDR_WIDTH-1:0]
                        io_upstream_axi_araddr,
    input wire  [7:0]   io_upstream_axi_arlen,
    input wire  [SIZE_WIDTH-1:0]
                        io_upstream_axi_arsize,
    input wire  [1:0]   io_upstream_axi_arburst,
    input wire  [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_arid,
    input wire          io_upstream_axi_arlock,
    input wire  [2:0]   io_upstream_axi_arprot,
    

    output reg          io_upstream_axi_rvalid,
    input  wire         io_upstream_axi_rready,
    output reg  [1:0]   io_upstream_axi_rresp,
    output reg          io_upstream_axi_rlast,
    output reg  [DATA_WIDTH-1:0]
                        io_upstream_axi_rdata,
    output reg  [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_rid,

    
    // Host port, connects to peripheral
    // All signals are registered


    // AXI AW Bus
    output wire  [1-1:0]
                        io_downstream_axi_awvalid,
    input  wire [1-1:0]
                        io_downstream_axi_awready,
    output wire [ADDR_WIDTH-1:0]
                        io_downstream_axi_awaddr,
    output wire [8-1:0]
                        io_downstream_axi_awlen,
    output wire [SIZE_WIDTH-1:0]
                        io_downstream_axi_awsize,
    output wire [2-1:0]
                        io_downstream_axi_awburst,
    output wire [0:0]   io_downstream_axi_awlock,
    output wire [3-1:0]
                        io_downstream_axi_awprot,
    output wire [DOWNSTREAM_ID_WIDTH-1:0]
                        io_downstream_axi_awid,

    // AXI W Bus
    output reg [1-1:0] 
                        io_downstream_axi_wvalid,
    input  wire [1-1:0] 
                        io_downstream_axi_wready,
    output wire [DATA_WIDTH-1:0]
                        io_downstream_axi_wdata,
    output wire [DATA_STROBES-1:0]
                        io_downstream_axi_wstrb,
    output wire [0:0]
                        io_downstream_axi_wlast,
    
    // AXI B Bus
    input  wire [1-1:0]
                        io_downstream_axi_bvalid,
    output reg [1-1:0]
                        io_downstream_axi_bready,
    input  wire [2-1:0]
                        io_downstream_axi_bresp,
    input wire [DOWNSTREAM_ID_WIDTH-1:0]
                        io_downstream_axi_bid,


    output reg  [1-1:0]
                        io_downstream_axi_arvalid,
    input  wire [1-1:0]
                        io_downstream_axi_arready,
    output reg  [ADDR_WIDTH-1:0]
                        io_downstream_axi_araddr,
    output wire [8-1:0]
                        io_downstream_axi_arlen,
    output wire [SIZE_WIDTH-1:0]
                        io_downstream_axi_arsize,
    output wire [2-1:0]
                        io_downstream_axi_arburst,
    output wire [0:0]
                        io_downstream_axi_arlock,
    output wire [3-1:0]
                        io_downstream_axi_arprot,
    output wire [DOWNSTREAM_ID_WIDTH-1:0]
                        io_downstream_axi_arid,
    

    input wire [1-1:0]
                        io_downstream_axi_rvalid,
    output reg [1-1:0]
                        io_downstream_axi_rready,
    input wire [2-1:0]
                        io_downstream_axi_rresp,
    input wire [1-1:0]
                        io_downstream_axi_rlast,
    input wire [DATA_WIDTH-1:0]
                        io_downstream_axi_rdata,
    input wire [DOWNSTREAM_ID_WIDTH-1:0]
                        io_downstream_axi_rid,
    input wire io_downstream_irq,


    // CHIP2CHIP interface
    // The input/output clk is "clk".
    output reg [15:0] io_outdata,
    output reg io_oe,
    input wire [15:0] io_indata,

    input wire io_accept,
    output reg io_request_o
);