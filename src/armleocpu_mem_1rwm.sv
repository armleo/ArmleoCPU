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
// Filename: armleocpu_mem_1rwm.v
// Project:	ArmleoCPU
//
// Purpose:	Memory cell with separate section write enable, read first,
//			read result stays same until next read request is complete
//		
//
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE


module armleocpu_mem_1rwm (clk, address, read, readdata, write, writeenable, writedata);
	parameter ELEMENTS_W = 7;
	localparam ELEMENTS = 2**ELEMENTS_W;
	parameter WIDTH = 32;
	parameter GRANULITY = 8;
	localparam ENABLE_WIDTH = WIDTH/GRANULITY;

	input clk;

    input [ELEMENTS_W-1:0] address;
    input read;
    output wire [WIDTH-1:0] readdata;

	input write;
	input [WIDTH/GRANULITY-1:0] writeenable;
	input [WIDTH-1:0] writedata;

`ifdef SIMULATION
	initial begin
		if((WIDTH % GRANULITY) != 0) begin
			$display("Width is not divisible by granulity");
			$fatal;
		end
	end
`endif

genvar i;
generate for(i = 0; i < WIDTH; i = i + GRANULITY) begin : mem_generate_for
	armleocpu_mem_1rw #(ELEMENTS_W, GRANULITY) realstorage(
		.clk(clk),
		
		.address(address),

		.read(read),
		.readdata(readdata[i + GRANULITY - 1 : i]),

		.write(write & writeenable[i/GRANULITY]),
		.writedata(writedata[i + GRANULITY - 1 : i])
	);
end
endgenerate


endmodule


`include "armleocpu_undef.vh"
