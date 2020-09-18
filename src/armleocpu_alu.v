`include "armleocpu_includes.vh"

module armleocpu_alu(
    input select_imm,
    input [`ARMLEOCPU_ALU_SELECT_WIDTH-1:0] select_result,
    input               shamt_sel,
    
    input      [4:0]    shamt,
    input      [31:0]   op1,
    input      [31:0]   op2,

    output reg [31:0]   result
);

/* verilator lint_off WIDTH */
wire [4:0] internal_shamt   = select_shamt ? op2[4:0] : shamt;
/* verilator lint_on WIDTH */

wire add_result = op1 + op2;

always @* begin
    case(select_result)
        `ARMLEOCPU_ALU_SELECT_ADD:        result = add_result;
        `ARMLEOCPU_ALU_SELECT_SUB:        result = op1 - op2;
        /* verilator lint_off WIDTH */
        `ARMLEOCPU_ALU_SELECT_SLT:        result = ($signed(op1) < $signed(op2));
        `ARMLEOCPU_ALU_SELECT_SLTU:       result = ($unsigned(op1) < $unsigned(op2));
        /* verilator lint_on WIDTH */
        `ARMLEOCPU_ALU_SELECT_SLL:        result = op1 << internal_shamt;
        /* verilator lint_off WIDTH */
        `ARMLEOCPU_ALU_SELECT_SRA:        result = {{32{op1[31]}}, op1} >> internal_shamt;
        /* verilator lint_on WIDTH */
        `ARMLEOCPU_ALU_SELECT_SRL:        result = op1 >> internal_shamt;
        
        `ARMLEOCPU_ALU_SELECT_XOR:        result = op1 ^ op2;
        `ARMLEOCPU_ALU_SELECT_OR:         result = op1 | op2;
        `ARMLEOCPU_ALU_SELECT_AND:        result = op1 & op2;
        default: begin
            result = add_result;
        end
    endcase
end

endmodule
