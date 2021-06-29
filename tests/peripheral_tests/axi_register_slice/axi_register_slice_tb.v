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

`define TIMEOUT 2000000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"

localparam DW = 16;

reg in_valid;
reg [DW-1:0] in_data;
wire in_ready;

wire out_valid;
wire [DW-1:0] out_data;
reg out_ready;

armleocpu_axi_register_slice #(DW) axi_register_slice (
	.*
);



initial begin
	in_valid = 0;
	out_ready = 0;
	@(posedge rst_n)
	@(negedge clk)
	`assert_equal(out_valid, 0)

	$display("Test case: Input is fed and no buffered data, no stall");
	in_valid = 1;
	in_data = 16'hFE0B;
	`assert_equal(in_ready, 1)
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0B)
	out_ready = 1;
	in_valid = 0;

	@(negedge clk)
	`assert_equal(out_valid, 0)
	



	$display("Test case: Two cycles Input is fed and no buffered data, no stall");
	in_valid = 1;
	in_data = 16'hFE0A;
	`assert_equal(in_ready, 1)
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0A)
	out_ready = 1;
	in_data = 16'hFE0C;
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0C)
	in_valid = 0;
	@(negedge clk)
	`assert_equal(out_valid, 0)
	

	$display("Test case: One cycle, then output is stalled");
	in_valid = 1;
	in_data = 16'hFE0A;
	`assert_equal(in_ready, 1)
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0A)
	in_data = 16'hFE0B;
	`assert_equal(in_ready, 1)
	out_ready = 0;
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0A)
	out_ready = 1;
	in_valid = 0;
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0B)


	@(negedge clk)
	@(negedge clk)
	$finish;
end


endmodule