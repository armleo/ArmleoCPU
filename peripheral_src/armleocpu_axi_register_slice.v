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
    input wire          upstream_axi_awvalid;
    output wire         upstream_axi_awready;
    input wire  [ADDR_WIDTH-1:0]
                        upstream_axi_awaddr;
    input wire  [7:0]   upstream_axi_awlen;
    input wire  [SIZE_WIDTH-1:0]
                        upstream_axi_awsize;
    input wire  [1:0]   upstream_axi_awburst;
    input wire          upstream_axi_awlock;
    input wire  [ID_WIDTH-1:0]
                        upstream_axi_awid;
    input wire  [2:0]   upstream_axi_awprot;

    // AXI W Bus
    input wire          upstream_axi_wvalid;
    output wire         upstream_axi_wready;
    input wire  [DATA_WIDTH-1:0]
                        upstream_axi_wdata;
    input wire  [DATA_STROBES-1:0]
                        upstream_axi_wstrb;
    input wire          upstream_axi_wlast;
    
    // AXI B Bus
    output wire         upstream_axi_bvalid;
    input wire          upstream_axi_bready;
    output wire[1:0]    upstream_axi_bresp;
    output wire[ID_WIDTH-1:0]
                        upstream_axi_bid;
    
    
    input wire          upstream_axi_arvalid;
    output wire         upstream_axi_arready;
    input wire  [ADDR_WIDTH-1:0]
                        upstream_axi_araddr;
    input wire  [7:0]   upstream_axi_arlen;
    input wire  [SIZE_WIDTH-1:0]
                        upstream_axi_arsize;
    input wire  [1:0]   upstream_axi_arburst;
    input wire  [ID_WIDTH-1:0]
                        upstream_axi_arid;
    input wire          upstream_axi_arlock;
    input wire  [2:0]   upstream_axi_arprot;
    

    output wire         upstream_axi_rvalid;
    input  wire         upstream_axi_rready;
    output wire [1:0]   upstream_axi_rresp;
    output wire         upstream_axi_rlast;
    output wire [DATA_WIDTH-1:0]
                        upstream_axi_rdata;
    output wire [ID_WIDTH-1:0]
                        upstream_axi_rid;



    // DOWNSTREAM
    
    // AXI AW Bus
    output wire         downstream_axi_awvalid;
    input wire         downstream_axi_awready;
    output wire [ADDR_WIDTH-1:0]
                        downstream_axi_awaddr;
    output wire [7:0]   downstream_axi_awlen;
    output wire [SIZE_WIDTH-1:0]
                        downstream_axi_awsize;
    output wire [1:0]   downstream_axi_awburst;
    output wire         downstream_axi_awlock;
    output wire [ID_WIDTH-1:0]
                        downstream_axi_awid;
    output wire [2:0]   downstream_axi_awprot;

    // AXI W Bus
    output wire         downstream_axi_wvalid;
    input wire         downstream_axi_wready;
    output wire [DATA_WIDTH-1:0]
                        downstream_axi_wdata;
    output wire [DATA_STROBES-1:0]
                        downstream_axi_wstrb;
    output wire         downstream_axi_wlast;
    
    // AXI B Bus
    input wire         downstream_axi_bvalid;
    output wire         downstream_axi_bready;
    input wire [1:0]    downstream_axi_bresp;
    input wire [ID_WIDTH-1:0]
                        downstream_axi_bid;
    
    
    output wire         downstream_axi_arvalid;
    input wire         downstream_axi_arready;
    output wire [ADDR_WIDTH-1:0]
                        downstream_axi_araddr;
    output wire [7:0]   downstream_axi_arlen;
    output wire [SIZE_WIDTH-1:0]
                        downstream_axi_arsize;
    output wire [1:0]   downstream_axi_arburst;
    output wire [ID_WIDTH-1:0]
                        downstream_axi_arid;
    output wire         downstream_axi_arlock;
    output wire [2:0]   downstream_axi_arprot;
    

    input wire         downstream_axi_rvalid;
    output wire         downstream_axi_rready;
    input wire [1:0]   downstream_axi_rresp;
    input wire         downstream_axi_rlast;
    input wire [DATA_WIDTH-1:0]
                        downstream_axi_rdata;
    input wire [ID_WIDTH-1:0]
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

generate if(!PASSTHROUGH) begin : AW_REGISTERED
armleocpu_register_slice #(
    .DW(AW_AR_DW)
) U_aw (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (upstream_axi_awvalid),
    .in_ready   (upstream_axi_awready),
    .in_data(`ADDR_SIGNALS(upstream_axi_aw)),

    .out_valid(downstream_axi_awvalid),
    .out_ready(downstream_axi_awvalid),
    .out_data(`ADDR_SIGNALS(downstream_axi_aw))
);
end else begin : AW_PASSTHROUGH
    assign downstream_axi_awvalid = upstream_axi_awvalid;
    assign upstream_axi_awready = downstream_axi_awready;
    assign `ADDR_SIGNALS(downstream_axi_aw) = `ADDR_SIGNALS(upstream_axi_aw);
end
endgenerate

// W Bus
// TODO: Passthrough logic
armleocpu_register_slice #(
    .DW(DATA_WIDTH + DATA_STROBES + 1)
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
    .DW(ID_WIDTH + 2)
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

generate if(!PASSTHROUGH) begin : AR_REGISTERED
armleocpu_register_slice #(
    .DW(AW_AR_DW)
) U_aw (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (upstream_axi_arvalid),
    .in_ready   (upstream_axi_arready),
    .in_data(`ADDR_SIGNALS(upstream_axi_ar)),

    .out_valid(downstream_axi_arvalid),
    .out_ready(downstream_axi_arvalid),
    .out_data(`ADDR_SIGNALS(downstream_axi_ar))
);
end else begin : AR_PASSTHROUGH
    assign downstream_axi_arvalid = upstream_axi_arvalid;
    assign upstream_axi_arready = downstream_axi_arready;
    assign `ADDR_SIGNALS(downstream_axi_ar) = `ADDR_SIGNALS(upstream_axi_ar);
end
endgenerate


// TODO: Passthrough logic
// R Bus



armleocpu_register_slice #(
    .DW(ID_WIDTH + 2 + 1 + DATA_WIDTH)
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

