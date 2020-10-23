`include "armleocpu_defines.vh"

module armleocpu_execute(
    input clk,
    input rst_n,

// Fetch unit
    input      [31:0]       f2e_instr,
    input                   f2e_instr_valid,
    input      [31:0]       f2e_pc,
    input                   f2e_exc_start,
    input      [1:0]        f2e_exc_privilege,
    input      [31:0]       f2e_epc,
    input      [31:0]       f2e_cause,

    output reg              e2f_ready,
    output reg [`ARMLEOCPU_E2F_CMD_WIDTH-1:0]
                            e2f_cmd,
    output reg [31:0]       e2f_bubble_jump_target,
    output reg [31:0]       e2f_branchtarget,

// To debug unit, indicates that ebreak in machine mode was met
    output reg              e2debug_machine_ebreak,

// Cache interface
    input      [3:0]        c_response,
    input                   c_reset_done,

    output reg [3:0]        c_cmd,
    output reg [31:0]       c_address,
    output     [2:0]        c_load_type,
    input      [31:0]       c_load_data,
    output     [1:0]        c_store_type,
    output     [31:0]       c_store_data,


// CSR Interface for csr class instructions
    output reg [3:0]        csr_cmd,
    output     [11:0]       csr_address,
    input                   csr_invalid,
    input      [31:0]       csr_readdata,
    output reg [31:0]       csr_writedata,

    input      [31:0]       csr_next_pc,
    output reg [31:0]       csr_exc_cause,
    output reg [31:0]       csr_exc_epc,
    output reg [1:0]        csr_exc_privilege,

// CSR Registers
    input      [1:0]        csr_mcurrent_privilege,
    
    input      [15:0]       csr_medeleg,
    

    input                   csr_mstatus_tsr, // sret generates illegal instruction
    input                   csr_mstatus_tvm, // sfence vma and csr satp write generates illegal instruction
    input                   csr_mstatus_tw, // wfi generates illegal instruction

// Regfile
    output     [4:0]        rs1_addr,
    input      [31:0]       rs1_data,

    output     [4:0]        rs2_addr,
    input      [31:0]       rs2_data,
    
    output     [4:0]        rd_addr,
    output reg [31:0]       rd_wdata,
    output reg              rd_write
);

// |------------------------------------------------|
// |              State                             |
// |------------------------------------------------|

reg dcache_command_issued;
reg csr_done;
reg csr_invalid_error;
reg branch_calc_done;
reg branch_taken_reg;

// |------------------------------------------------|
// |              Signals                           |
// |------------------------------------------------|

// Decode opcode
wire [6:0]  opcode                  = f2e_instr[6:0];
assign      rd_addr                 = f2e_instr[11:7];
wire [2:0]  funct3                  = f2e_instr[14:12];
assign      rs1_addr                = f2e_instr[19:15];
assign      rs2_addr                = f2e_instr[24:20];
wire [6:0]  funct7                  = f2e_instr[31:25];
assign      c_load_type             = funct3;
assign      c_store_type            = funct3[1:0];
assign      c_store_data            = rs2_data;

//
//
//
wire sign = f2e_instr[31];

