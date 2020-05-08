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
    csr_writedata,*/

    // Regfile
    output     [4:0]        rs1_addr,
    input      [31:0]       rs1_data,

    output     [4:0]        rs2_addr,
    input      [31:0]       rs2_data,
    
    output     [4:0]        rd_addr,
    output reg [31:0]       rd_wdata,
    output reg              rd_write
);

// Decode opcode
wire [6:0] instr_opcode = f2e_instr[6:0];
assign rd_addr          = f2e_instr[11:7];
wire [2:0] funct3       = f2e_instr[14:12];
assign rs1_addr         = f2e_instr[19:15];
assign rs2_addr         = f2e_instr[24:20];
wire [6:0] funct7       = f2e_instr[31:25];


/*
ALU
IMMGEN
BRCOND
*/


always @* begin
    case(instr_opcode)

    endcase
end


endmodule