module armleocpu_alu(
	input wire is_alui,
	input wire [2:0] funct3,
	input wire [6:0] funct7,
	
	input wire [31:0] operand0,
	input wire [31:0] alu_operand1,
	input wire [31:0] alui_operand1,
	
	output reg [31:0] result,
	output reg unknown_operation
);

localparam ALU_OP_ADD    = 10'b0000000_000;
localparam ALU_OP_SUB    = 10'b0100000_000;
localparam ALU_OP_SLL    = 10'b0000000_001;
localparam ALU_OP_SLT    = 10'b0000000_010;
localparam ALU_OP_SLTU   = 10'b0000000_011;
localparam ALU_OP_XOR    = 10'b0000000_100;
localparam ALU_OP_SRL    = 10'b0000000_101;
localparam ALU_OP_SRA    = 10'b0100000_101;
localparam ALU_OP_OR     = 10'b0000000_110;
localparam ALU_OP_AND    = 10'b0000000_111;
localparam ALU_OP_MUL	 = 10'b0000001_000;
localparam ALU_OP_MULH	 = 10'b0000001_001;
localparam ALU_OP_MULHSU = 10'b0000001_010;
localparam ALU_OP_MULHU	 = 10'b0000001_011;
localparam ALU_OP_DIV	 = 10'b0000001_100;
localparam ALU_OP_DIVU	 = 10'b0000001_101;
localparam ALU_OP_REM	 = 10'b0000001_110;
localparam ALU_OP_REMU	 = 10'b0000001_111;


reg [31:0] operand1;

always @* begin
	if(is_alui)
		operand1 = alui_operand1;
	else
		operand1 = alu_operand1;
end

wire [63:0] mul_result = $signed(operand0) * $signed(operand1);
wire [63:0] mulu_result = $unsigned(operand0) * $unsigned(operand1);
wire [63:0] mulsu_result = $signed(operand0) * $unsigned(operand1);

wire [6:0] funct7_comb = is_alui ? 7'h0 : funct7;

always @* begin
	unknown_operation = 0;
	case ({funct7_comb, funct3})
		ALU_OP_ADD:     result = operand0 + operand1;
		ALU_OP_SUB:     result = operand0 - operand1;
		ALU_OP_SLL:     result = operand0 << operand1[4:0];
        /* verilator lint_off WIDTH */
		ALU_OP_SLT:     result = $signed(operand0) < $signed(operand1);
		ALU_OP_SLTU:    result = $unsigned(operand0) < $unsigned(operand1);
		/* verilator lint_on WIDTH */
		ALU_OP_XOR:     result = operand0 ^ operand1;
		ALU_OP_SRL:     result = operand0 >> operand1[4:0];
		ALU_OP_SRA:     result = $signed(operand0) >>> operand1[4:0];
		ALU_OP_OR:      result = operand0 | operand1;
		ALU_OP_AND:     result = operand0 & operand1;
		ALU_OP_MUL:     result = mul_result[31:0];
		ALU_OP_MULH:    result = mul_result[63:32];
		ALU_OP_MULHSU:  result = mulsu_result[63:32];
		ALU_OP_MULHU:   result = mulu_result[63:32];
		ALU_OP_DIV:
			if(operand1 == 0)
				result = -1;
			else if(operand0 == -2147483648 && operand1 == -1)
				result = -2147483648;
			else
				result = $signed(operand0) / $signed(operand1);
		ALU_OP_REM:
			if(operand1 == 0)
				result = operand0;
			else if(operand0 == -2147483648 && operand1 == -1)
				result = 0;
			else
				result = $signed(operand0) % $signed(operand1);
		ALU_OP_DIVU:
			if(operand1 == 0)
				result = -1;
			else
				result = $unsigned(operand0) / $unsigned(operand1);
		ALU_OP_REMU:
			if(operand1 == 0)
				result = operand0;
			else
				result = $unsigned(operand0) % $unsigned(operand1);
		default: begin
			result = operand0 + operand1;
			unknown_operation = 1;
		end
	endcase
end
endmodule