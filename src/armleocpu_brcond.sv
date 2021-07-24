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
// Filename:    armleocpu_brcond.v
// Project:	ArmleoCPU
//
// Purpose:	ArmleoCPU's Branch condition calculation, designed for RISC-V
//
////////////////////////////////////////////////////////////////////////////////


`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_brcond(
	output reg branch_taken,
	output reg incorrect_instruction,
	input wire [2:0] funct3,
	input wire [31:0] rs1,
	input wire [31:0] rs2
);

	always @* begin
		incorrect_instruction = 0;
		case(funct3)
			3'b000: //beq
				branch_taken = rs1 == rs2;
			3'b001: //bne
				branch_taken = rs1 != rs2;
			3'b100: //blt
				branch_taken = $signed(rs1) < $signed(rs2);
			3'b101: //bge
				branch_taken = $signed(rs1) >= $signed(rs2);
			3'b110: // bltu
				branch_taken = $unsigned(rs1) < $unsigned(rs2);
			3'b111: //bgeu
				branch_taken = $unsigned(rs1) >= $unsigned(rs2);
			default: begin
				branch_taken = 0;
				incorrect_instruction = 1;
			end
		endcase
	end
endmodule


`include "armleocpu_undef.vh"
