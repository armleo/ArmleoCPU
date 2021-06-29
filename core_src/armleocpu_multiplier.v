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
// Filename: armleocpu_multiplier.v
// Project:	ArmleoCPU
//
// Purpose:	Multiplier 32x32 = 64
//		
//
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE



module armleocpu_multiplier(
	input  wire         clk,
    input  wire         rst_n,
	
	input  wire         valid,
	
	input  wire [31:0]  factor0,
	input  wire [31:0]  factor1,
	
	output reg          ready,
	output wire [63:0]  result
);

assign result = accumulator;

localparam STATE_IDLE = 1'd0;
localparam STATE_OP = 1'd1;
reg state = STATE_IDLE;

reg [63:0] accumulator;
reg [63:0] a;
reg [31:0] b;
reg [5:0] cycle;

always @(posedge clk) begin
	if(!rst_n) begin
		state <= STATE_IDLE;
		ready <= 0;
	end else begin
		case(state)
			STATE_IDLE: begin
				ready <= 0;
				accumulator <= 0;
				cycle <= 0;
				if(factor1 < factor0) begin
					a <= factor0;
					b <= factor1;
				end else begin
					a <= factor1;
					b <= factor0;
				end
				
				if(valid) begin
					state <= STATE_OP;
				end
			end
			STATE_OP: begin
				ready <= 0;
				accumulator <= accumulator + (b[0] ? a : 0);
				a <= a << 1;
				b <= {1'b0, b[31:1]}; // Shift right
				if(cycle == 31 || (b == 0)) begin
					ready <= 1;
					state <= STATE_IDLE;
				end else begin
					cycle <= cycle + 1;
				end
			end
		endcase
	end
end

endmodule


`include "armleocpu_undef.vh"
