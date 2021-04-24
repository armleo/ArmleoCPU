module armleocpu_brcond(
	output reg branch_taken,
	output reg unknown_funct3,
	input [2:0] funct3,
	input [31:0] rs1,
	input [31:0] rs2
);

wire equal = rs1 == rs2;
wire signed_less_than = $signed(rs1) < $signed(rs2);
wire unsigned_less_than = $unsigned(rs1) < $unsigned(rs2);

	always @* begin
		incorrect_instruction = 0;
		case(funct3)
			3'b000: //beq
				branch_taken = equal;
			3'b001: //bne
				branch_taken = !equal;
			3'b100: //blt
				branch_taken = signed_less_than;
			3'b101: //bge
				branch_taken = !signed_less_than;
			3'b110: // bltu
				branch_taken = unsigned_less_than;
			3'b111: //bgeu
				branch_taken = !unsigned_less_than;
			default: begin
				branch_taken = 0;
				unknown_funct3 = 1;
			end
		endcase
	end
endmodule