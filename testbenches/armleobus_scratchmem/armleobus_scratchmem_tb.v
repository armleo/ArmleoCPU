`timescale 1ns/1ns
module armleobus_scratchmem_tb;

`include "../sync_clk_gen_template.vh"

`include "armleocpu_defines.vh"

localparam ADDRESS_W = 4;
localparam DEPTH = 2**ADDRESS_W;

reg [31:0] teststorage [DEPTH-1:0];

reg         transaction;
reg  [2:0]  cmd;
wire        transaction_done;
wire [2:0]  transaction_response;
reg  [ADDRESS_W+2-1:0]    address;
reg  [31:0]             wdata;
reg  [3:0]             wbyte_enable;
wire [31:0]             rdata;

armleobus_scratchmem #(ADDRESS_W, 2) sm(
    .*
);

reg [31:0] temp;
integer seed;
initial begin
    seed = 32'h13ea9c84;
    temp = $urandom(seed);
    //address = $urandom << 2;
    wbyte_enable = 4'hF;
    cmd = `ARMLEOBUS_CMD_NONE;
    @(posedge rst_n)
    @(negedge clk)

    address = 4;
    transaction = 1;
    cmd = `ARMLEOBUS_CMD_WRITE;
    temp = $urandom;
    wdata = temp;
    while(!transaction_done) begin
        @(negedge clk);
    end
    `assert(transaction_response, `ARMLEOBUS_RESPONSE_SUCCESS);
    @(negedge clk);
    cmd = `ARMLEOBUS_CMD_READ;
    wdata = 0;
    while(!transaction_done) begin
        @(negedge clk);
    end
    @(posedge clk)
    `assert(rdata, temp);

    repeat(10) @(posedge clk);
    $finish;
end

endmodule
