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

