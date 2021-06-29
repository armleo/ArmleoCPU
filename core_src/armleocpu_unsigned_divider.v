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
// Filename:    armleocpu_unsigned_divider.v
// Project:	ArmleoCPU
//
// Purpose:	Multi cycle divider
//          
//          
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE


module armleocpu_unsigned_divider(
	input  wire 		clk,
	input  wire 		rst_n,
	input  wire 		fetch,
	
	input  wire [31:0]  dividend,
	input  wire [31:0]  divisor,
	
	
	output reg			ready,
	output reg   		division_by_zero,
	output reg   [31:0] quotient,
	output reg   [31:0] remainder
);

reg [31:0] r_dividend;

reg [5:0] counter;


wire [31:0] difference = remainder - divisor;
wire positive = remainder >= divisor;

reg state;
localparam STATE_IDLE = 1'b0;
localparam STATE_OP = 1'b1;

always @(posedge clk) begin
	if(!rst_n) begin
		state <= STATE_IDLE;
		ready <= 0;
		division_by_zero <= 1'b0;
	end else begin
		case(state)
			STATE_IDLE: begin
				ready <= 0;
				division_by_zero <= 1'b0;
				counter <= 0;
				remainder <= 0;
				if(fetch) begin
					if(divisor != 0) begin
						r_dividend <= dividend;
						state <= STATE_OP;
					end else begin
						ready <= 1'b1;
						division_by_zero <= 1'b1;
					end
				end
			end
			STATE_OP: begin
				r_dividend <= {r_dividend[30:0], 1'b0};
				quotient <= {quotient[30:0], positive};
				
				if(positive) begin // add 1 to quotient, substract from dividend
					remainder <= {difference[30:0], r_dividend[31]};
				end else begin // add 0 to quotient,
					remainder <= {remainder[30:0], r_dividend[31]};
				end
				
				if(counter != 32) begin
					counter <= counter + 6'b1;
				end else begin
					if(positive)
						remainder <= difference;
					else
						remainder <= remainder;
					ready <= 1'b1;
					state <= STATE_IDLE;
				end
			end
		endcase
	end
end

endmodule


`include "armleocpu_undef.vh"

