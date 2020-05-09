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
    output reg              e2f_flush,
    output reg              e2f_branchtaken,
    output reg [31:0]       e2f_branchtarget,

    output reg              e2debug_machine_ebreak,

    // Cache interface
    input      [3:0]        c_response,
    input                   c_reset_done,

    output     [3:0]        c_cmd,
    output reg [31:0]       c_address,
    output     [2:0]        c_load_type,
    input      [31:0]       c_load_data,
    output     [1:0]        c_store_type,
    output     [31:0]       c_store_data,



    // CSR Interface
    /*
    csr_cmd,
    csr_exc_start,
    csr_exc_cause,
    csr_address,
    csr_invalid,
    csr_readdata,
    csr_writedata,
    */

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
`include "corevx_immgen.svh"
`include "corevx_instructions.svh"

// Decode opcode
wire [6:0] instr_opcode = f2e_instr[6:0];
assign rd_addr          = f2e_instr[11:7];
wire [2:0] funct3       = f2e_instr[14:12];
assign rs1_addr         = f2e_instr[19:15];
assign rs2_addr         = f2e_instr[24:20];
wire [6:0] funct7       = f2e_instr[31:25];

reg is_alui;
reg unknown_opcode = 1;

reg [2:0] immgen_sel;
wire [31:0] immgen_out;

wire [31:0] alu_result;
wire alu_unknown_operation;

wire [31:0] pc_plus_4 = f2e_pc + 4;

wire brcond_branchtaken;
wire brcond_incorrect_instruction;

corevx_alu alu(
    .is_alui(is_alui),
    .funct3(funct3),
    .funct7(funct7),

    .operand0(rs1_data),
    .alu_operand1(rs2_data),
    .alui_operand1(immgen_out),

    .result(alu_result),
    .unknown_operation(alu_unknown_operation)
);


corevx_immgen immgen(
    .instruction(f2e_instr),
    .sel(immgen_sel),
    .out(immgen_out)
);

corevx_brcond brcond(
    .funct3(funct3),
    .rs1(rs1_data),
    .rs2(rs2_data),
    .incorrect_instruction(brcond_incorrect_instruction),
    .branch_taken(brcond_branchtaken)
);

`define RD_ALU (2'd0)
`define RD_CSR (2'd1)
`define RD_DCACHE (2'd2)

always @* begin
    case(rd_sel)
        `RD_ALU:    rd_wdata = alu_result;
        `RD_CSR:    rd_wdata = csr_readdata;
        `RD_DCACHE: rd_wdata = c_load_data;
        default:    rd_wdata = alu_result;
    endcase
end



always @* begin
    is_alui = 0;
    rd_write = 0;
    rd_sel = ALU;
    e2f_branchtarget = f2e_pc + immgen_out;
    e2f_branchtaken = 0;
    e2f_exc_start = 0;
    e2f_ready = 1;
    immgen_sel = `IMM_I;
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
            branch_offset_sel = jal_offset;
            e2f_branchtarget = pc + branch_offset;
            e2f_branchtarget = ;
            e2f_branchtaken = 1;
            rd_write = (rd_addr != 0);
        end
        */
        `OPCODE_BRANCH: begin
            immgen_sel = `IMM_B;
            if(!brcond_incorrect_instruction) begin
                e2f_branchtarget = f2e_pc + immgen_out;
                e2f_branchtaken = brcond_branchtaken;
                e2f_exc_start = 1;
            end
            e2f_ready = 1;
        end
        /*
        `OPCODE_LOAD: begin

        end
        `OPCODE_STORE: begin

        end*/
        `OPCODE_ALUI: begin
            e2f_ready = 1;
            immgen_sel = `IMM_I;
            is_alui = 1;
            rd_write = (rd_addr != 0);
            rd_sel = ALU;
        end
        `OPCODE_ALU: begin
            e2f_ready = 1;
            rd_write = (rd_addr != 0);
            rd_sel = ALU;
        end
        default: begin
            e2f_exc_start = 1;
            e2f_ready = 1;
            unknown_opcode = 1;
        end
    endcase

end


endmodule