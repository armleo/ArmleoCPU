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

`define TIMEOUT 10000
`define SYNC_RST
`define CLK_HALF_PERIOD 1

`include "template.vh"

reg         valid;
reg [31:0]  factor0;
reg [31:0]  factor1;

wire         ready;
wire [63:0]  result;

armleocpu_multiplier mult(
	.*
);

initial begin
	reg [63:0] i;
	reg [63:0] j;
	valid <= 0;
	@(posedge rst_n);
	$display("Testbench: 64 * 53 test");
	@(posedge clk)
	valid <= 1;
	factor0 <= 64;
	factor1 <= 53;
	@(posedge clk)
	valid <= 0;
	while(ready != 1)
		@(posedge clk);
	`assert_equal(result, 64*53);
	@(posedge clk);
	$display("Testbench: 64'hFFFF_FFFF * 64'hFFFF_FFFF test");
	valid <= 1;
	factor0 <= 32'hFFFF_FFFF;
	factor1 <= 32'hFFFF_FFFF;
	@(posedge clk)
	valid <= 0;

	while(ready != 1)
		@(posedge clk);
	`assert_equal(result, 64'hFFFF_FFFE_0000_0001);
	`assert_equal(result, 64'hFFFF_FFFF * 64'hFFFF_FFFF);
	@(posedge clk);

	$display("Testbench: Stress testing");
	for(i = -100; i < 100; i = i + 1) begin
		for(j = -100; j < 100; j = j + 1) begin
			valid <= 1;
			factor0 <= i[31:0];
			factor1 <= j[31:0];
			@(posedge clk)
			valid <= 0;

			while(ready != 1)
				@(posedge clk);
			`assert_equal(result, i * j);
		end
	end
	$display("Testbench: All tests passed");
	$finish;
end
endmodule