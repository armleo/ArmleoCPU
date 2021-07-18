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
// Purpose: Decoding stage. Main purpose is to read the register data
////////////////////////////////////////////////////////////////////////////////


`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_decode (
    input                   clk,
    input                   rst_n,

    
    // Towards debug module. Shows that this pipeline stage is not active
    output reg              dbg_pipeline_busy,

    // Register file interface
    output reg              rs1_read,
    output reg  [4:0]       rs1_raddr,

    output reg              rs2_read,
    output reg  [4:0]       rs2_raddr,

    // Decode to execute interface
    output reg              d2e_valid,
    output reg [`F2E_TYPE_WIDTH-1:0]
                            d2e_type,
    output reg [31:0]       d2e_instr,
    output reg [31:0]       d2e_pc,
    output reg  [3:0]       d2e_resp, // Cache response

    // Execute to decode interface
    input wire              e2d_ready,
    input wire  [`ARMLEOCPU_E2D_CMD_WIDTH-1:0]
                            e2d_cmd,
    input wire [31:0]       e2d_branchtarget,
    input wire              e2d_rd_write,
    input wire  [4:0]       e2d_rd_waddr,


    // from fetch
    input wire              f2d_valid,
    input wire [`F2E_TYPE_WIDTH-1:0]
                            f2d_type,
    input wire [31:0]       f2d_instr,
    input wire [31:0]       f2d_pc,
    input wire  [3:0]       f2d_resp,

    // to decode
    output reg              d2f_ready,
    output reg [`ARMLEOCPU_D2F_CMD_WIDTH-1:0]
                            d2f_cmd,
    output wire [31:0]      d2f_branchtarget
);

wire rs_rd_match = e2d_ready && e2d_rd_write && ((e2d_rd_waddr == rs2_raddr) || (e2d_rd_waddr == rs1_raddr));

assign d2f_branchtarget = e2d_branchtarget;

assign rs1_raddr = f2d_instr[19:15];
assign rs2_raddr = f2d_instr[24:20];


assign dbg_pipeline_busy = f2d_valid || d2e_valid;

always @(posedge clk) begin
    if(e2d_ready) begin // If E2F Done
        d2e_valid <= 0;
        // Combinational, send d2f_cmd

    end
    
    if(f2d_valid && (!d2e_valid || (d2e_valid && e2d_ready)) && !rs_rd_match) begin
        // If Fetch done and no data towards execute or it's executed and rs1/rs2 is not written
        // Then pass the fetched data towards execute
        d2e_valid <= 1;
        // TODO: D2E registration
        // Combinational d2f_ready = 1
        // Combinational rs1_read and rs2_read
    end


end


endmodule
