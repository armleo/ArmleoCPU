
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


    // Memory <-> Cache / AXI4 Bus in no cache configuration
    // AR-Bus
    output	reg				            M_AXI_ARVALID,
    input	wire				        M_AXI_ARREADY,
    output	reg	[31:0]	                M_AXI_ARADDR,
    output	reg	[7:0]			        M_AXI_ARLEN,
    output	reg	[2:0]			        M_AXI_ARSIZE,
    output	reg	[1:0]			        M_AXI_ARBURST,
    output	reg				            M_AXI_ARLOCK,
    output	reg	[2:0]			        M_AXI_ARPROT, // Read documentation about this signal value represantation
    // R-Bus
    input	wire				        M_AXI_RVALID,
    output	reg				            M_AXI_RREADY,
    input	wire	[31:0]	            M_AXI_RDATA,
    input	wire				        M_AXI_RLAST,
    input	wire	[1:0]			    M_AXI_RRESP,
    input   wire                        M_AXI_RUSER, // [0] shows pagefault RRESP should be 2'b11
    // F-Bus
    output reg                          M_AXI_FVALID,
    input                               M_AXI_FREADY,


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







endmodule