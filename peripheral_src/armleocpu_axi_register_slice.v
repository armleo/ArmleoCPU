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
    
    `AXI_FULL_MODULE_IO_NAMELIST(upstream_axi_),
    
    `AXI_FULL_MODULE_IO_NAMELIST(downstream_axi_)
);

    parameter ADDR_WIDTH = 32;
    parameter DATA_WIDTH = 32;
    parameter ID_WIDTH = 4;

    parameter PASSTHROUGH = 0;

    localparam DATA_STROBES = DATA_WIDTH/8;

    input wire          clk;
    input wire          rst_n;

    `AXI_FULL_IO_CLIENT     (upstream_axi_, ADDR_WIDTH, DATA_WIDTH, ID_WIDTH)
    `AXI_FULL_IO_HOST       (downstream_axi_, ADDR_WIDTH, DATA_WIDTH, ID_WIDTH)



// AW Bus

localparam AW_AR_DW = ADDR_WIDTH
        + 8 // len
        + 3 // size
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

// AW Bus
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

