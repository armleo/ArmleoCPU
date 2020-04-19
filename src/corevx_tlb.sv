`timescale 1ns/1ns

module corevx_tlb(
    input clk,
    input rst_n,
    
    input [19:0]        virtual_address,
    // commands
    input [1:0]         command,

    // read port
    output  reg         hit,
    output  reg [7:0]   accesstag_r,
    output  reg [21:0]  phys_r,
    
    // write port
    input       [19:0]  virtual_address_w,
    input       [7:0]   accesstag_w,
    input       [21:0]  phys_w
);

parameter  ENTRIES_W = 4;

parameter  WAYS_W = 2;
localparam WAYS = 2**WAYS_W;

/*
for resolve request resolve for all tlb  ways
for write, write to tlb[victim_way]
for invalidate, write 0 to valid to all ways
*/


endmodule

