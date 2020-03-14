
module mem_2r1w(
    input clk,

    input read0,
    input [ELEMENTS_W-1:0] readaddress0,
    output [WIDTH-1:0] readdata0,


    input read1,
    input [ELEMENTS_W-1:0] readaddress1,
    output [WIDTH-1:0] readdata1,

    input write,
    input [ELEMENTS_W-1:0] writeaddress,
	input [WIDTH-1:0] writedata

);

parameter ELEMENTS_W = 7;
localparam ELEMENTS = 2**ELEMENTS_W;
parameter WIDTH = 32;

mem_1w1r #(ELEMENTS_W, WIDTH) mem0(
    .clk(clk),

    .read(read0),
    .readaddress(readaddress0),
    .readdata(readdata0),

    .write(write),
    .writeaddress(writeaddress),
    .writedata(writedata)
);


mem_1w1r #(ELEMENTS_W, WIDTH) mem1(
    .clk(clk),

    .read(read1),
    .readaddress(readaddress1),
    .readdata(readdata1),

    .write(write),
    .writeaddress(writeaddress),
    .writedata(writedata)
);

endmodule