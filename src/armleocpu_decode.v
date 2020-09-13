
module armleocpu_decode (
    input clk,
    input rst_n,

// CSR Registers input
    input                               csr_mstatus_tsr, // sret generates illegal instruction
    input                               csr_mstatus_tvm, // sfence vma and csr satp write generates illegal instruction
    input                               csr_mstatus_tw,  // wfi generates illegal instruction
    
    input      [1:0]                    csr_mcurrent_privilege,
    // TODO: csr registers for memory access
// Pipeline I/O
    // FETCH <-> DECODE
    input                               f2d_instr_valid,
    input  [31:0]                       f2d_instr,
    input  [31:0]                       f2d_pc,
    /*
    input                               f2d_interrupt_pending,
    input  [31:0]                       f2d_instruction_fetch_exception,
    */

    output reg                          d2f_ready,
    output reg                          d2f_cmd,
    
    // DECODE <-> EXECUTE
    output reg                          d2e_instr_valid,
    output reg [31:0]                   d2e_instr,
    output reg                          d2e_instr_decode_alu_output_sel,
    d2e_instr_illegal
    d2e_instr_decode_type
    d2e_instr_decode_alu_in0_mux_sel
    d2e_instr_decode_alu_in1_mux_sel
    d2e_rd_sel
    output reg [31:0]                   d2e_pc,
    

    /*
    output reg [`ARMLEOCPU_ALU_SELECT_WIDTH-1:0]  d2e_instr_decode_alu_output_sel,
    output reg [31:0]                   d2e_instruction_fetch_exception,
    output reg                          d2e_interrupt_pending,
    */
    input                               e2d_ready,
    input                               e2d_cmd,
    input [31:0]                        e2d_jump_target,
    input                               e2d_rd_write,
    input                               e2d_rd_waddr,

    // Regfile I/O
    output reg                          rs1_read,
    output     [4:0]                    rs1_addr,

    output reg                          rs2_read,
    output     [4:0]                    rs2_addr
);

