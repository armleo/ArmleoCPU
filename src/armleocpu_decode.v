
module armleocpu_decode (
    input clk,
    input rst_n,

// CSR Registers input
    input                               csr_mstatus_tsr, // sret generates illegal instruction
    input                               csr_mstatus_tvm, // sfence vma and csr satp write generates illegal instruction
    input                               csr_mstatus_tw,  // wfi generates illegal instruction
    
    input      [1:0]                    csr_mcurrent_privilege,

// Debug I/O
    output reg                          d2dbg_dbg_mode, // Decode accepted fetch's debug mode

// Pipeline I/O
    input                               e2d_ready,

    output reg                          d2f_ready,

    output reg                          d2e_instr_valid,
    output reg [31:0]                   d2e_instr,
    output reg [DECODE_IS_WIDTH-1:0]    d2e_instr_decode,
    output reg                          d2e_instr_illegal,
    
// Regfile I/O
    output reg                          rs1_read,
    output     [4:0]                    rs1_addr,

    output reg                          rs2_read,
    output     [4:0]                    rs2_addr
);

`include "armleocpu_decode.vh"


// We will stall for Branch, JAL, JALR and CSR-like instructions
// Because CSR-Like instructions have fetch side effects
// Decode also stalls when execute is currently executing instruction which moddifies currenlty reading registers in this cycle or in the future

always @(posedge clk) begin
    <= ;
end

endmodule
