////////////////////////////////////////////////////////////////////////////////
// 
// This file is part of ArmleoCPU.
// ArmleoCPU is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// ArmleoCPU is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with ArmleoCPU.  If not, see <https://www.gnu.org/licenses/>.
// 
// Copyright (C) 2016-2021, Arman Avetisyan, see COPYING file or LICENSE file
// SPDX-License-Identifier: GPL-3.0-or-later
// 

`include "armleocpu_defines.vh"

module armleocpu_execute(
    input clk,
    input rst_n,

    output reg              dbg_pipeline_busy,

    input [31:0]            rs1_rdata,
    input [31:0]            rs2_rdata,

    // Decode to execute interface
    input wire              d2e_valid,
    input wire [`F2E_TYPE_WIDTH-1:0]
                            d2e_type,
    input wire [31:0]       d2e_instr,
    input wire [31:0]       d2e_pc,
    input wire  [3:0]       d2e_resp, // Cache response

    output reg              e2d_ready,
    output reg [`ARMLEOCPU_E2F_CMD_WIDTH-1:0]
                            e2d_cmd,
    output reg [31:0]       e2d_branchtarget,

    output reg              e2d_rd_write,
    output reg  [4:0]       e2d_rd_waddr,


// Cache interface
    input      [3:0]        c_response,
    input                   c_reset_done,

    output reg [3:0]        c_cmd,
    output reg [31:0]       c_address,
    output     [2:0]        c_load_type,
    input      [31:0]       c_load_data,
    output     [1:0]        c_store_type,
    output     [31:0]       c_store_data,


// To debug unit, indicates that ebreak was met
// If this is asserted then next cycle dbg_mode goes high
// Also: e2d branch taken is issued with current pc + 4
//      This makes sure that fetch will start by next instruction
//      And that reading csr_dpc contains valid data

    output reg              e2debug_machine_ebreak,
    output reg              e2debug_supervisor_ebreak,
    output reg              e2debug_user_ebreak,

    input  wire             debug2e_machine_ebreak_traps,
    input  wire             debug2e_supervisor_ebreak_traps,
    input  wire             debug2e_user_ebreak_traps,
    

// CSR Interface for csr class instructions
    output reg [3:0]        csr_cmd,
    output     [11:0]       csr_address,
    input                   csr_invalid,
    input      [31:0]       csr_to_rd,
    output reg [31:0]       csr_from_rs,

    input      [31:0]       csr_next_pc,
    output reg [3:0]        csr_icache_resp,
    output reg [3:0]        csr_dcache_resp,
    
    

// CSR Registers
    input      [1:0]        csr_mcurrent_privilege,
    
    input      [15:0]       csr_medeleg,
    

    input                   csr_mstatus_tsr, // sret generates illegal instruction
    input                   csr_mstatus_tvm, // sfence vma and csr satp write generates illegal instruction
    input                   csr_mstatus_tw, // wfi generates illegal instruction

);

assign csr_address = f2d_instr[31:20];

// |------------------------------------------------|
// |              State                             |
// |------------------------------------------------|


// |------------------------------------------------|
// |              Signals                           |
// |------------------------------------------------|

// Decode opcode
wire [6:0]  opcode                  = f2d_instr[6:0];
assign      rd_addr                 = f2d_instr[11:7];
wire [2:0]  funct3                  = f2d_instr[14:12];
wire [6:0]  funct7                  = f2d_instr[31:25];
assign      c_load_type             = funct3;
assign      c_store_type            = funct3[1:0];
assign      c_store_data            = rs2_data;


wire sign = f2d_instr[31];

wire [31:0] immgen_simm12 = {{20{sign}}, f2d_instr[31:20]};
wire [31:0] immgen_store_offset = {{20{sign}}, f2d_instr[31:25], f2d_instr[11:7]};
wire [31:0] immgen_branch_offset = {{20{sign}}, f2d_instr[7], f2d_instr[30:25], f2d_instr[11:8], 1'b0};
wire [31:0] immgen_upper_imm = {f2d_instr[31:12], 12'h000};
wire [31:0] immgen_jal_offset = {{12{sign}}, f2d_instr[19:12], f2d_instr[20], f2d_instr[30:25], f2d_instr[24:21], 1'b0};
// 11 + 1 + 6 + 1 + 6 + 4 + 1
wire [31:0] immgen_csr_imm = {27'b0, f2d_instr[19:15]}; // used by csr bit write/set/clear



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

wire is_ebreak  = is_system && f2d_instr == 32'b000000000001_00000_000_00000_1110011;

wire is_ecall   = is_system && f2d_instr == 32'b000000000000_00000_000_00000_1110011;
wire is_wfi     = is_system && f2d_instr == 32'b0001000_00101_00000_000_00000_1110011;
wire is_mret    = is_system && f2d_instr == 32'b0011000_00010_00000_000_00000_1110011;
wire is_sret    = is_system && f2d_instr == 32'b0001000_00010_00000_000_00000_1110011;

wire is_sfence_vma = is_system && f2d_instr[11:7] == 5'b00000 && f2d_instr[14:12] == 3'b000 && f2d_instr[31:25] == 7'b0001001;
wire is_sfence_vma_allowed = !(csr_mstatus_tvm && csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR);
wire is_ifencei = is_fence && f2d_instr[14:12] == 3'b001;

wire is_fence_normal = is_fence && f2d_instr[14:12] == 3'b000;

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



wire [31:0] pc_plus_4 = f2e_pc + 4;



// |------------------------------------------------|
// |              ALU                               |
// |------------------------------------------------|


wire [31:0] alu_result;
wire alu_illegal_instruction;


armleocpu_alu alu(
    .is_op_imm(is_op_imm),
    .is_op(is_op),

    .funct3(funct3),
    .funct7(funct7),
    .shamt(f2d_instr[24:20]),

    .rs1(rs1_data),
    .rs2(rs2_data),
    
    .simm12(immgen_simm12),

    .result(alu_result),
    .illegal_instruction(alu_illegal_instruction)
);

// |------------------------------------------------|
// |              brcond                            |
// |------------------------------------------------|

wire brcond_branchtaken;
wire brcond_illegal_instruction;


armleocpu_brcond brcond(
    .funct3                 (funct3),
    .rs1                    (rs1_data),
    .rs2                    (rs2_data),
    .incorrect_instruction  (brcond_illegal_instruction),
    .branch_taken           (brcond_branchtaken)
);

// |------------------------------------------------|
// |              MUL                               |
// |------------------------------------------------|

reg mul_valid;
reg [31:0] mul_factor0;
reg [31:0] mul_factor1;

wire mul_ready;
wire [63:0] mul_result;
wire [63:0] mul_inverted_result = -mul_result;


armleocpu_multiplier multiplier(
    .clk            (clk),
    .rst_n          (rst_n),

    .valid          (mul_valid),

    .factor0        (mul_factor0),
    .factor1        (mul_factor1),

    .ready          (mul_ready),
    .result         (mul_result)
);



// |------------------------------------------------|
// |              divider                           |
// |------------------------------------------------|

reg div_fetch;
reg div_signinvert;
reg [31:0] div_dividend;
reg [31:0] div_divisor;

wire div_ready;
wire div_division_by_zero;
wire [31:0] div_quotient;
wire [31:0] div_remainder;


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


reg illegal_instruction;



reg [3:0] rd_sel;
reg mul_signinvert;
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


always @* begin
    if(f2e_instr_valid)
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
            rd_write = (rd_addr != 0) && !(|e2f_branchtarget[1:0]);
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

    endcase
end



always @(posedge clk) begin
    if(!rst_n) begin

    end else begin

    end
end


endmodule

`include "armleocpu_undef.vh"

