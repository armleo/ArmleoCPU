`include "armleocpu_e2f_cmd.vh"

module armleocpu_execute(
    input clk,
    input rst_n,

// Fetch unit
    input      [31:0]       f2e_instr,
    input                   f2e_ignore_instr,
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
    output                  rs1_read,
    output     [4:0]        rs1_addr,
    input      [31:0]       rs1_data,

    output                  rs2_read,
    output     [4:0]        rs2_addr,
    input      [31:0]       rs2_data,
    
    output reg              rd_write,
    output     [4:0]        rd_addr,
    output reg [31:0]       rd_wdata
);

wire ready = e2f_ready && !((
                                    (rs1_read && rs1_addr != 0 && rs1_addr == rd_addr) ||
                                    (rs2_read && rs2_addr != 0 && rs2_addr == rd_addr)
                                ) && rd_write);




endmodule