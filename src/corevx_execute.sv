module corevx_execute(
    input clk,
    input rst_n,

    // Fetch unit
    input      [31:0]       f2e_instr,
    input      [31:0]       f2e_pc,
    input                   f2e_exc_start,
    input      [3:0]        f2e_cause, // cause [3:0]
    input                   f2e_cause_interrupt, // cause[31]

    output reg              e2f_ready,
    output reg              e2f_exc_start,
    output reg              e2f_exc_return,
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



    // CSR Interface
    
    output reg              csr_exc_start,
    output reg              csr_exc_return,
    output     [31:0]       csr_exc_cause,
    output     [31:0]       csr_exc_epc,

    output reg [2:0]        csr_cmd, // NONE, WRITE, READ, READ_WRITE, 
    output     [11:0]       csr_address,
    input                   csr_invalid,
    input      [31:0]       csr_readdata,
    output reg [31:0]       csr_writedata,
 
    //input                   csr_mstatus_tsr, // sret generates illegal instruction
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

`include "corevx_cache.svh"
`include "corevx_instructions.svh"
`include "corevx_exception.svh"


// |------------------------------------------------|
// |                                                |
// |              Signals                           |
// |                                                |
// |------------------------------------------------|

// Decode opcode
wire [6:0]  instr_opcode            = f2e_instr[6:0];
assign      rd_addr                 = f2e_instr[11:7];
wire [2:0]  funct3                  = f2e_instr[14:12];
assign      rs1_addr                = f2e_instr[19:15];
assign      rs2_addr                = f2e_instr[24:20];
wire [6:0]  funct7                  = f2e_instr[31:25];
assign      c_load_type             = funct3;
assign      c_store_type            = funct3[1:0];
wire        store_st_type_incorrect = funct3[2];

//
//
//
wire sign = f2e_instr[31];

wire [31:0] immgen_simm12 = {{20{sign}}, f2e_instr[31:20]};
wire [31:0] immgen_store_offset = {{20{sign}}, f2e_instr[31:25], f2e_instr[11:7]};
wire [31:0] immgen_branch_offset = {{20{sign}}, f2e_instr[7], f2e_instr[30:25], f2e_instr[11:8], 1'b0};
wire [31:0] immgen_upper_imm = {f2e_instr[31:12], 12'h000};
wire [31:0] immgen_jal_offset = {{12{sign}}, f2e_instr[19:12], f2e_instr[11], f2e_instr[30:25], f2e_instr[24:21], 1'b0};
wire [31:0] immgen_csr_imm = {27'b0, f2e_instr[19:15]}; // used by csr bit write/set/clear


assign      csr_exc_epc             = f2e_pc;

reg is_op_imm;
reg is_op;
reg unknown_opcode;

wire [31:0] alu_result;
wire alu_illegal_instruction;

wire [31:0] pc_plus_4 = f2e_pc + 4;

wire brcond_branchtaken;
wire brcond_incorrect_instruction;
// |------------------------------------------------|
// |              ALU                               |
// |------------------------------------------------|
corevx_alu alu(
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
corevx_brcond brcond(
    .funct3(funct3),
    .rs1(rs1_data),
    .rs2(rs2_data),
    .incorrect_instruction(brcond_incorrect_instruction),
    .branch_taken(brcond_branchtaken)
);

reg [1:0] rd_sel;

`define RD_ALU (2'd0)
`define RD_CSR (2'd1)
`define RD_DCACHE (2'd2)

always @* begin
    case(rd_sel)
        `RD_ALU:    rd_wdata = alu_result;
        //`RD_CSR:    rd_wdata = csr_readdata;
        `RD_DCACHE: rd_wdata = c_load_data;
        default:    rd_wdata = alu_result;
    endcase
end



always @* begin
    e2f_exc_start = 0;
    e2f_exc_return = 0;
    e2f_ready = 1;
    e2f_flush = 0;
    e2f_branchtarget = f2e_pc + immgen_branch_offset;
    e2f_branchtaken = 0;

    e2debug_machine_ebreak = 0;

    c_cmd = `CACHE_CMD_NONE;
    c_address = 0; // TODO


    csr_exc_start = 0;
    csr_exc_return = 0;
    

    csr_cmd = 0;// TODO
    csr_writedata = 0;



    is_op_imm = 0;
    is_op = 0;
    rd_write = 0;
    rd_sel = `RD_ALU;
    
    unknown_opcode = 0;

    rd_write = 0;
    case(instr_opcode)
        /*`OPCODE_LUI: begin

        end
        `OPCODE_AUIPC: begin

        end
        `OPCODE_JALR: begin

        end
        `OPCODE_JAL: begin
            // TODO:
            e2f_branchtarget = pc + branch_offset;
            e2f_branchtarget = ;
            e2f_branchtaken = 1;
            rd_write = (rd_addr != 0);
        end
        */
        `OPCODE_BRANCH: begin
            if(!brcond_incorrect_instruction) begin
                e2f_branchtarget = f2e_pc + immgen_branch_offset;
                e2f_branchtaken = brcond_branchtaken;
                e2f_exc_start = 0;
            end else begin
                e2f_exc_start = 1;
                csr_exc_start = 1;
                csr_exc_cause = EXCEPTION_CODE_ILLEGAL_INSTRUCTION;
                // TODO: CSR_exc_start = 1, set cause to correct value
            end
            e2f_ready = 1;
        end
        /*
        `OPCODE_LOAD: begin

        end
        `OPCODE_STORE: begin

        end*/
        `OPCODE_OP_IMM: begin
            e2f_ready = 1;
            is_op_imm = 1;
            rd_write = (rd_addr != 0);
            rd_sel = `RD_ALU;
            // TODO: SRAI, SLLI, SRLI (shamt)
        end
        `OPCODE_OP: begin
            is_op = 1;
            e2f_ready = 1;
            rd_write = (rd_addr != 0);
            rd_sel = `RD_ALU;
        end
        default: begin
            e2f_exc_start = 1;
            e2f_ready = 1;
            unknown_opcode = 1;
        end
    endcase
    
end


endmodule