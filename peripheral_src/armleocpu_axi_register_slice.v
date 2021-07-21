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
// Project:	ArmleoCPU
//
// Purpose:	AXI4 Register slice
////////////////////////////////////////////////////////////////////////////////


`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE


module armleocpu_axi_register_slice (
    clk, rst_n,
    
    upstream_axi_awvalid, upstream_axi_awready, upstream_axi_awaddr, upstream_axi_awlen, upstream_axi_awburst, upstream_axi_awsize, upstream_axi_awid, upstream_axi_awlock, upstream_axi_awprot,
    upstream_axi_wvalid, upstream_axi_wready, upstream_axi_wdata, upstream_axi_wstrb, upstream_axi_wlast,
    upstream_axi_bvalid, upstream_axi_bready, upstream_axi_bresp, upstream_axi_bid,

    upstream_axi_arvalid, upstream_axi_arready, upstream_axi_araddr, upstream_axi_arlen, upstream_axi_arsize, upstream_axi_arburst, upstream_axi_arid, upstream_axi_arlock, upstream_axi_arprot,
    upstream_axi_rvalid, upstream_axi_rready, upstream_axi_rresp, upstream_axi_rlast, upstream_axi_rdata, upstream_axi_rid,
    

    downstream_axi_awvalid, downstream_axi_awready, downstream_axi_awaddr, downstream_axi_awlen, downstream_axi_awburst, downstream_axi_awsize, downstream_axi_awid, downstream_axi_awlock, downstream_axi_awprot,
    downstream_axi_wvalid, downstream_axi_wready, downstream_axi_wdata, downstream_axi_wstrb, downstream_axi_wlast,
    downstream_axi_bvalid, downstream_axi_bready, downstream_axi_bresp, downstream_axi_bid,

    downstream_axi_arvalid, downstream_axi_arready, downstream_axi_araddr, downstream_axi_arlen, downstream_axi_arsize, downstream_axi_arburst, downstream_axi_arid, downstream_axi_arlock, downstream_axi_arprot,
    downstream_axi_rvalid, downstream_axi_rready, downstream_axi_rresp, downstream_axi_rlast, downstream_axi_rdata, downstream_axi_rid
);

    parameter ADDR_WIDTH = 32;
    parameter DATA_WIDTH = 32;
    parameter ID_WIDTH = 4;

    parameter PASSTHROUGH = 0;

    localparam DATA_STROBES = DATA_WIDTH/8;
    localparam SIZE_WIDTH = 3;

    input clk;
    input rst_n;


    // AXI AW Bus
    input logic          upstream_axi_awvalid;
    output logic         upstream_axi_awready;
    input logic  [ADDR_WIDTH-1:0]
                        upstream_axi_awaddr;
    input logic  [7:0]   upstream_axi_awlen;
    input logic  [SIZE_WIDTH-1:0]
                        upstream_axi_awsize;
    input logic  [1:0]   upstream_axi_awburst;
    input logic          upstream_axi_awlock;
    input logic  [ID_WIDTH-1:0]
                        upstream_axi_awid;
    input logic  [2:0]   upstream_axi_awprot;

    // AXI W Bus
    input logic          upstream_axi_wvalid;
    output logic         upstream_axi_wready;
    input logic  [DATA_WIDTH-1:0]
                        upstream_axi_wdata;
    input logic  [DATA_STROBES-1:0]
                        upstream_axi_wstrb;
    input logic          upstream_axi_wlast;
    
    // AXI B Bus
    output logic         upstream_axi_bvalid;
    input logic          upstream_axi_bready;
    output logic[1:0]    upstream_axi_bresp;
    output logic[ID_WIDTH-1:0]
                        upstream_axi_bid;
    
    
    input logic          upstream_axi_arvalid;
    output logic         upstream_axi_arready;
    input logic  [ADDR_WIDTH-1:0]
                        upstream_axi_araddr;
    input logic  [7:0]   upstream_axi_arlen;
    input logic  [SIZE_WIDTH-1:0]
                        upstream_axi_arsize;
    input logic  [1:0]   upstream_axi_arburst;
    input logic  [ID_WIDTH-1:0]
                        upstream_axi_arid;
    input logic          upstream_axi_arlock;
    input logic  [2:0]   upstream_axi_arprot;
    

    output logic         upstream_axi_rvalid;
    input  logic         upstream_axi_rready;
    output logic [1:0]   upstream_axi_rresp;
    output logic         upstream_axi_rlast;
    output logic [DATA_WIDTH-1:0]
                        upstream_axi_rdata;
    output logic [ID_WIDTH-1:0]
                        upstream_axi_rid;



    // DOWNSTREAM
    
    // AXI AW Bus
    output logic         downstream_axi_awvalid;
    input logic         downstream_axi_awready;
    output logic [ADDR_WIDTH-1:0]
                        downstream_axi_awaddr;
    output logic [7:0]   downstream_axi_awlen;
    output logic [SIZE_WIDTH-1:0]
                        downstream_axi_awsize;
    output logic [1:0]   downstream_axi_awburst;
    output logic         downstream_axi_awlock;
    output logic [ID_WIDTH-1:0]
                        downstream_axi_awid;
    output logic [2:0]   downstream_axi_awprot;

    // AXI W Bus
    output logic         downstream_axi_wvalid;
    input logic         downstream_axi_wready;
    output logic [DATA_WIDTH-1:0]
                        downstream_axi_wdata;
    output logic [DATA_STROBES-1:0]
                        downstream_axi_wstrb;
    output logic         downstream_axi_wlast;
    
    // AXI B Bus
    input logic         downstream_axi_bvalid;
    output logic         downstream_axi_bready;
    input logic [1:0]    downstream_axi_bresp;
    input logic [ID_WIDTH-1:0]
                        downstream_axi_bid;
    
    
    output logic         downstream_axi_arvalid;
    input logic         downstream_axi_arready;
    output logic [ADDR_WIDTH-1:0]
                        downstream_axi_araddr;
    output logic [7:0]   downstream_axi_arlen;
    output logic [SIZE_WIDTH-1:0]
                        downstream_axi_arsize;
    output logic [1:0]   downstream_axi_arburst;
    output logic [ID_WIDTH-1:0]
                        downstream_axi_arid;
    output logic         downstream_axi_arlock;
    output logic [2:0]   downstream_axi_arprot;
    

    input logic         downstream_axi_rvalid;
    output logic         downstream_axi_rready;
    input logic [1:0]   downstream_axi_rresp;
    input logic         downstream_axi_rlast;
    input logic [DATA_WIDTH-1:0]
                        downstream_axi_rdata;
    input logic [ID_WIDTH-1:0]
                        downstream_axi_rid;



// AW Bus

localparam AW_AR_DW = ADDR_WIDTH
        + 8 // len
        + SIZE_WIDTH
        + 2 // burst
        + 1 // lock
        + ID_WIDTH
        + 3; // prot

`define ADDR_SIGNALS(prefix) { \
                ``prefix``addr, \
                ``prefix``len, \
                ``prefix``size, \
                ``prefix``burst, \
                ``prefix``lock, \
                ``prefix``id, \
                ``prefix``prot \
}

