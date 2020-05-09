module corevx_immgen(
    input [31:0] instruction,
    input [2:0] sel,
    output [31:0] out
);

`include "corevx_immgen.svh"

wire sign = instruction[31];

wire [31:0] Iimm = {{20{sign}}, instruction[31:20]};
wire [31:0] Simm = {{20{sign}}, instruction[31:25], instruction[11:7]};
wire [31:0] Bimm = {{20{sign}}, instruction[7], instruction[30:25], instruction[11:8], 1'b0};
    // BIMM -> 1 + 6 + 4 + 1
wire [31:0] Uimm = {instruction[31:12], 12'h000};
wire [31:0] Zimm = {27'b0, instruction[19:15]}; // used by csr bit write/set/clear

always @* begin
    out = Iimm;
    case(sel)
        `IMM_I: out = Iimm;
        `IMM_S: out = Simm;
        `IMM_B: out = Bimm;
        `IMM_U: out = Uimm;
        `IMM_Z: out = Zimm;
        default: out = Iimm;
    endcase
end

endmodule