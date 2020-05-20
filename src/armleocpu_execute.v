module armleocpu_execute(
    input clk,
    input rst_n,

// Fetch unit
    input      [31:0]       f2e_instr,
    input      [31:0]       f2e_pc,
    input                   f2e_exc_start,
    input      [31:0]       f2e_cause,

    output reg              e2f_ready,
    output reg              e2f_exc_start,
    output reg              e2f_exc_return,
    output reg [31:0]       e2f_exc_epc,
    output reg              e2f_flush,
    output reg              e2f_branchtaken,
    output reg [31:0]       e2f_branchtarget,

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



// CSR Interface for exceptions
    
    output reg [1:0]        csr_exc_cmd, //  Exception start, mret, sret
    output reg [31:0]       csr_exc_cause,
    output     [31:0]       csr_exc_epc, //  Exception start pc

// CSR Interface for csr class instructions
    output reg [2:0]        csr_cmd, // NONE, WRITE, READ, READ_WRITE, 
    output     [11:0]       csr_address,
    input                   csr_invalid,
    input      [31:0]       csr_readdata,
    output reg [31:0]       csr_writedata,

// CSR Registers
    input      [1:0]        csr_mcurrent_privilege,
    input      [31:0]       csr_mepc,
    input      [31:0]       csr_sepc,
    
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

`include "armleocpu_cache.inc"
`include "armleocpu_instructions.inc"
`include "armleocpu_exception.inc"
`include "armleocpu_privilege.inc"
`include "armleocpu_csr.inc"

parameter MULDIV_ENABLED = 1;

// |------------------------------------------------|
// |              State                             |
// |------------------------------------------------|

reg dcache_command_issued;
reg csr_done;
reg deferred_illegal_instruction;

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


assign      csr_exc_epc             = f2e_pc;

wire is_op_imm  = opcode == `OPCODE_OP_IMM;
wire is_op      = opcode == `OPCODE_OP;
wire is_jalr    = opcode == `OPCODE_JALR;
wire is_jal     = opcode == `OPCODE_JAL;
wire is_lui     = opcode == `OPCODE_LUI;
wire is_auipc   = opcode == `OPCODE_AUIPC;
wire is_branch  = opcode == `OPCODE_BRANCH;
wire is_store   = opcode == `OPCODE_STORE;
wire is_load    = opcode == `OPCODE_LOAD;
wire is_system  = opcode == `OPCODE_SYSTEM;
wire is_fence   = opcode == `OPCODE_FENCE;

wire is_ebreak  = f2e_instr == 32'b000000000001_00000_000_00000_1110011;

wire dcache_response_done = c_response == `CACHE_RESPONSE_DONE;
wire dcache_response_error = (c_response == `CACHE_RESPONSE_MISSALIGNED) || (c_response == `CACHE_RESPONSE_ACCESSFAULT) || (c_response == `CACHE_RESPONSE_PAGEFAULT);
// TODO:

reg illegal_instruction;
reg dcache_exc;
reg [31:0] dcache_exc_cause;


wire [31:0] alu_result;
wire alu_illegal_instruction;

wire [31:0] pc_plus_4 = f2e_pc + 4;

wire brcond_branchtaken;
wire brcond_illegal_instruction;
// |------------------------------------------------|
// |              ALU                               |
// |------------------------------------------------|
armleocpu_alu #(
    .MULDIV_ENABLED(MULDIV_ENABLED)
) alu(
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
    .funct3(funct3),
    .rs1(rs1_data),
    .rs2(rs2_data),
    .incorrect_instruction(brcond_illegal_instruction),
    .branch_taken(brcond_branchtaken)
);



reg [2:0] rd_sel;

