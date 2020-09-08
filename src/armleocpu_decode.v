
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
    // FETCH <-> DECODE
    input                               f2d_instr_valid,
    input  [31:0]                       f2d_instr,
    input      [31:0]                   f2d_pc,
    input                               f2d_interrupt_pending,
    input  [31:0]                       f2d_instruction_fetch_exception,
    
    
    // DECODE <-> EXECUTE
    output reg                          d2e_instr_valid,
    output reg [31:0]                   d2e_instr,
    output reg [DECODE_IS_WIDTH-1:0]    d2e_instr_decode,
    output reg                          d2e_instr_illegal,
    output reg [31:0]                   d2e_instruction_fetch_exception,
    output                              d2e_interrupt_pending,

    input                               wb2d_rd_write,
    input                               wb2d_rd_waddr,

    input                               e2d_rd_write,
    input                               e2d_rd_waddr,
    
    input                               stall,
    output                              stall_o,
    // Goes to fetch unit
    // to request fetch to stall pipeline when register that is being written to contains stlae data

    
// Regfile I/O
    output reg                          rs1_read,
    output     [4:0]                    rs1_addr,

    output reg                          rs2_read,
    output     [4:0]                    rs2_addr
);

`include "armleocpu_decode.vh"

// Decode opcode
assign      rs1_addr                = f2d_instr[19:15];
assign      rs2_addr                = f2d_instr[24:20];
wire [6:0]  opcode                  = f2d_instr[6:0];
wire [2:0]  funct3                  = f2d_instr[14:12];
wire [6:0]  funct7                  = f2d_instr[31:25];

wire comb_is_op_imm  = opcode == `OPCODE_OP_IMM;
wire comb_is_op      = opcode == `OPCODE_OP;
wire comb_is_jalr    = opcode == `OPCODE_JALR;
wire comb_is_jal     = opcode == `OPCODE_JAL;
wire comb_is_lui     = opcode == `OPCODE_LUI;
wire comb_is_auipc   = opcode == `OPCODE_AUIPC;
wire comb_is_branch  = opcode == `OPCODE_BRANCH;
wire comb_is_store   = opcode == `OPCODE_STORE && funct3[2] == 0;
wire comb_is_load    = opcode == `OPCODE_LOAD;
wire comb_is_system  = opcode == `OPCODE_SYSTEM;
wire comb_is_fence   = opcode == `OPCODE_FENCE;

wire comb_is_ebreak  = comb_is_system && f2d_instr == 32'b000000000001_00000_000_00000_1110011;

wire comb_is_ecall   = comb_is_system && f2d_instr == 32'b000000000000_00000_000_00000_1110011;
wire comb_is_wfi     = !csr_mstatus_tw && comb_is_system && f2d_instr == 32'b0001000_00101_00000_000_00000_1110011;
wire comb_is_mret    = (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) && comb_is_system && f2d_instr == 32'b0011000_00010_00000_000_00000_1110011;
wire comb_is_sret    = (!(csr_mstatus_tsr && csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR) ||
                (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE))
                && comb_is_system && f2d_instr == 32'b0001000_00010_00000_000_00000_1110011;

wire comb_is_sfence_vma = !(csr_mstatus_tvm && csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR)) && comb_is_system && f2d_instr[11:7] == 5'b00000 && f2d_instr[14:12] == 3'b000 && f2d_instr[31:25] == 7'b0001001;
wire comb_is_ifencei = comb_is_fence && f2d_instr[14:12] == 3'b001;
wire comb_is_fence_normal = comb_is_fence && f2d_instr[14:12] == 3'b000;

wire comb_is_cache_flush = comb_is_sfence_vma || comb_is_ifencei || comb_is_fence_normal;

wire comb_is_csrrw_csrrwi = comb_is_system && funct3[1:0] == 2'b01;
wire comb_is_csrs_csrsi   = comb_is_system && funct3[1:0] == 2'b10;
wire comb_is_csrc_csrci   = comb_is_system && funct3[1:0] == 2'b11;

wire comb_is_csr     = comb_is_csrrw_csrrwi || comb_is_csrs_csrsi || comb_is_csrc_csrci;

