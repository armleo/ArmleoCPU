`timescale 1ns/1ns

module armleocpu_mem_1rw (clk, address, read, readdata, write, writedata);
	parameter ELEMENTS_W = 7;
	localparam ELEMENTS = 2**ELEMENTS_W;
	parameter WIDTH = 32;

	input clk;

    input [ELEMENTS_W-1:0] address;
    input read;
    output reg [WIDTH-1:0] readdata;

	input write;
	input [WIDTH-1:0] writedata;

reg [WIDTH-1:0] storage[ELEMENTS-1:0];


always @(posedge clk) begin
	if(write) begin
		storage[address] <= writedata;
	end
	if(read)
		readdata <= storage[address];
end

endmodule