armleocpu_register_slice #(
    .DW(AW_AR_DW),
    .PASSTHROUGH(PASSTHROUGH)
) U_aw (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (upstream_axi_awvalid),
    .in_ready   (upstream_axi_awready),
    .in_data(`ADDR_SIGNALS(upstream_axi_aw)),

    .out_valid(downstream_axi_awvalid),
    .out_ready(downstream_axi_awready),
    .out_data(`ADDR_SIGNALS(downstream_axi_aw))
);

// W Bus
armleocpu_register_slice #(
    .DW(DATA_WIDTH + DATA_STROBES + 1),
    .PASSTHROUGH(PASSTHROUGH)
) U_w(
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (upstream_axi_wvalid),
    .in_ready   (upstream_axi_wready),
    .in_data    ({
        upstream_axi_wdata,
        upstream_axi_wstrb,
        upstream_axi_wlast
    }),

    .out_valid  (downstream_axi_wvalid),
    .out_ready  (downstream_axi_wready),
    .out_data   ({
        downstream_axi_wdata,
        downstream_axi_wstrb,
        downstream_axi_wlast
    })
);

// TODO: Passthrough logic
// B Bus
armleocpu_register_slice #(
    .DW(ID_WIDTH + 2),
    .PASSTHROUGH(PASSTHROUGH)
) U_b(
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (downstream_axi_bvalid),
    .in_ready   (downstream_axi_bready),
    .in_data    ({
        downstream_axi_bid,
        downstream_axi_bresp
    }),

    .out_valid  (upstream_axi_bvalid),
    .out_ready  (upstream_axi_bready),
    .out_data   ({
        upstream_axi_bid,
        upstream_axi_bresp
    })
);

// AR Bus

armleocpu_register_slice #(
    .DW(AW_AR_DW),
    .PASSTHROUGH(PASSTHROUGH)
) U_ar (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (upstream_axi_arvalid),
    .in_ready   (upstream_axi_arready),
    .in_data(`ADDR_SIGNALS(upstream_axi_ar)),

    .out_valid(downstream_axi_arvalid),
    .out_ready(downstream_axi_arready),
    .out_data(`ADDR_SIGNALS(downstream_axi_ar))
);


// TODO: Passthrough logic
// R Bus



armleocpu_register_slice #(
    .DW(ID_WIDTH + 2 + 1 + DATA_WIDTH),
    .PASSTHROUGH(PASSTHROUGH)
) U_r (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (downstream_axi_rvalid),
    .in_ready   (downstream_axi_rready),
    .in_data    ({
        downstream_axi_rid,
        downstream_axi_rresp,
        downstream_axi_rdata,
        downstream_axi_rlast
    }),

    .out_valid  (upstream_axi_rvalid),
    .out_ready  (upstream_axi_rready),
    .out_data   ({
        upstream_axi_rid,
        upstream_axi_rresp,
        upstream_axi_rdata,
        upstream_axi_rlast
    })
);


`undef ADDR_SIGNALS

endmodule


`include "armleocpu_undef.vh"

