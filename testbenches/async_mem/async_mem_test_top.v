`timescale 1ns/1ns


module async_mem_test_top(
    input clk,
    output internal_clk,
    
    
    input [7:0] raddr,
    output [31:0] rdata,
    
    input [7:0] waddr,
    input write,
    input [31:0] wdata
);

wire [1:0] counter;

internal_clk_gen clk_gen(
    .clk(clk),
    .internal_clk(internal_clk),
    
    .counter(counter)
);

async_mem async_mem_back(
    .clk(clk),
    .counter(counter),
    
    .raddr(raddr),
    .rdata(rdata),
    .waddr(waddr),
    .write(write),
    .wdata(wdata)
);

endmodule
