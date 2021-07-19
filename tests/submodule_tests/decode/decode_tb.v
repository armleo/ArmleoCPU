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

`define TIMEOUT 1000
`define SYNC_RST
`define CLK_HALF_PERIOD 5

`define MAXIMUM_ERRORS 1

`include "template.vh"

wire dbg_pipeline_busy;
wire rs1_read, rs2_read;
wire [4:0] rs1_raddr;
wire [4:0] rs2_raddr;

wire d2e_valid;
wire [`F2E_TYPE_WIDTH-1:0] d2e_type;
wire [31:0] d2e_instr;
wire [31:0] d2e_pc;
wire [3:0] d2e_resp;

reg e2d_ready;
reg [`ARMLEOCPU_E2D_CMD_WIDTH-1:0] e2d_cmd;
reg [31:0] e2d_branchtarget;
reg e2d_rd_write;
reg [4:0] e2d_rd_waddr;

reg f2d_valid;
reg [`F2E_TYPE_WIDTH-1:0] f2d_type;
reg [31:0] f2d_instr;
reg [31:0] f2d_pc;
reg [31:0] f2d_resp;

wire d2f_ready;
wire [`ARMLEOCPU_D2F_CMD_WIDTH-1:0] d2f_cmd;
wire [31:0] d2f_branchtarget;


armleocpu_decode decode (
    .*
);

`define TESTBENCH_START(str) \
    $display("Time: %t, Testbench: %s", $time, ``str``);

initial begin

    @(posedge rst_n)

    `TESTBENCH_START("Testbench: Starting decode testing");
    
    @(negedge clk);

    `TESTBENCH_START("Testbench: No tests for decode yet");
    

    @(negedge clk);

    `TESTBENCH_START("Testbench: Tests passed");
    $finish;
end


endmodule


`include "armleocpu_undef.vh"