wire comb_is_mul         = comb_is_op     && (funct3 == 3'b000) && (funct7 == 7'b0000_001);
wire comb_is_mulh        = comb_is_op     && (funct3 == 3'b001) && (funct7 == 7'b0000_001);
wire comb_is_mulhsu      = comb_is_op     && (funct3 == 3'b010) && (funct7 == 7'b0000_001);
wire comb_is_mulhu       = comb_is_op     && (funct3 == 3'b011) && (funct7 == 7'b0000_001);

wire comb_is_div         = comb_is_op     && (funct3 == 3'b100) && (funct7 == 7'b0000_001);
wire comb_is_divu        = comb_is_op     && (funct3 == 3'b101) && (funct7 == 7'b0000_001);

wire comb_is_rem         = comb_is_op     && (funct3 == 3'b110) && (funct7 == 7'b0000_001);
wire comb_is_remu        = comb_is_op     && (funct3 == 3'b111) && (funct7 == 7'b0000_001);

wire comb_is_amo                = ;
wire comb_is_load_reserve       = comb_is_amo && ;
wire comb_is_store_conditional  = comb_is_amo && ;

always @(posedge clk) begin
    if(!rst_n) begin

    end else begin
        if(!stall && e2d_kill) begin
            d2e_instr_valid <= 0;
        end else if(!stall) begin
            
            

            d2e_instr_decode[`DECODE_IS_OP_IMM] <= comb_is_op_imm;
            d2e_instr_decode[`DECODE_IS_OP] <= comb_is_op;
            d2e_instr_decode[`DECODE_IS_JALR] <= comb_is_jalr;
            d2e_instr_decode[`DECODE_IS_JAL] <= comb_is_jal;
            d2e_instr_decode[`DECODE_IS_LUI] <= comb_is_lui;
            d2e_instr_decode[`DECODE_IS_AUIPC] <= comb_is_auipc;
            d2e_instr_decode[`DECODE_IS_BRANCH] <= comb_is_branch;
            d2e_instr_decode[`DECODE_IS_STORE] <= comb_is_store;
            d2e_instr_decode[`DECODE_IS_LOAD] <= comb_is_load;

            d2e_instr_decode[`DECODE_IS_EBREAK] <= comb_is_ebreak;
            d2e_instr_decode[`DECODE_IS_ECALL] <= comb_is_ecall;
            d2e_instr_decode[`DECODE_IS_WFI] <= comb_is_wfi;
            d2e_instr_decode[`DECODE_IS_MRET] <= comb_is_mret;
            d2e_instr_decode[`DECODE_IS_SRET] <= comb_is_sret;

            d2e_instr_decode[`DECODE_CACHE_FLUSH] <= comb_is_cache_flush;

            d2e_instr_decode[`DECODE_IS_CSRRW_CSRRWI] <= comb_is_csrrw_csrrwi;
            d2e_instr_decode[`DECODE_IS_CSRS_CSRSI] <= comb_is_csrs_csrsi;
            d2e_instr_decode[`DECODE_IS_CSRC_CSRCI] <= comb_is_csrc_csrci;
            d2e_instr_decode[`DECODE_IS_CSR] <= comb_is_csr;

            d2e_instr_decode[`DECODE_IS_MUL] <= comb_is_mul;
            d2e_instr_decode[`DECODE_IS_MULH] <= comb_is_mulh;
            d2e_instr_decode[`DECODE_IS_MULHSU] <= comb_is_mulhsu;
            d2e_instr_decode[`DECODE_IS_MULHU] <= comb_is_mulhu;
            
            d2e_instr_decode[`DECODE_IS_DIV] <= comb_is_div;
            d2e_instr_decode[`DECODE_IS_DIVU] <= comb_is_divu;

            d2e_instr_decode[`DECODE_IS_REM] <= comb_is_rem;
            d2e_instr_decode[`DECODE_IS_REMU] <= comb_is_remu;

            d2e_instr_decode[`DECODE_LOAD_RESERVE] <= comb_is_load_reserve;
            d2e_instr_decode[`DECODE_STORE_CONDITIONAL] <= comb_is_store_conditional;
        end
    end
end

endmodule