`include "armleocpu_includes.vh"

// Decode opcode
assign      rs1_addr                = f2d_instr[19:15];
assign      rs2_addr                = f2d_instr[24:20];
wire [6:0]  opcode                  = f2d_instr[6:0];
wire [2:0]  funct3                  = f2d_instr[14:12];
wire [6:0]  funct7                  = f2d_instr[31:25];

wire comb_is_op_imm  = opcode == `ARMLEOCPU_OPCODE_OP_IMM;
wire comb_is_op      = opcode == `ARMLEOCPU_OPCODE_OP;
wire comb_is_jalr    = opcode == `ARMLEOCPU_OPCODE_JALR && funct3 == 3'h0;
wire comb_is_jal     = opcode == `ARMLEOCPU_OPCODE_JAL;
wire comb_is_lui     = opcode == `ARMLEOCPU_OPCODE_LUI;
wire comb_is_auipc   = opcode == `ARMLEOCPU_OPCODE_AUIPC;
wire comb_is_branch  = opcode == `ARMLEOCPU_OPCODE_BRANCH;
wire comb_is_store   = opcode == `ARMLEOCPU_OPCODE_STORE && funct3[2] == 0;
wire comb_is_load    = opcode == `ARMLEOCPU_OPCODE_LOAD;
wire comb_is_system  = opcode == `ARMLEOCPU_OPCODE_SYSTEM;
wire comb_is_fence   = opcode == `ARMLEOCPU_OPCODE_FENCE;
wire comb_is_amo     = opcode == `ARMLEOCPU_OPCODE_AMO;


wire comb_is_addi        = comb_is_op_imm && (funct3 == 3'b000);
wire comb_is_slti        = comb_is_op_imm && (funct3 == 3'b010);
wire comb_is_sltiu       = comb_is_op_imm && (funct3 == 3'b011);
wire comb_is_xori        = comb_is_op_imm && (funct3 == 3'b100);
wire comb_is_ori         = comb_is_op_imm && (funct3 == 3'b110);
wire comb_is_andi        = comb_is_op_imm && (funct3 == 3'b111);

wire comb_is_slli        = comb_is_op_imm && (funct3 == 3'b001) && (funct7 == 7'b0000_000);
wire comb_is_srli        = comb_is_op_imm && (funct3 == 3'b101) && (funct7 == 7'b0000_000);
wire comb_is_srai        = comb_is_op_imm && (funct3 == 3'b101) && (funct7 == 7'b0100_000);

wire comb_is_alui =
    comb_is_addi ||
    comb_is_slti ||
    comb_is_sltiu ||
    comb_is_xori ||
    comb_is_ori ||
    comb_is_andi || 
    comb_is_slli ||
    comb_is_srli ||
    comb_is_srai;

wire comb_is_add         = comb_is_op     && (funct3 == 3'b000) && (funct7 == 7'b0000_000);
wire comb_is_sub         = comb_is_op     && (funct3 == 3'b000) && (funct7 == 7'b0100_000);
wire comb_is_slt         = comb_is_op     && (funct3 == 3'b010) && (funct7 == 7'b0000_000);
wire comb_is_sltu        = comb_is_op     && (funct3 == 3'b011) && (funct7 == 7'b0000_000);
wire comb_is_xor         = comb_is_op     && (funct3 == 3'b100) && (funct7 == 7'b0000_000);
wire comb_is_or          = comb_is_op     && (funct3 == 3'b110) && (funct7 == 7'b0000_000);
wire comb_is_and         = comb_is_op     && (funct3 == 3'b111) && (funct7 == 7'b0000_000);

wire comb_is_sll         = comb_is_op     && (funct3 == 3'b001) && (funct7 == 7'b0000_000);
wire comb_is_srl         = comb_is_op     && (funct3 == 3'b101) && (funct7 == 7'b0000_000);
wire comb_is_sra         = comb_is_op     && (funct3 == 3'b101) && (funct7 == 7'b0100_000);

wire comb_is_alu = 
    comb_is_add ||
    comb_is_sub ||
    comb_is_slt ||
    comb_is_sltu ||
    comb_is_xor ||
    comb_is_or ||
    comb_is_and ||
    comb_is_sll ||
    comb_is_srl ||
    comb_is_sra;


wire comb_is_mul         = comb_is_op     && (funct3 == 3'b000) && (funct7 == 7'b0000_001);
wire comb_is_mulh        = comb_is_op     && (funct3 == 3'b001) && (funct7 == 7'b0000_001);
wire comb_is_mulhsu      = comb_is_op     && (funct3 == 3'b010) && (funct7 == 7'b0000_001);
wire comb_is_mulhu       = comb_is_op     && (funct3 == 3'b011) && (funct7 == 7'b0000_001);

wire comb_is_div         = comb_is_op     && (funct3 == 3'b100) && (funct7 == 7'b0000_001);
wire comb_is_divu        = comb_is_op     && (funct3 == 3'b101) && (funct7 == 7'b0000_001);

wire comb_is_rem         = comb_is_op     && (funct3 == 3'b110) && (funct7 == 7'b0000_001);
wire comb_is_remu        = comb_is_op     && (funct3 == 3'b111) && (funct7 == 7'b0000_001);

wire comb_is_muldiv =
    comb_is_mul || comb_is_mulh || comb_is_mulhsu || comb_is_mulhu ||
    comb_is_div || comb_is_divu ||
    comb_is_rem || comb_is_remu;


wire rs1_read_allowed = (
    comb_is_op_imm ||
    comb_is_op ||
    comb_is_jalr ||
    comb_is_branch || 
    comb_is_load ||
    comb_is_load_reserve || comb_is_store_conditional ||
    ((comb_is_csrrw_csrrwi || comb_is_csrs_csrsi || comb_is_csrc_csrci) && funct3[2] == 1'b0) // It's CSR with register access
);

wire rs2_read_allowed = (
    comb_is_op ||
    comb_is_branch ||
    comb_is_store ||
    comb_is_store_conditional
);

wire rs1_stale = rs1_read_allowed && ((rs1_addr == e2d_rd_waddr && e2d_rd_write) || (rs1_addr == e2d_rd_waddr && e2d_rd_write));

wire rs2_stale = rs2_read_allowed && ((rs2_addr == e2d_rd_waddr && e2d_rd_write) || (rs2_addr == e2d_rd_waddr && e2d_rd_write));

reg decode_next;
reg d2e_instr_valid_nxt;


always @* begin
    decode_next = 0;
    d2e_instr_valid_nxt = d2e_instr_valid;

    if(d2e_instr_valid) begin
        if(e2d_ready) begin
            d2f_cmd_valid = 1;
            d2f_cmd = e2d_cmd;
            if(f2d_instr_valid && e2d_cmd == CMD_NONE) begin
                if(rs1_stale || rs2_stale) begin
                    d2e_instr_valid_nxt = 0;
                    decode_next = 0;
                end else begin
                    decode_next = 1;
                    d2e_instr_valid_nxt = 1;
                end
            end else if(!f2d_instr_valid || e2d_cmd == CMD_KILL || FLUSH || BRANCH) begin
                d2e_instr_valid_nxt = 0;
            end
        end
    end else begin
        if(f2d_instr_valid) begin
            decode_next = 1;
            d2e_instr_valid_nxt = 1;
        end
    end
    if(decode_next) begin
        rs1_read = 1;
        rs2_read = 1;
    end
end

always @(posedge clk) begin
    // TODO: Reset
    d2e_instr_valid <= d2e_instr_valid_nxt;


    if(decode_next) begin
        case (1)
            comb_is_add, comb_is_addi:      d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_ADD;
            comb_is_sub:                    d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_SUB;
            comb_is_slt, comb_is_slti:      d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_SLT;
            comb_is_sltu, comb_is_sltiu:    d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_SLTU;
            comb_is_sll, comb_is_slli:      d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_SLL;
            comb_is_sra, comb_is_srai:      d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_SRA;
            comb_is_srl, comb_is_srli:      d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_SRL;
            comb_is_xor, comb_is_xori:      d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_XOR;
            comb_is_or, comb_is_ori:        d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_OR;
            comb_is_and, comb_is_andi:      d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_AND;
            comb_is_mul:                    d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_MUL;     
            comb_is_mulh:                   d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_MULH;
            comb_is_mulhsu:                 d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_MULHSU;
            comb_is_mulhu:                  d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_MULHU;
            comb_is_div:                    d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_DIV;
            comb_is_divu:                   d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_DIVU;
            comb_is_rem:                    d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_REM;
            comb_is_remu:                   d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_REMU;
            default: begin
                d2e_instr_decode_alu_output_sel  <= `ARMLEOCPU_ALU_SELECT_ADD;
            end
        endcase

        // Defaults:
        d2e_instr_illegal <= 0;
        d2e_instr_decode_type <= `ARMLEOCPU_DECODE_INSTRUCTION_ALU;
        d2e_instr_decode_alu_in0_mux_sel <= `ARMLEOCPU_DECODE_IN0_MUX_SEL_RS1;
        d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_RS2;
        d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_ALU;

        case (1)
            comb_is_alu: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_ALU;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_RS2;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_ALU;
            end
            comb_is_alui: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_ALU;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_SIMM12;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_ALU;
            end
            comb_is_muldiv: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_MULDIV;
                if()
                d2e_instr_decode_muldiv_sel <= ;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_MULDIV;
            end
            comb_is_jalr: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_JUMP;
                d2e_instr_decode_alu_output_sel <= `ARMLEOCPU_ALU_SELECT_ADD;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_SIMM12;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_ALU;
            end
            comb_is_jal: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_JUMP;
                d2e_instr_decode_alu_output_sel <= `ARMLEOCPU_ALU_SELECT_ADD;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_ALU;
                d2e_instr_decode_alu_in0_mux_sel <= `ARMLEOCPU_DECODE_IN0_MUX_SEL_PC;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_IMM_JAL_OFFSET;
            end
            comb_is_lui: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_ALU;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_LUI;
            end
            comb_is_auipc: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_ALU;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_ALU;
                d2e_instr_decode_alu_in0_mux_sel <= `ARMLEOCPU_DECODE_IN0_MUX_SEL_PC;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_CONST4;
                d2e_instr_decode_alu_output_sel <= `ARMLEOCPU_ALU_SELECT_ADD;
            end
            comb_is_branch: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_CONDITIONAL_BRANCH;
                d2e_instr_decode_alu_in0_mux_sel <= `ARMLEOCPU_DECODE_IN0_MUX_SEL_PC;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_IMM_BRANCH_OFFSET;
                d2e_instr_decode_alu_output_sel <= `ARMLEOCPU_ALU_SELECT_ADD;
            end
            comb_is_store: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_STORE;
                d2e_instr_decode_alu_in0_mux_sel <= `ARMLEOCPU_DECODE_IN0_MUX_SEL_RS1;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_IMM_STORE;
                d2e_instr_decode_alu_output_sel <= `ARMLEOCPU_ALU_SELECT_ADD;
            end
            comb_is_load: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_LOAD;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_MEMORY;
                d2e_instr_decode_alu_in0_mux_sel <= `ARMLEOCPU_DECODE_IN0_MUX_SEL_RS1;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_SIMM12;
                d2e_instr_decode_alu_output_sel <= `ARMLEOCPU_ALU_SELECT_ADD;
            end

            /*
            comb_is_load_reserve: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_LOAD_RESERVE;
                d2e_instr_decode_alu_in0_mux_sel <= `ARMLEOCPU_DECODE_IN0_MUX_SEL_RS1;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_ZERO;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_MEMORY;
            end
            comb_is_store_conditional: begin
                d2e_instr_decode_type <= `ARMLOECPU_DECODE_INSTRUCTION_STORE_CONDITIONAL;
                d2e_instr_decode_alu_in0_mux_sel <= `ARMLEOCPU_DECODE_IN0_MUX_SEL_RS1;
                d2e_instr_decode_alu_in1_mux_sel <= `ARMLEOCPU_DECODE_IN1_MUX_SEL_ZERO;
                d2e_rd_sel <= `ARMLEOCPU_DECODE_RD_SEL_STORE_CONDITIONAL_RESULT;
            end
            TODO: Flush
            TODO: Fix TSR, TW and TVM
            TODO: Add CSR Support
            */

            default: begin
                d2e_instr_illegal <= 1;
            end
        endcase
    end
