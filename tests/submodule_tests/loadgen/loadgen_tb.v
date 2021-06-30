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

`define TIMEOUT 100
`define SYNC_RST
`define CLK_HALF_PERIOD 1

`include "template.vh"

reg [1:0] inword_offset;
reg [2:0] loadgen_type;


reg [31:0] loadgen_datain;

wire [31:0] loadgen_dataout;
wire loadgen_missaligned;
wire loadgen_unknowntype;
// TODO: Add test for loadgen_unknowntype


armleocpu_loadgen loadgen(
	.*
);

integer m;
reg [31:0] tempword;
initial begin
	@(negedge clk)
	loadgen_type = `LOAD_BYTE;
	loadgen_datain = 32'h8888_8888;
	for(m = 0; m < 4; m = m + 1) begin
		@(negedge clk)
		inword_offset = m;
		@(posedge clk)
		tempword = loadgen_datain >> (m * 8);
		`assert_equal($signed(loadgen_dataout), $signed(tempword[7:0]));
		`assert_equal(loadgen_missaligned, 0);
		$display("Test signed byte - Done inword_offset=%d", inword_offset);
	end

	@(negedge clk)
	loadgen_type = `LOAD_BYTE_UNSIGNED;
	loadgen_datain = 32'h8888_8888;
	for(m = 0; m < 4; m = m + 1) begin
		@(negedge clk)
		inword_offset = m;
		@(posedge clk)
		tempword = loadgen_datain >> (m * 8);
		`assert_equal(loadgen_dataout, tempword[7:0]);
		`assert_equal(loadgen_missaligned, 0);
		$display("Test unsigned byte - Done inword_offset=%d", inword_offset);
	end


	@(negedge clk)
	loadgen_type = `LOAD_HALF_UNSIGNED;
	loadgen_datain = 32'h8888_8888;
	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		inword_offset = m << 1;
		@(posedge clk)
		tempword = loadgen_datain >> (m * 16);
		`assert_equal(loadgen_dataout, tempword[15:0]);
		`assert_equal(loadgen_missaligned, 0);
		$display("Test aligned unsigned Halfword - Done inword_offset=%d", inword_offset);

		@(negedge clk)
		inword_offset = (m << 1) + 1;
		@(posedge clk)
		`assert_equal(loadgen_missaligned, 1);
		$display("Test missaligned unsigned Halfword - Done inword_offset=%d", inword_offset);
	end

	@(negedge clk)
	loadgen_type = `LOAD_HALF;
	loadgen_datain = 32'h8888_8888;
	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		inword_offset = m << 1;
		@(posedge clk)
		tempword = loadgen_datain >> (m * 16);
		`assert_equal($signed(loadgen_dataout), $signed(tempword[15:0]));
		`assert_equal(loadgen_missaligned, 0);
		$display("Test aligned signed Halfword - Done inword_offset=%d", inword_offset);

		@(negedge clk)
		inword_offset = (m << 1) + 1;
		@(posedge clk)
		`assert_equal(loadgen_missaligned, 1);
		$display("Test missaligned signed Halfword - Done inword_offset=%d", inword_offset);
	end

	@(negedge clk)
	loadgen_type = `LOAD_WORD;
	loadgen_datain = 32'h8888_8888;
	inword_offset = 0;
	@(negedge clk)
	`assert_equal(loadgen_missaligned, 0);
	`assert_equal(loadgen_dataout, loadgen_datain);
	$display("Test aligned word = Done inword_offset=%d", inword_offset);

	for(m = 1; m < 4; m = m + 1) begin
		@(negedge clk)
		inword_offset = m;
		@(posedge clk)
		`assert_equal(loadgen_missaligned, 1);
		$display("Test aligned word = Done inword_offset=%d", inword_offset);
	end
	
	$finish;
end
endmodule