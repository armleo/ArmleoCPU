`timescale 1ns/1ns

//`define ASYNC

module mem_1w1r (clk, readaddress, read, readdata, writeaddress, write, writedata);
	parameter ELEMENTS_W = 7;
	localparam ELEMENTS = 2**ELEMENTS_W;
	parameter WIDTH = 32;

	input clk;

    input [ELEMENTS_W-1:0] readaddress;
    input read;
	`ifdef ASYNC
    output [WIDTH-1:0] readdata;
	`else
	
	output reg [WIDTH-1:0] readdata;
	`endif

	input [ELEMENTS_W-1:0] writeaddress;
	input write;
	input [WIDTH-1:0] writedata;

reg [WIDTH-1:0] storage[ELEMENTS-1:0];
/*
initial begin
	integer i;
	for(i = 0; i < ELEMENTS; i = i + 1) begin
		storage[i] = 0;
	end
end
*/

`ifdef ASYNC
reg [ELEMENTS_W-1:0] readaddress_r;

assign readdata = storage[readaddress_r];
`endif

always @(posedge clk) begin
	if(write) begin
		storage[writeaddress] <= writedata;
	end
	`ifdef ASYNC
	if(read)
		readaddress_r <= readaddress;
	`else
	if(read)
		readdata <= storage[readaddress];
	`endif
end

endmodule