end



/*


wire comb_is_ebreak  = f2d_instr == 32'b000000000001_00000_000_00000_1110011;
wire comb_is_ecall   = f2d_instr == 32'b000000000000_00000_000_00000_1110011;
wire comb_is_wfi     = !csr_mstatus_tw && f2d_instr == 32'b0001000_00101_00000_000_00000_1110011;
wire comb_is_mret    = (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) && f2d_instr == 32'b0011000_00010_00000_000_00000_1110011;
wire comb_is_sret    = ((!csr_mstatus_tsr && csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR) ||
                (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE))
                && comb_is_system && f2d_instr == 32'b0001000_00010_00000_000_00000_1110011;

wire comb_is_sfence_vma = (!csr_mstatus_tvm && (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR)) && comb_is_system && f2d_instr[11:7] == 5'b00000 && f2d_instr[14:12] == 3'b000 && f2d_instr[31:25] == 7'b0001001;
wire comb_is_ifencei = comb_is_fence && f2d_instr[14:12] == 3'b001;
wire comb_is_fence_normal = comb_is_fence && f2d_instr[14:12] == 3'b000;

wire comb_is_cache_flush = comb_is_sfence_vma || comb_is_ifencei || comb_is_fence_normal;

wire comb_is_csrrw_csrrwi = comb_is_system && funct3[1:0] == 2'b01;
wire comb_is_csrs_csrsi   = comb_is_system && funct3[1:0] == 2'b10;
wire comb_is_csrc_csrci   = comb_is_system && funct3[1:0] == 2'b11;






wire comb_is_load_reserve       = comb_is_amo && f2d_instr[31:27] == 5'b00010;
wire comb_is_store_conditional  = comb_is_amo && f2d_instr[31:27] == 5'b00011;


reg d2e_instr_valid_nxt;
always @(posedge clk) begin
    if(!rst_n) begin
        d2e_instr_valid <= 0;
    end else begin
        d2e_instr_valid <= d2e_instr_valid_nxt;
    end
end

always @* begin
    d2e_instr_decode_nxt = d2e_instr_decode;
    if(e2d_ready && 
            (e2d_cmd == `ARMLEOCPU_PIPELINE_CMD_KILL ||
            e2d_cmd == `ARMLEOCPU_PIPELINE_BRANCH) || 
        ) begin
        d2e_instr_valid = 0;
        d2f_ready = 1;
        d2f_cmd = e2d_cmd;
    end else if(e2d_ready && e2d_cmd == `ARMLEOCPU_PIPELINE_CMD_NONE) begin
        d2f_ready = !rs1_stale && !rs2_stale;
        if(!d2f_ready && e2d_ready)
            d2e_instr_valid_nxt = 0;
        d2f_cmd = `ARMLEOCPU_PIPELINE_CMD_NONE;

        d2e_interrupt_pending_nxt = f2d_interrupt_pending;
        d2e_instruction_fetch_exception_nxt = f2d_instruction_fetch_exception;

        d2e_instr_decode_nxt[`DECODE_IS_OP] = comb_is_op;
        d2e_instr_decode_nxt[`DECODE_IS_OP_IMM] = comb_is_op_imm;
        d2e_instr_decode_nxt[`DECODE_IS_JALR] = comb_is_jalr;
        d2e_instr_decode_nxt[`DECODE_IS_JAL] = comb_is_jal;
        d2e_instr_decode_nxt[`DECODE_IS_LUI] = comb_is_lui;
        d2e_instr_decode_nxt[`DECODE_IS_AUIPC] = comb_is_auipc;
        d2e_instr_decode_nxt[`DECODE_IS_BRANCH] = comb_is_branch;
        d2e_instr_decode_nxt[`DECODE_IS_STORE] = comb_is_store;
        d2e_instr_decode_nxt[`DECODE_IS_LOAD] = comb_is_load;

        d2e_instr_decode_nxt[`DECODE_IS_EBREAK] = comb_is_ebreak;
        d2e_instr_decode_nxt[`DECODE_IS_ECALL] = comb_is_ecall;
        d2e_instr_decode_nxt[`DECODE_IS_WFI] = comb_is_wfi;
        d2e_instr_decode_nxt[`DECODE_IS_MRET] = comb_is_mret;
        d2e_instr_decode_nxt[`DECODE_IS_SRET] = comb_is_sret;

        d2e_instr_decode_nxt[`DECODE_CACHE_FLUSH] = comb_is_cache_flush;

        d2e_instr_decode_nxt[`DECODE_IS_CSRRW_CSRRWI] = comb_is_csrrw_csrrwi;
        d2e_instr_decode_nxt[`DECODE_IS_CSRS_CSRSI] = comb_is_csrs_csrsi;
        d2e_instr_decode_nxt[`DECODE_IS_CSRC_CSRCI] = comb_is_csrc_csrci;

        d2e_instr_decode_nxt[`DECODE_IS_MUL] = comb_is_mul;
        d2e_instr_decode_nxt[`DECODE_IS_MULH] = comb_is_mulh;
        d2e_instr_decode_nxt[`DECODE_IS_MULHSU] = comb_is_mulhsu;
        d2e_instr_decode_nxt[`DECODE_IS_MULHU] = comb_is_mulhu;
        
        d2e_instr_decode_nxt[`DECODE_IS_DIV] = comb_is_div;
        d2e_instr_decode_nxt[`DECODE_IS_DIVU] = comb_is_divu;

        d2e_instr_decode_nxt[`DECODE_IS_REM] = comb_is_rem;
        d2e_instr_decode_nxt[`DECODE_IS_REMU] = comb_is_remu;

        d2e_instr_decode_nxt[`DECODE_LOAD_RESERVE] = comb_is_load_reserve;
        d2e_instr_decode_nxt[`DECODE_STORE_CONDITIONAL] = comb_is_store_conditional;
    end
end


reg reseted = 0;
always @(posedge clk) begin
    if(!rst_n) reseted <= 1;
    if(reseted && rst_n) begin
        if(stall || stall_o) begin
            assert(rs1_read == 0);
            assert(rs2_read == 0);
        end
        cover(d2e_instr_valid);
    end
end
*/


endmodule
