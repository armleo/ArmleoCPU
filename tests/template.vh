
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

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

`ifndef TOP_TB
	`define TOP_TB top_tb
`endif

module `TOP_TB();

`ifndef CLK_HALF_PERIOD
    `define CLK_HALF_PERIOD 1
`endif

reg clk = 0;
reg rst_n = 1;
reg clk_enable = 0;

`ifndef ASYNC_RST
`define SYNC_RST
`endif

`ifdef ASYNC_RST
initial begin
	#`CLK_HALF_PERIOD rst_n = 0;
	#`CLK_HALF_PERIOD rst_n = 1;
	#`CLK_HALF_PERIOD clk_enable = 1;
end
`endif

`ifdef SYNC_RST
initial begin
    rst_n = 0;
	clk_enable = 1;
	#`CLK_HALF_PERIOD; #`CLK_HALF_PERIOD rst_n = 1;
end
`endif

always begin
	#`CLK_HALF_PERIOD  clk <= clk_enable ? !clk : clk;
end

`ifndef TIMEOUT
`define TIMEOUT 1000
`endif


`include "assert.vh"

`ifndef SIMRESULT
	`define SIMRESULT "dump.vcd"
`endif

initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars(0, `TOP_TB);
    #`TIMEOUT
    `assert_equal(0, 1)
end



