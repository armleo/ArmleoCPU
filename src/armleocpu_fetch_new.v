
`include "armleocpu_cache.vh"
`include "ld_type.vh"
`include "armleocpu_exception.vh"
`include "armleocpu_privilege.vh"
`include "armleocpu_e2f_cmd.vh"

module armleocpu_fetch(
    input                   clk,
    input                   rst_n,

    // CSR Registers
    input [1:0]             csr_mcurrent_privilege,

    // From debug
    input                   dbg_request,
    input                   dbg_set_pc,
    input                   dbg_exit_request,
    input [31:0]            dbg_pc,

    // To Debug
    output reg              dbg_mode,
    // async signal:
    output reg              dbg_done,

    // Interrupts
    input                   interrupt_pending_csr,


    // towards execute
    output reg              f2d_instr_valid,
    output reg [31:0]       f2d_instr,
    output reg [31:0]       f2d_pc,
    output reg              f2d_instr_fetch_exception,
    output reg [31:0]       f2d_instr_fetch_exception_cause,
    output reg              f2d_interrupt_pending,
    

    // from execute
    input                                               d2f_ready,
    input      [`ARMLEOCPU_E2F_CMD_WIDTH-1:0]           d2f_cmd,
    input      [31:0]                                   e2f_jump_target
);

parameter RESET_VECTOR = 32'h0000_2000;

`include "armleocpu_includes.vh"




/*STATE*/
reg [31:0] pc;
reg [3:0] state;
reg [0:0] request_pending;

`define FETCH_STATE_ACTIVE 4'd0
`define FETCH_STATE_BUBBLE 4'd1
`define FETCH_STATE_KILL 4'd2
`define FETCH_STATE_FLUSH 4'd3

/*SIGNALS*/
reg [31:0] pc_nxt;
reg [3:0] state_nxt;

reg fetch_start;

wire [31:0] pc_plus_4 = pc + 4;


assign M_AXI_ARSIZE = 3'b010;
assign M_AXI_ARLEN = 0;
assign M_AXI_ARBURST = 2'b01; // INCR
assign M_AXI_ARADDR = pc_nxt;
assign M_AXI_ARLOCK = 0;
// TODO: Calculate PROT based on mcurrent_privilege
wire [2:0] prot = {1'b0, 1'b0, 1'b0};

reg address_done_nxt, address_done;

always @(posedge clk)
    state <= state_nxt;

always @(posedge clk)
    pc <= pc_nxt;


// f2d_instr_valid, f2d_instr_ready
// 
state == ACTIVE

    arvalid = 0
    arready = 0
    rready = 0
    rvalid = 0
    f2d_instr_valid = 0
    d2f_ready = x
    d2f_cmd = x

    first cylce request reset vector
    araddr == pc == RESET_VECTOR
    arvalid = 1
    arready = 0
    rvalid = 0
    rready = x
    f2d_instr_valid = 0
    d2f_ready = x
    d2f_cmd = x


    araddr == pc == RESET_VECTOR
    arvalid = 1
    arready = 1
    rvalid = 0
    rready = 0
    f2d_instr_valid = 1
    d2f_ready = 1
    d2f_cmd = 1

    arvalid = 1
    arready = 0
    rvalid = 1
    rready = 0
    f2d_instr_valid = 1
    d2f_ready = 1
    d2f_cmd = 1





    {arvalid, arready, rvalid, rready, f2d_instr_valid, d2f_ready, d2f_cmd != IDLE};

    rvalid -> f2d_instr_valid, read_done <= 0
    rready -> d2f_ready, read_done <= 1

    (rvalid && rready) || read_done -> 

    if(f2d_instr_valid && d2f_ready)
        // If instruction is valid and not stalled
        // send fetch to AR
        start_fetch = 1
        rready = 1;
    else if(f2d_instr_valid && !d2f_ready)
        // If instruction is valid and stalled
        // Don't send fetch
    else if(!f2d_instr_valid)
        // continue fetching instruction





endmodule