`define RD_ALU (3'd0)
`define RD_CSR (3'd1)
`define RD_DCACHE (3'd2)
`define RD_LUI (3'd3)
`define RD_AUIPC (3'd4)
`define RD_PC_PLUS_4 (3'd5)

always @* begin
    case(rd_sel)
        `RD_ALU:        rd_wdata = alu_result;
        `RD_CSR:        rd_wdata = csr_readdata;
        `RD_DCACHE:     rd_wdata = c_load_data;
        `RD_LUI:        rd_wdata = immgen_upper_imm;
        `RD_AUIPC:      rd_wdata = f2e_pc + immgen_upper_imm;
        `RD_PC_PLUS_4:  rd_wdata = pc_plus_4;
        default:        rd_wdata = alu_result;
    endcase
end



always @* begin
    illegal_instruction = 0;
    e2f_exc_start = 0;
    e2f_exc_return = 0;
    e2f_exc_epc = 0;
    e2f_ready = 1;
    e2f_flush = 0;
    e2f_branchtarget = f2e_pc + immgen_branch_offset;
    e2f_branchtaken = 0;

    e2debug_machine_ebreak = 0;

    c_cmd = `CACHE_CMD_NONE;
    c_address = rs1_data + immgen_simm12;
    
    csr_exc_cmd = `CSR_EXC_NONE;
    csr_exc_cause = 0;

    csr_cmd = `CSR_CMD_NONE;
    csr_writedata = 0;

    rd_write = 0;
    rd_sel = `RD_ALU;
    
    dcache_exc = 0;
    dcache_exc_cause = 0;

    case(1)
        is_op_imm, is_op: begin
            rd_write = (rd_addr != 0);
            rd_sel = `RD_ALU;
            illegal_instruction = alu_illegal_instruction;
            e2f_ready = 1;
        end
        is_jal: begin
            e2f_branchtaken = 1;
            e2f_branchtarget = f2e_pc + immgen_jal_offset;
            rd_write = (rd_addr != 0);
            rd_sel = `RD_PC_PLUS_4;
            e2f_ready = 1;
        end
        is_jalr: begin
            e2f_ready = 1;
            if(funct3 != 0) begin
                illegal_instruction = 1;
            end else begin
                e2f_branchtaken = 1;
                e2f_branchtarget = rs1_data + immgen_simm12;
                rd_write = (rd_addr != 0);
                rd_sel = `RD_PC_PLUS_4;
            end
        end
        is_branch: begin
            e2f_ready = 1;
            if(brcond_illegal_instruction) begin
                illegal_instruction = 1;
            end else begin
                e2f_branchtaken = brcond_branchtaken;
                e2f_branchtarget = f2e_pc + immgen_branch_offset;
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
            if(!dcache_command_issued) begin
                
            end else begin
                if(dcache_response_done) begin
                    rd_sel = `RD_DCACHE;
                    rd_write = (rd_addr != 0);
                    c_cmd = `CACHE_CMD_NONE;
                    e2f_ready = 1;
                end else if(dcache_response_error) begin
                    dcache_exc = 1;
                    e2f_ready = 1;
                    e2f_exc_start = 1;
                    c_cmd = `CACHE_CMD_NONE;
                    if(c_response == `CACHE_RESPONSE_MISSALIGNED)
                        dcache_exc_cause = `EXCEPTION_CODE_LOAD_ADDRESS_MISALIGNED;
                    else if(c_response == `CACHE_RESPONSE_PAGEFAULT)
                        dcache_exc_cause = `EXCEPTION_CODE_LOAD_PAGE_FAULT;
                    else if(c_response == `CACHE_RESPONSE_ACCESSFAULT)
                        dcache_exc_cause = `EXCEPTION_CODE_LOAD_ACCESS_FAULT;
                    
                end
            end
        end
        is_store: begin
            c_address = rs1_data + immgen_store_offset;
            c_cmd = `CACHE_CMD_STORE;
            if(funct3[2] != 0) begin
                c_cmd = `CACHE_CMD_NONE;
                illegal_instruction = 1;
                e2f_ready = 1;
            end else if(!dcache_command_issued) begin
                c_cmd = `CACHE_CMD_STORE;
                e2f_ready = 0;
            end else if(dcache_command_issued) begin
                e2f_ready = 0;
                c_cmd = `CACHE_CMD_STORE;
                if(dcache_response_done) begin
                    e2f_ready = 1;
                    c_cmd = `CACHE_CMD_NONE;
                end else if(dcache_response_error) begin
                    dcache_exc = 1;
                    e2f_ready = 1;
                    e2f_exc_start = 1;
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
        is_fence: begin
            // Not implemented, just yet
            if(f2e_instr[31:28] == 4'b0000) begin
                if(!dcache_command_issued) begin
                    c_cmd = `CACHE_CMD_FLUSH_ALL;
                    e2f_ready = 0;
                end else if(dcache_command_issued) begin
                    e2f_ready = 0;
                    c_cmd = `CACHE_CMD_FLUSH_ALL;
                    if(dcache_response_done) begin
                        e2f_flush = 1;
                        e2f_ready = 1;
                        c_cmd = `CACHE_CMD_NONE;
                    end
                end
            end else begin
                illegal_instruction = 1;
            end
        end
        is_system: begin
            //illegal_instruction = 1;
            if(is_ebreak) begin
                e2f_ready = 0;
                e2debug_machine_ebreak = 1;
            end
            // Just temporary thing, pause on ebreak, for testing purposes

            // Handle CSR but with 1 cycle delay

            // TODO: Handle EBREAK, ECALL
            /*if(is_ecall) begin
                csr_exc_start = 1;
                if(csr_mcurrent_privilege == `armleocpu_PRIVILEGE_MACHINE)
                    csr_exc_cause = 
            end else if(is_ebreak) begin
                if(csr_mcurrent_privilege == `armleocpu_PRIVILEGE_MACHINE) begin
                    e2debug_machine_ebreak = 1;
                end
            end else if(is_wfi && !csr_mstatus_tw) begin

            end else if(is_mret && (csr_mcurrent_privilege == `armleocpu_PRIVILEGE_MACHINE)) begin

            end else if(is_sret && !csr_mstatus_tsr && ((csr_mcurrent_privilege == `armleocpu_PRIVILEGE_MACHINE) || (csr_mcurrent_privilege == `armleocpu_PRIVILEGE_SUPERVISOR))) begin

            end else begin
                illegal_instruction = 1;
            end*/
        end
        default: begin
            illegal_instruction = 1;
        end
    endcase

    if(deferred_illegal_instruction) begin
        e2f_exc_start = 1;
        e2f_ready = 1;
    end else if(illegal_instruction) begin
        e2f_ready = 0;
        csr_exc_cmd = `CSR_EXC_START;
        csr_exc_cause = `EXCEPTION_CODE_ILLEGAL_INSTRUCTION;
    end else if(dcache_exc) begin
        //e2f_exc_start = 1;
        csr_exc_cmd = `CSR_EXC_START;
        csr_exc_cause = dcache_exc_cause;

        // TODO:
        //csr_exc_cause = EXCEPTION_CODE_;
    end else if(f2e_exc_start) begin
        csr_exc_cause = f2e_cause;
        csr_exc_cmd = `CSR_EXC_START;
    end
end

always @(posedge clk) begin
    if(!rst_n) begin
        dcache_command_issued <= 0;
        csr_done <= 0;
    end else if(c_reset_done) begin
        deferred_illegal_instruction <= 0;
        if(deferred_illegal_instruction) begin
            
        end else if(illegal_instruction) begin
            `ifdef DEBUG_EXECUTE
                $display("[%m][%d][Execute] Illegal instruction, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
            `endif
            deferred_illegal_instruction <= 1;
        end else begin
            if(is_op || is_op_imm) begin
                if(alu_illegal_instruction) begin
                    `ifdef DEBUG_EXECUTE
                        $display("[%m][%d][Execute] ALU Illegal instruction, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                    `endif
                end
                if(is_op) begin
                    `ifdef DEBUG_EXECUTE
                        $display("[%m][%d][Execute] ALU instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, rs1_data = 0x%X, rs2_data = 0x%X, rd_wdata = 0x%X, rd_write = %d", $time, f2e_instr, f2e_pc, rs1_data, rs2_data, rd_wdata, rd_write);
                    `endif
                end
                if(is_op_imm) begin
                    `ifdef DEBUG_EXECUTE
                        $display("[%m][%d][Execute] ALU IMM instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, rs1_data = 0x%X, immgen_simm12 = 0x%X, rd_wdata = 0x%X, rd_write = %d", $time, f2e_instr, f2e_pc, rs1_data, immgen_simm12, rd_wdata, rd_write);
                    `endif
                end
            end
            if(is_jal) begin
                `ifdef DEBUG_EXECUTE
                    $display("[%m][%d][Execute] JAL instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, e2f_branchtarget = 0x%X, immgen_jal_offset = 0x%X", $time, f2e_instr, f2e_pc, e2f_branchtarget, immgen_jal_offset);
                `endif
            end
            if(is_jalr) begin
                `ifdef DEBUG_EXECUTE
                    $display("[%m][%d][Execute] JALR instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, e2f_branchtarget = 0x%X", $time, f2e_instr, f2e_pc, e2f_branchtarget);
                `endif
            end
            if(is_branch) begin
                if(e2f_branchtaken) begin
                    `ifdef DEBUG_EXECUTE
                        $display("[%m][%d][Execute] Branch taken, f2e_instr = 0x%X, f2e_pc = 0x%X, e2f_branchtarget = 0x%X, rs1_data = 0x%X, rs2_data = 0x%X", $time, f2e_instr, f2e_pc, e2f_branchtarget, rs1_data, rs2_data);
                    `endif
                end else begin
                    `ifdef DEBUG_EXECUTE
                        $display("[%m][%d][Execute] Branch not taken, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                    `endif
                end
            end
            if(is_lui) begin
                `ifdef DEBUG_EXECUTE
                    $display("[%m][%d][Execute] LUI instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, rd_wdata = 0x%X", $time, f2e_instr, f2e_pc, rd_wdata);
                `endif
            end
            if(is_auipc) begin
                `ifdef DEBUG_EXECUTE
                    $display("[%m][%d][Execute] AUIPC instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, rd_wdata = 0x%X", $time, f2e_instr, f2e_pc, rd_wdata);
                `endif
            end
            if(is_load) begin
                if(!dcache_command_issued) begin
                    dcache_command_issued <= 1;
                    `ifdef DEBUG_EXECUTE
                        $display("[%m][%d][Execute] Load instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, c_response = , c_load_type = ,", $time, f2e_instr, f2e_pc);
                    `endif
                end else begin
                    if(dcache_response_done) begin
                        `ifdef DEBUG_EXECUTE
                            $display("[%m][%d][Execute] Load instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X, c_address = 0x%X, c_response = 0x%X, c_load_type = 0x%X, c_load_data = 0x%X", $time, f2e_instr, f2e_pc, c_address, c_response, c_load_type, c_load_data);
                        `endif
                        dcache_command_issued <= 0;
                    end else if(dcache_response_error) begin
                        `ifdef DEBUG_EXECUTE
                            $display("[%m][%d][Execute] Load instruction, error, f2e_instr = 0x%X, f2e_pc = 0x%X, c_address = 0x%X, c_response = 0x%X, c_load_type = 0x%X", $time, f2e_instr, f2e_pc, c_address, c_response, c_load_type);
                        `endif
                        dcache_command_issued <= 0;
                    end
                end
            end
            if(is_store) begin
                if(!dcache_command_issued) begin
                    dcache_command_issued <= 1;
                    `ifdef DEBUG_EXECUTE
                        $display("[%m][%d][Execute] Store instruction, f2e_instr = 0x%X, f2e_pc = 0x%X, c_store_type = 0x%X", $time, f2e_instr, f2e_pc, c_store_type);
                    `endif
                end else begin
                    if(dcache_response_done) begin
                        `ifdef DEBUG_EXECUTE
                            $display("[%m][%d][Execute]  Store instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X, c_address = 0x%X, c_response = 0x%X, c_store_type = 0x%X, c_store_data = 0x%X", $time, f2e_instr, f2e_pc, c_address, c_response, c_store_type, c_store_data);
                        `endif
                        dcache_command_issued <= 0;
                    end else if(dcache_response_error) begin
                        dcache_command_issued <= 0;
                        `ifdef DEBUG_EXECUTE
                            $display("[%m][%d][Execute] Store instruction, error, f2e_instr = 0x%X, f2e_pc = 0x%X, c_address = 0x%X, c_response = 0x%X, c_load_type = 0x%X", $time, f2e_instr, f2e_pc, c_address, c_response, c_store_type);
                        `endif
                    end
                end
            end
            if(is_fence) begin
                if(!dcache_command_issued) begin
                    dcache_command_issued <= 1'b1;
                    `ifdef DEBUG_EXECUTE
                        $display("[%m][%d][Execute] Fence instruction, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                    `endif
                end else if(dcache_command_issued) begin
                    if(dcache_response_done) begin
                        dcache_command_issued <= 1'b0;
                        `ifdef DEBUG_EXECUTE
                            $display("[%m][%d][Execute] Fence instruction, done, f2e_instr = 0x%X, f2e_pc = 0x%X", $time, f2e_instr, f2e_pc);
                        `endif
                    end
                end
            end
        end
    end
end

endmodule