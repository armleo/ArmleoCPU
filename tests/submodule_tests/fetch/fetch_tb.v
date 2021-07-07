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

`define TIMEOUT 100
`define SYNC_RST
`define CLK_HALF_PERIOD 5

`define MAXIMUM_ERRORS 1

`include "template.vh"


reg [31:0] reset_vector;
reg c_done;
reg [3:0] c_response;

wire [3:0] c_cmd;
wire [31:0] c_address;

reg [31:0] c_load_data;

reg interrupt_pending;
reg dbg_mode;
wire busy;

wire f2d_valid;
wire [`F2E_TYPE_WIDTH-1:0] f2d_type;
wire [31:0] f2d_instr;
wire [31:0] f2d_pc;

reg d2f_ready;
reg [`ARMLEOCPU_D2F_CMD_WIDTH-1:0] d2f_cmd;
reg [31:0] d2f_branchtarget;


armleocpu_fetch u0 (
    .*
);

initial begin
    reset_vector = 32'h100;
    c_done = 0;
    c_response = `CACHE_RESPONSE_SUCCESS;
    c_load_data = 0;

    interrupt_pending = 0;
    dbg_mode = 0;

    d2f_ready = 0;


    @(posedge rst_n)

    $display("Testbench: Starting fetch testing");
    
    @(negedge clk);
    `assert_equal(c_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(c_address, 32'h100);


    $display("Testbench: Tests passed");
    $finish;
end


endmodule
