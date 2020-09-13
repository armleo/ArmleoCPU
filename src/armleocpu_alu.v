`include "armleocpu_includes.vh"

module armleocpu_alu(
    input select_imm,
    input [ARMLEOCPU_ALU_SELECT_WIDTH-1:0] select_result,

    input      [4:0]    shamt,
    
    input      [31:0]   rs1,
    input      [31:0]   rs2,
    input      [31:0]   simm12,


    
    output reg [31:0]   result
);

wire [31:0] internal_op2     = select_imm ? rs2 : simm12;
/* verilator lint_off WIDTH */
wire [4:0] internal_shamt   = select_imm ? rs2[4:0] : shamt;
/* verilator lint_on WIDTH */

wire add_result = rs1 + internal_op2;

always @* begin
    case(select_result)
        `ARMLEOCPU_ALU_SELECT_ADD:        result = add_result;
        `ARMLEOCPU_ALU_SELECT_SUB:        result = rs1 - rs2;
        /* verilator lint_off WIDTH */
        `ARMLEOCPU_ALU_SELECT_SLT:        result = ($signed(rs1) < $signed(internal_op2));
        `ARMLEOCPU_ALU_SELECT_SLTU:       result = ($unsigned(rs1) < $unsigned(internal_op2));
        /* verilator lint_on WIDTH */
        `ARMLEOCPU_ALU_SELECT_SLL:        result = rs1 << internal_shamt;
        /* verilator lint_off WIDTH */
        `ARMLEOCPU_ALU_SELECT_SRA:        result = {{32{rs1[31]}}, rs1} >> internal_shamt;
        /* verilator lint_on WIDTH */
        `ARMLEOCPU_ALU_SELECT_SRL:        result = rs1 >> internal_shamt;

        `ARMLEOCPU_ALU_SELECT_XOR:        result = rs1 ^ internal_op2;
        `ARMLEOCPU_ALU_SELECT_OR:         result = rs1 | internal_op2;
        `ARMLEOCPU_ALU_SELECT_AND:        result = rs1 & internal_op2;
        default: begin
            result = add_result;
        end
    endcase
end

endmodule