wire [31:0] immgen_simm12 = {{20{sign}}, f2e_instr[31:20]};
wire [31:0] immgen_store_offset = {{20{sign}}, f2e_instr[31:25], f2e_instr[11:7]};
wire [31:0] immgen_branch_offset = {{20{sign}}, f2e_instr[7], f2e_instr[30:25], f2e_instr[11:8], 1'b0};
wire [31:0] immgen_upper_imm = {f2e_instr[31:12], 12'h000};
wire [31:0] immgen_jal_offset = {{12{sign}}, f2e_instr[19:12], f2e_instr[20], f2e_instr[30:25], f2e_instr[24:21], 1'b0};
// 11 + 1 + 6 + 1 + 6 + 4 + 1
wire [31:0] immgen_csr_imm = {27'b0, f2e_instr[19:15]}; // used by csr bit write/set/clear


wire is_op_imm  = opcode == `OPCODE_OP_IMM;
wire is_op      = opcode == `OPCODE_OP;
wire is_jalr    = opcode == `OPCODE_JALR && funct3 == 3'b000;
wire is_jal     = opcode == `OPCODE_JAL;
wire is_lui     = opcode == `OPCODE_LUI;
wire is_auipc   = opcode == `OPCODE_AUIPC;
wire is_branch  = opcode == `OPCODE_BRANCH;
wire is_store   = opcode == `OPCODE_STORE && funct3[2] == 0;
wire is_load    = opcode == `OPCODE_LOAD;
wire is_system  = opcode == `OPCODE_SYSTEM;
wire is_fence   = opcode == `OPCODE_FENCE;

wire is_ebreak  = is_system && f2e_instr == 32'b000000000001_00000_000_00000_1110011;

wire is_ecall   = is_system && f2e_instr == 32'b000000000000_00000_000_00000_1110011;
wire is_wfi     = is_system && f2e_instr == 32'b0001000_00101_00000_000_00000_1110011;
wire is_mret    = is_system && f2e_instr == 32'b0011000_00010_00000_000_00000_1110011;
wire is_sret    = is_system && f2e_instr == 32'b0001000_00010_00000_000_00000_1110011;

wire is_sfence_vma = is_system && f2e_instr[11:7] == 5'b00000 && f2e_instr[14:12] == 3'b000 && f2e_instr[31:25] == 7'b0001001;
wire is_sfence_vma_allowed = !(csr_mstatus_tvm && csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR);
wire is_ifencei = is_fence && f2e_instr[14:12] == 3'b001;

wire is_fence_normal = is_fence && f2e_instr[14:12] == 3'b000;

wire is_csrrw_csrrwi = is_system && funct3[1:0] == 2'b01;
wire is_csrs_csrsi   = is_system && funct3[1:0] == 2'b10;
wire is_csrc_csrci   = is_system && funct3[1:0] == 2'b11;

wire is_csr     = is_csrrw_csrrwi || is_csrs_csrsi || is_csrc_csrci;

wire is_mul         = is_op     && (funct3 == 3'b000) && (funct7 == 7'b0000_001);
wire is_mulh        = is_op     && (funct3 == 3'b001) && (funct7 == 7'b0000_001);
wire is_mulhsu      = is_op     && (funct3 == 3'b010) && (funct7 == 7'b0000_001);
wire is_mulhu       = is_op     && (funct3 == 3'b011) && (funct7 == 7'b0000_001);

wire is_div         = is_op     && (funct3 == 3'b100) && (funct7 == 7'b0000_001);
wire is_divu        = is_op     && (funct3 == 3'b101) && (funct7 == 7'b0000_001);

wire is_rem         = is_op     && (funct3 == 3'b110) && (funct7 == 7'b0000_001);
wire is_remu        = is_op     && (funct3 == 3'b111) && (funct7 == 7'b0000_001);

wire is_flush = is_fence_normal || is_ifencei || (is_sfence_vma && is_sfence_vma_allowed);


wire dcache_response_done = c_response == `CACHE_RESPONSE_DONE;
wire dcache_response_error = (c_response == `CACHE_RESPONSE_MISSALIGNED) || (c_response == `CACHE_RESPONSE_ACCESSFAULT) || (c_response == `CACHE_RESPONSE_PAGEFAULT);

reg dcache_command_issued_nxt;
reg csr_done_nxt;
reg csr_invalid_error_nxt;
reg branch_calc_done_nxt;
reg branch_taken_reg_nxt;
always@(posedge clk) begin
    if(!rst_n) begin
        csr_done <= 0;
        dcache_command_issued <= 0;
        csr_invalid_error <= 0;
        branch_calc_done <= 0;
        branch_taken_reg <= 0;
    end else begin
        csr_done <= csr_done_nxt;
        dcache_command_issued <= dcache_command_issued_nxt;
        csr_invalid_error <= csr_invalid_error_nxt;
        branch_calc_done <= branch_calc_done_nxt;
        branch_taken_reg <= branch_taken_reg_nxt;
    end
end

reg illegal_instruction;
reg dcache_exc;
reg [31:0] dcache_exc_cause;


wire [31:0] alu_result;
wire alu_illegal_instruction;

wire [31:0] pc_plus_4 = f2e_pc + 4;

wire brcond_branchtaken;
wire brcond_illegal_instruction;

reg mul_valid;
reg [31:0] mul_factor0;
reg [31:0] mul_factor1;

wire mul_ready;
wire [63:0] mul_result;
wire [63:0] mul_inverted_result = -mul_result;

reg div_fetch;
reg div_signinvert;
reg [31:0] div_dividend;
reg [31:0] div_divisor;

wire div_ready;
wire div_division_by_zero;
wire [31:0] div_quotient;
wire [31:0] div_remainder;

// |------------------------------------------------|
// |              ALU                               |
// |------------------------------------------------|
armleocpu_alu alu(
    .is_op_imm(is_op_imm),
    .is_op(is_op),

    .funct3(funct3),
    .funct7(funct7),
    .shamt(f2e_instr[24:20]),

    .rs1(rs1_data),
    .rs2(rs2_data),
    
    .simm12(immgen_simm12),

    .result(alu_result),
    .illegal_instruction(alu_illegal_instruction)
);

// |------------------------------------------------|
// |              brcond                               |
// |------------------------------------------------|
armleocpu_brcond brcond(
    .funct3                 (funct3),
    .rs1                    (rs1_data),
    .rs2                    (rs2_data),
    .incorrect_instruction  (brcond_illegal_instruction),
    .branch_taken           (brcond_branchtaken)
);

armleocpu_multiplier multiplier(
    .clk            (clk),
    .rst_n          (rst_n),

    .valid          (mul_valid),

    .factor0        (mul_factor0),
    .factor1        (mul_factor1),

    .ready          (mul_ready),
    .result         (mul_result)
);


armleocpu_unsigned_divider divider(
    .clk                (clk),
    .rst_n              (rst_n),

    .fetch              (div_fetch),

    .dividend           (div_dividend),
    .divisor            (div_divisor),

    .ready              (div_ready),
    .division_by_zero   (div_division_by_zero),
    .quotient           (div_quotient),
    .remainder          (div_remainder)        
);
reg [3:0] rd_sel;
reg mul_signinvert;
`define RD_ALU (4'd0)
`define RD_CSR (4'd1)
`define RD_DCACHE (4'd2)
`define RD_LUI (4'd3)
`define RD_AUIPC (4'd4)
`define RD_PC_PLUS_4 (4'd5)
`define RD_MUL (4'd6)
`define RD_MULH (4'd7)
`define RD_DIV (4'd8)
`define RD_REM (4'd9)
`define RD_RS1 (4'd10)
`define RD_MINUS_ONE (4'd11)

always @* begin
    case(rd_sel)
        `RD_ALU:        rd_wdata = alu_result;
        `RD_CSR:        rd_wdata = csr_readdata;
        `RD_DCACHE:     rd_wdata = c_load_data;
        `RD_LUI:        rd_wdata = immgen_upper_imm;
        `RD_AUIPC:      rd_wdata = f2e_pc + immgen_upper_imm;
        `RD_PC_PLUS_4:  rd_wdata = pc_plus_4;
        `RD_MUL:        rd_wdata = mul_signinvert ? mul_inverted_result[31:0] : mul_result[31:0];
        `RD_MULH:       rd_wdata = mul_signinvert ? mul_inverted_result[63:32] : mul_result[63:32];
        `RD_DIV:        rd_wdata = div_signinvert ? -div_quotient : div_quotient;
        `RD_REM:        rd_wdata = div_signinvert ? -div_remainder : div_remainder;
        `RD_RS1:        rd_wdata = rs1_data;
        `RD_MINUS_ONE:  rd_wdata = -1;
        default:        rd_wdata = alu_result;
    endcase
end

assign csr_address = f2e_instr[31:20];

always @* begin
    illegal_instruction = 0;

    e2f_ready = 1;
    e2f_cmd = `ARMLEOCPU_E2F_CMD_IDLE;
    e2f_bubble_jump_target = csr_next_pc;

    e2f_branchtarget = f2e_pc + immgen_branch_offset;

    e2debug_machine_ebreak = 0;

    c_cmd = `CACHE_CMD_NONE;
    c_address = rs1_data + immgen_simm12;
    

    csr_cmd = `ARMLEOCPU_CSR_CMD_NONE;
    csr_writedata = rs1_data;
    csr_exc_cause = 20;
    csr_exc_epc = f2e_pc;
    csr_exc_privilege = `ARMLEOCPU_PRIVILEGE_USER;


    rd_write = 0;
    mul_signinvert = 0;

    rd_sel = `RD_ALU;
    
    dcache_exc = 0;
    dcache_exc_cause = 0;

    mul_valid = 0;

    mul_factor0 = rs1_data;
    mul_factor1 = rs2_data;

    div_fetch = 0;
    div_signinvert = 0;
    div_dividend = rs1_data;
    div_divisor = rs2_data;

    dcache_command_issued_nxt = 0;
    csr_done_nxt = 0;

    csr_invalid_error_nxt = 0;
    branch_calc_done_nxt = branch_calc_done;
    branch_taken_reg_nxt = brcond_branchtaken;

    if(!c_reset_done)
        e2f_ready = 0;
    else if(f2e_instr_valid)
    case(1)
        is_mul: begin
            e2f_ready = 0;
            mul_signinvert = rs1_data[31] ^ rs2_data[31];
            mul_valid = !mul_ready;
            mul_factor0 = rs1_data[31] ? -rs1_data : rs1_data;
            mul_factor1 = rs2_data[31] ? -rs2_data : rs2_data;
            if(mul_ready) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_MUL;
            end
        end
        is_mulh: begin
            e2f_ready = 0;
            mul_signinvert = rs1_data[31] ^ rs2_data[31];
            mul_valid = !mul_ready;
            mul_factor0 = rs1_data[31] ? -rs1_data : rs1_data;
            mul_factor1 = rs2_data[31] ? -rs2_data : rs2_data;
            if(mul_ready) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_MULH;
            end
        end
        is_mulhu: begin
            e2f_ready = 0;
            mul_signinvert = 0;
            mul_valid = !mul_ready;
            mul_factor0 = rs1_data;
            mul_factor1 = rs2_data;
            if(mul_ready) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_MULH;
            end
        end
        is_mulhsu: begin
            e2f_ready = 0;
            mul_signinvert = rs1_data[31];
            mul_valid = !mul_ready;
            mul_factor0 = rs1_data[31] ? -rs1_data : rs1_data;
            mul_factor1 = rs2_data;
            if(mul_ready) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_MULH;
            end
        end
        is_divu: begin
            e2f_ready = 0;
            div_fetch = !div_ready;
            div_signinvert = 0;
            div_dividend = rs1_data;
            div_divisor = rs2_data;
            if(div_ready && div_division_by_zero) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_MINUS_ONE;
            end else if(div_ready) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_DIV;
            end
        end
        is_div: begin
            e2f_ready = 0;
            div_fetch = !div_ready;
            div_signinvert = rs1_data[31] ^ rs2_data[31];
            div_dividend = rs1_data[31] ? -rs1_data : rs1_data;
            div_divisor = rs2_data[31] ? -rs2_data : rs2_data;
            if(div_ready && div_division_by_zero) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_MINUS_ONE;
            end else if(div_ready) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_DIV;
            end
        end
        is_remu: begin
            e2f_ready = 0;
            div_fetch = !div_ready;
            div_signinvert = 0;
            div_dividend = rs1_data;
            div_divisor = rs2_data;
            if(div_ready && div_division_by_zero) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_RS1;
            end else if(div_ready) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_REM;
            end
        end
        is_rem: begin
            e2f_ready = 0;
            div_fetch = !div_ready;
            div_signinvert = rs1_data[31];
            div_dividend = rs1_data[31] ? -rs1_data : rs1_data;
            div_divisor = rs2_data[31] ? -rs2_data : rs2_data;
            if(div_ready && div_division_by_zero) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_RS1;
            end else if(div_ready) begin
                e2f_ready = 1;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_REM;
            end
        end
        is_op_imm, is_op: begin
            rd_write = (rd_addr != 0);
            rd_sel = `RD_ALU;
            illegal_instruction = alu_illegal_instruction;
            e2f_ready = 1;
        end
        is_jal: begin
            e2f_cmd = `ARMLEOCPU_E2F_CMD_BRANCHTAKEN;
            e2f_branchtarget = f2e_pc + immgen_jal_offset;
            rd_write = (rd_addr != 0);
            rd_sel = `RD_PC_PLUS_4;
            e2f_ready = 1;
        end
        is_jalr: begin
            e2f_ready = 1;
            e2f_cmd = `ARMLEOCPU_E2F_CMD_BRANCHTAKEN;
            e2f_branchtarget = rs1_data + immgen_simm12;
            rd_write = (rd_addr != 0);
            rd_sel = `RD_PC_PLUS_4;
        end
        is_branch: begin
            e2f_ready = 0;
            e2f_branchtarget = f2e_pc + immgen_branch_offset;
            if(brcond_illegal_instruction) begin
                illegal_instruction = 1;
                e2f_ready = 1;
            end else if(!branch_calc_done) begin
                e2f_ready = 0;
                branch_calc_done_nxt = 1;
                e2f_cmd = `ARMLEOCPU_E2F_CMD_IDLE;
                // branch_taken_reg is recorded in this cycle
            end else if (branch_calc_done) begin
                e2f_ready = 1;
                // if branch_taken_reg which contains result of previous cycle is set, then branch is taken
                e2f_cmd = branch_taken_reg ? `ARMLEOCPU_E2F_CMD_BRANCHTAKEN : `ARMLEOCPU_E2F_CMD_IDLE;
                branch_calc_done_nxt = 0;
            end
        end
        is_lui: begin
            e2f_ready = 1;
            rd_write = (rd_addr != 0);
            rd_sel = `RD_LUI;
        end
        is_auipc: begin
            e2f_ready = 1;
            rd_write = (rd_addr != 0);
            rd_sel = `RD_AUIPC;
        end
        is_load: begin
            e2f_ready = 0;
            c_address = rs1_data + immgen_simm12;
            c_cmd = `CACHE_CMD_LOAD;
            dcache_command_issued_nxt = 1;
            rd_sel = `RD_DCACHE;
            if(!dcache_command_issued) begin
                
            end else begin
                if(dcache_response_done) begin
                    rd_write = (rd_addr != 0);
                    c_cmd = `CACHE_CMD_NONE;
                    e2f_ready = 1;
                    dcache_command_issued_nxt = 0;
                end else if(dcache_response_error) begin
                    dcache_exc = 1;
                    e2f_ready = 1;
                    c_cmd = `CACHE_CMD_NONE;
                    if(c_response == `CACHE_RESPONSE_MISSALIGNED)
                        dcache_exc_cause = `EXCEPTION_CODE_LOAD_ADDRESS_MISALIGNED;
                    else if(c_response == `CACHE_RESPONSE_PAGEFAULT)
                        dcache_exc_cause = `EXCEPTION_CODE_LOAD_PAGE_FAULT;
                    else if(c_response == `CACHE_RESPONSE_ACCESSFAULT)
                        dcache_exc_cause = `EXCEPTION_CODE_LOAD_ACCESS_FAULT;
                    dcache_command_issued_nxt = 0;
                end
            end
        end
        is_store: begin
            c_address = rs1_data + immgen_store_offset;
            c_cmd = `CACHE_CMD_STORE;
            dcache_command_issued_nxt = 1;
            e2f_ready = 0;
            if(!dcache_command_issued) begin
                c_cmd = `CACHE_CMD_STORE;
            end else if(dcache_command_issued) begin
                c_cmd = `CACHE_CMD_STORE;
                if(dcache_response_done) begin
                    dcache_command_issued_nxt = 0;
                    e2f_ready = 1;
                    c_cmd = `CACHE_CMD_NONE;
                end else if(dcache_response_error) begin
                    dcache_command_issued_nxt = 0;
                    dcache_exc = 1;
                    e2f_ready = 1;
                    c_cmd = `CACHE_CMD_NONE;
                    if(c_response == `CACHE_RESPONSE_MISSALIGNED)
                        dcache_exc_cause = `EXCEPTION_CODE_STORE_ADDRESS_MISALIGNED;
                    else if(c_response == `CACHE_RESPONSE_PAGEFAULT)
                        dcache_exc_cause = `EXCEPTION_CODE_STORE_PAGE_FAULT;
                    else if(c_response == `CACHE_RESPONSE_ACCESSFAULT)
                        dcache_exc_cause = `EXCEPTION_CODE_STORE_ACCESS_FAULT;
                end
            end
        end
        is_flush: begin
            dcache_command_issued_nxt = 1;
            e2f_ready = 0;
            if(!dcache_command_issued) begin
                c_cmd = `CACHE_CMD_FLUSH_ALL;
            end else if(dcache_command_issued) begin
                c_cmd = `CACHE_CMD_FLUSH_ALL;
                if(dcache_response_done) begin
                    e2f_cmd = `ARMLEOCPU_E2F_CMD_FLUSH;
                    e2f_ready = 1;
                    c_cmd = `CACHE_CMD_NONE;
                    dcache_command_issued_nxt = 0;
                end
            end
        end
        is_system: begin
            if(is_csr) begin
                if(!csr_done) begin
                    // CSR NOT DONE
                    csr_done_nxt = 1;
                    if(funct3[2]) // IMM CSR
                        csr_writedata = immgen_csr_imm;
                    else // RS1 DATA
                        csr_writedata = rs1_data;
                    
                    if(is_csrrw_csrrwi) begin
                        if(rd_addr != 0)
                            csr_cmd = `ARMLEOCPU_CSR_CMD_WRITE;
                        else
                            csr_cmd = `ARMLEOCPU_CSR_CMD_READ_WRITE;
                    end else if(is_csrs_csrsi) begin
                        if(rs1_addr == 0)
                            csr_cmd = `ARMLEOCPU_CSR_CMD_READ;
                        else
                            csr_cmd = `ARMLEOCPU_CSR_CMD_READ_SET;
                    end else if(is_csrc_csrci) begin
                        if(rs1_addr == 0)
                            csr_cmd = `ARMLEOCPU_CSR_CMD_READ;
                        else
                            csr_cmd = `ARMLEOCPU_CSR_CMD_READ_CLEAR;
                    end
                    rd_write = (rd_addr != 0) && !csr_invalid;
                    rd_sel = `RD_CSR;
                    e2f_ready = 0;
                    csr_invalid_error_nxt = csr_invalid;
                    
                end else begin
                    // CSR_DONE
                    e2f_ready = 1;
                    csr_done_nxt = 0;
                    if(csr_invalid_error) begin
                        illegal_instruction = 1;
                    end
                end
            end else if(is_ecall) begin
                e2f_cmd = `ARMLEOCPU_E2F_CMD_BUBBLE_JUMP;
                csr_cmd = `ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN;
                csr_exc_cause = `EXCEPTION_CODE_ILLEGAL_INSTRUCTION;
                if(csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE)
                    csr_exc_cause = `EXCEPTION_CODE_MCALL;
                else if(csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR)
                    csr_exc_cause = `EXCEPTION_CODE_SCALL;
                else if(csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_USER) begin
                    csr_exc_cause = `EXCEPTION_CODE_UCALL;
                end
                csr_exc_privilege = csr_medeleg[csr_exc_cause] ? `ARMLEOCPU_PRIVILEGE_SUPERVISOR : `ARMLEOCPU_PRIVILEGE_MACHINE;
                e2f_ready = 1;
            end else if(is_ebreak) begin
                e2f_cmd = `ARMLEOCPU_E2F_CMD_IDLE;
                if(csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) begin
                    e2debug_machine_ebreak = 1;
                end else begin
                    e2f_cmd = `ARMLEOCPU_E2F_CMD_BUBBLE_JUMP;
                    csr_cmd = `ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN;
                    csr_exc_cause = `EXCEPTION_CODE_BREAKPOINT;
                    csr_exc_privilege = csr_medeleg[csr_exc_cause] ? `ARMLEOCPU_PRIVILEGE_SUPERVISOR : `ARMLEOCPU_PRIVILEGE_MACHINE;
                end
                e2f_ready = 1;
            end else if(is_wfi && !csr_mstatus_tw) begin
                // Implement it as NOP
                e2f_ready = 1;
            end else if(is_mret && (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE)) begin
                csr_cmd = `ARMLEOCPU_CSR_CMD_MRET;
                e2f_cmd = `ARMLEOCPU_E2F_CMD_BUBBLE_JUMP;
                e2f_ready = 1;
            end else if(is_sret &&
                !(csr_mstatus_tsr && csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR) ||
                (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE)) begin
                csr_cmd = `ARMLEOCPU_CSR_CMD_SRET;
                e2f_cmd = `ARMLEOCPU_E2F_CMD_BUBBLE_JUMP;
                e2f_ready = 1;
            end else begin
                illegal_instruction = 1;
            end
        end
        default: begin
            illegal_instruction = 1;
        end
    endcase

    csr_exc_cause = f2e_cause;
    csr_exc_epc = f2e_epc;
    if(f2e_exc_start) begin
        e2f_ready = 1;
        csr_cmd = `ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN;
        csr_exc_cause = f2e_cause;
        csr_exc_epc = f2e_epc;
        csr_exc_privilege = f2e_exc_privilege;
    end else if(dcache_exc) begin
        e2f_ready = 1;
        e2f_cmd = `ARMLEOCPU_E2F_CMD_BUBBLE_JUMP;
        // e2f_bubble_exc_start_target = csr_next_pc;
        csr_cmd = `ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN;
        csr_exc_cause = dcache_exc_cause;
        csr_exc_epc = f2e_pc;
        csr_exc_privilege = 
            (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) ? `ARMLEOCPU_PRIVILEGE_MACHINE : csr_medeleg[csr_exc_cause] ? `ARMLEOCPU_PRIVILEGE_SUPERVISOR : `ARMLEOCPU_PRIVILEGE_MACHINE;
    end else if(illegal_instruction) begin
        e2f_ready = 1;
        e2f_cmd = `ARMLEOCPU_E2F_CMD_BUBBLE_JUMP;
        // e2f_bubble_exc_start_target = csr_next_pc;
        csr_cmd = `ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN;
        csr_exc_cause = `EXCEPTION_CODE_ILLEGAL_INSTRUCTION;
        csr_exc_epc = f2e_pc;
        csr_exc_privilege = (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) ? `ARMLEOCPU_PRIVILEGE_MACHINE : csr_medeleg[csr_exc_cause] ? `ARMLEOCPU_PRIVILEGE_SUPERVISOR : `ARMLEOCPU_PRIVILEGE_MACHINE;;
    end
end


`ifdef DEBUG_EXECUTE
always @(posedge clk) begin
    if(!rst_n) begin
        dcache_command_issued <= 0;
        csr_done <= 0;
    end else if(c_reset_done && f2e_instr_valid) begin
        if(illegal_instruction) begin
            $display("[%m][%d][Execute] Illegal instruction, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
        end else begin
        case(1)
            is_mulhu, is_mul, is_mulh, is_mulhsu: begin
                if(mul_ready) begin
                    $display("[%m][%d][Execute] MUL instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X, rs1_data = 0x%X, rs2_data = 0x%X, rd_wdata = 0x%X, rd_write = %d", $time, f2e_instr, f2e_pc, rs1_data, rs2_data, rd_wdata, rd_write);
                end
            end
            is_div, is_divu: begin
                if(div_ready) begin
                    $display("[%m][%d][Execute] DIV instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X, rs1_data = 0x%X, rs2_data = 0x%X, rd_wdata = 0x%X, rd_write = %d", $time, f2e_instr, f2e_pc, rs1_data, rs2_data, rd_wdata, rd_write);
                end
            end
            is_rem, is_remu: begin
                if(div_ready) begin
                    $display("[%m][%d][Execute] REM instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X, rs1_data = 0x%X, rs2_data = 0x%X, rd_wdata = 0x%X, rd_write = %d", $time, f2e_instr, f2e_pc, rs1_data, rs2_data, rd_wdata, rd_write);
                end
            end
            is_op, is_op_imm: begin
                if(alu_illegal_instruction) begin
                    $display("[%m][%d][Execute] ALU Illegal instruction, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                end
                if(is_op) begin
                    $display("[%m][%d][Execute] ALU instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, rs1_data = 0x%X, rs2_data = 0x%X, rd_wdata = 0x%X, rd_write = %d", $time, f2e_instr, f2e_pc, rs1_data, rs2_data, rd_wdata, rd_write);
                end
                if(is_op_imm) begin
                    $display("[%m][%d][Execute] ALU IMM instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, rs1_data = 0x%X, immgen_simm12 = 0x%X, rd_wdata = 0x%X, rd_write = %d", $time, f2e_instr, f2e_pc, rs1_data, immgen_simm12, rd_wdata, rd_write);
                end
            end
            is_jal: $display("[%m][%d][Execute] JAL instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, e2f_branchtarget = 0x%X, immgen_jal_offset = 0x%X", $time, f2e_instr, f2e_pc, e2f_branchtarget, immgen_jal_offset);
            is_jalr: $display("[%m][%d][Execute] JALR instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, e2f_branchtarget = 0x%X", $time, f2e_instr, f2e_pc, e2f_branchtarget);
            is_branch:
                if(!brcond_illegal_instruction) begin
                    if(!branch_calc_done)
                        $display("[%m][%d][Execute] Branch calculation, f2e_instr = 0x%X, f2e_pc = 0x%X, e2f_branchtarget = 0x%X, rs1_data = 0x%X, rs2_data = 0x%X", $time, f2e_instr, f2e_pc, e2f_branchtarget, rs1_data, rs2_data);
                    else
                        if(e2f_cmd == `ARMLEOCPU_E2F_CMD_BRANCHTAKEN) begin
                            $display("[%m][%d][Execute] Branch taken, f2e_instr = 0x%X, f2e_pc = 0x%X, e2f_branchtarget = 0x%X, rs1_data = 0x%X, rs2_data = 0x%X", $time, f2e_instr, f2e_pc, e2f_branchtarget, rs1_data, rs2_data);
                        end else begin
                            $display("[%m][%d][Execute] Branch not taken, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                        end
                end

            is_lui: $display("[%m][%d][Execute] LUI instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, rd_wdata = 0x%X", $time, f2e_instr, f2e_pc, rd_wdata);
            is_auipc: $display("[%m][%d][Execute] AUIPC instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, rd_wdata = 0x%X", $time, f2e_instr, f2e_pc, rd_wdata);
            is_load: begin
                if(!dcache_command_issued) begin
                    $display("[%m][%d][Execute] Load instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, c_address = 0x%X", $time, f2e_instr, f2e_pc, c_address);
                end else begin
                    if(dcache_response_done) begin
                        $display("[%m][%d][Execute] Load instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X, c_address = 0x%X, c_response = 0x%X, c_load_type = 0x%X, c_load_data = 0x%X", $time, f2e_instr, f2e_pc, c_address, c_response, c_load_type, c_load_data);
                    end else if(dcache_response_error) begin
                        $display("[%m][%d][Execute] Load instruction, error, f2e_instr = 0x%X, f2e_pc = 0x%X, c_address = 0x%X, c_response = 0x%X, c_load_type = 0x%X", $time, f2e_instr, f2e_pc, c_address, c_response, c_load_type);
                    end
                end
            end
            is_store: begin
                if(!dcache_command_issued) begin
                    $display("[%m][%d][Execute] Store instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, c_store_type = 0x%X", $time, f2e_instr, f2e_pc, c_store_type);
                end else begin
                    if(dcache_response_done) begin
                        $display("[%m][%d][Execute]  Store instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X, c_address = 0x%X, c_response = 0x%X, c_store_type = 0x%X, c_store_data = 0x%X", $time, f2e_instr, f2e_pc, c_address, c_response, c_store_type, c_store_data);
                    end else if(dcache_response_error) begin
                        $display("[%m][%d][Execute] Store instruction, error, f2e_instr = 0x%X, f2e_pc = 0x%X, c_address = 0x%X, c_response = 0x%X, c_load_type = 0x%X", $time, f2e_instr, f2e_pc, c_address, c_response, c_store_type);
                    end
                end
            end
            is_flush: begin
                if(!dcache_command_issued) begin
                    $display("[%m][%d][Execute] Fence instruction, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                end else if(dcache_command_issued) begin
                    if(dcache_response_done) begin
                        $display("[%m][%d][Execute] Fence instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                    end
                end
            end
            // TODO: Improve, add more debugged signals
            is_system: begin
                case(1)
                    is_csr && !csr_invalid: $display("[%m][%d][Execute] CSR instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                    is_ecall: $display("[%m][%d][Execute] ECALL instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                    is_ebreak: $display("[%m][%d][Execute] EBREAK instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                    is_wfi && !csr_mstatus_tsr: $display("[%m][%d][Execute] WFI instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                    is_mret && (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE):
                        $display("[%m][%d][Execute] MRET instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                    is_sret &&
                        (csr_mstatus_tsr && csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR) ||
                        (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE): begin
                            $display("[%m][%d][Execute] SRET instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                        end
                endcase
            end
        endcase
        end
        if(!illegal_instruction && !dcache_exc && f2e_exc_start)
            if(f2e_instr != ({12'h0, 5'h0, 3'b000, 5'h0, 7'b00_100_11})) begin
                $error("[%m][%d] Instruction is not NOP when exc_start is 1", $time);
            end
    end
end
`endif

endmodule