////////////////////////////////////////////////////////////////////////////////
//
// Filename:    armleocpu_brcond.v
// Project:	ArmleoCPU
//
// Purpose:	ArmleoCPU's Branch condition calculation, designed for RISC-V
//
// Copyright (C) 2021, Arman Avetisyan
////////////////////////////////////////////////////////////////////////////////


`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_brcond(
	output reg branch_taken,
	output reg incorrect_instruction,
	input [2:0] funct3,
	input [31:0] rs1,
	input [31:0] rs2
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
