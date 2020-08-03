`include "armleocpu_e2f_cmd.vh"

module armleocpu_fpga_harness(
    input clk,
    input rst_n_i,

    output reg              d_transaction_r,
    output reg   [2:0]      d_cmd_r,
    input                   d_transaction_done_i,
    input        [2:0]      d_transaction_response_i,
    output reg   [33:0]     d_address_r,
    output reg   [3:0]      d_burstcount_r,
    output reg   [31:0]     d_wdata_r,
    output reg   [3:0]      d_wbyte_enable_r,
    input        [31:0]     d_rdata_i,

    output reg              i_transaction_r,
    output reg   [2:0]      i_cmd_r,
    input                   i_transaction_done_i,
    input        [2:0]      i_transaction_response_i,
    output reg   [33:0]     i_address_r,
    output reg   [3:0]      i_burstcount_r,
    output reg   [31:0]     i_wdata_r,
    output reg   [3:0]      i_wbyte_enable_r,
    input        [31:0]     i_rdata_i,

    input                   irq_timer_i_i,
    input                   irq_exti_i_i,
    input                   irq_swi_i_i,

    output reg              dbg_mode_r, /*TODO: Fix*/
    output reg              dbg_done_r /*TODO: Fix*/
);


reg                     rst_n;
always @(posedge clk)
    rst_n <= rst_n_i;

wire                  d_transaction;
always @(posedge clk)
    d_transaction_r <= d_transaction;
wire       [2:0]      d_cmd;
always @(posedge clk)
    d_cmd_r <= d_cmd;

reg                   d_transaction_done;
always @(posedge clk)
    d_transaction_done <= d_transaction_done_i;

reg        [2:0]      d_transaction_response;
always @(posedge clk)
    d_transaction_response <= d_transaction_response_i;

wire       [33:0]     d_address;
wire       [3:0]      d_burstcount;
wire       [31:0]     d_wdata;
wire       [3:0]      d_wbyte_enable;
always @(posedge clk) begin
    d_address_r <= d_address;
    d_burstcount_r <= d_burstcount;
    d_wdata_r <= d_wdata;
    d_wbyte_enable_r <= d_wbyte_enable;
end

reg        [31:0]     d_rdata;
always @(posedge clk)
    d_rdata <= d_rdata_i;

wire                  i_transaction;
always @(posedge clk)
    i_transaction_r <= i_transaction;
wire       [2:0]      i_cmd;
always @(posedge clk)
    i_cmd_r <= i_cmd;


reg                   i_transaction_done;
always @(posedge clk)
    i_transaction_done <= i_transaction_done_i;
reg        [2:0]      i_transaction_response;
always @(posedge clk)
    i_transaction_response <= i_transaction_response_i;


wire       [33:0]     i_address;
wire       [3:0]      i_burstcount;
wire       [31:0]     i_wdata;
wire       [3:0]      i_wbyte_enable;
always @(posedge clk) begin
    i_address_r <= i_address;
    i_burstcount_r <= i_burstcount;
    i_wdata_r <= i_wdata;
    i_wbyte_enable_r <= i_wbyte_enable;
end

reg        [31:0]     i_rdata;
always @(posedge clk)
    i_rdata <= i_rdata_i;

reg                   irq_timer_i;
reg                   irq_exti_i;
reg                   irq_swi_i;
always @(posedge clk) begin
    irq_timer_i <= irq_timer_i_i;
    irq_exti_i <= irq_exti_i_i;
    irq_swi_i <= irq_swi_i_i;
end

reg                   dbg_request = 0;
reg        [3:0]      dbg_cmd = 0;

wire                  dbg_mode;
wire                  dbg_done;



always @(posedge clk) begin
    dbg_mode_r <= dbg_mode;
    dbg_done_r <= dbg_done;
end

armleocpu acpu(
    .clk(clk),
    .rst_n(rst_n),

    .d_transaction(d_transaction),
    .d_cmd(d_cmd),
    .d_transaction_done(d_transaction_done),
    .d_transaction_response(d_transaction_response),
    .d_address(d_address),
    .d_burstcount(d_burstcount),
    .d_wdata(d_wdata),
    .d_wbyte_enable(d_wbyte_enable),
    .d_rdata(d_rdata),

    .i_transaction(i_transaction),
    .i_cmd(i_cmd),
    .i_transaction_done(i_transaction_done),
    .i_transaction_response(i_transaction_response),
    .i_address(i_address),
    .i_burstcount(i_burstcount),
    .i_wdata(i_wdata),
    .i_wbyte_enable(i_wbyte_enable),
    .i_rdata(i_rdata),


    .irq_timer_i(irq_timer_i),
    .irq_exti_i(irq_exti_i),
    .irq_swi_i(irq_exti_i),

    .dbg_request(dbg_request),
    .dbg_cmd(dbg_cmd),
    
    .dbg_mode(dbg_mode),
    .dbg_done(dbg_done)
);


endmodule