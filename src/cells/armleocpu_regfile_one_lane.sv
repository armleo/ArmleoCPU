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
// Filename: armleocpu_regfile_one_lane.v
// Project:	ArmleoCPU
//
// Purpose:	Used by regfile to allow two reads and one write
//		two instances is used, where
//		write port is connected together and exposed and
//		two read ports are exposed to core itself
//		
//
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_regfile_one_lane (clk, readaddress, read, readdata, writeaddress, write, writedata);
	parameter ELEMENTS_W = 5;
	localparam ELEMENTS = 2**ELEMENTS_W;
	parameter WIDTH = 32;

	input wire clk;

    input wire [ELEMENTS_W-1:0] readaddress;
    input wire read;
	output reg [WIDTH-1:0] readdata;


	input wire [ELEMENTS_W-1:0] writeaddress;
	input wire write;
	input wire  [WIDTH-1:0] writedata;

reg [WIDTH-1:0] storage[ELEMENTS-1:0];

always @(posedge clk) begin
	if(write) begin
		storage[writeaddress] <= writedata;
	end
	if(read)
		readdata <= storage[readaddress];
end

endmodule


`include "armleocpu_undef.vh"

