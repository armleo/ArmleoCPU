////////////////////////////////////////////////////////////////////////////////
//
// Filename: armleocpu_regfile_one_lane.v
// Project:	ArmleoCPU
//
// Purpose:	Used by regfile to allow two reads and one write
//		two instances is used, where
//		write port is connected together and exposed and
//		two read ports are exposed to core itself
//		
//
// Copyright (C) 2021, Arman Avetisyan
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_regfile_one_lane (clk, readaddress, read, readdata, writeaddress, write, writedata);
	parameter ELEMENTS_W = 5;
	localparam ELEMENTS = 2**ELEMENTS_W;
	parameter WIDTH = 32;

	input clk;

    input [ELEMENTS_W-1:0] readaddress;
    input read;
	output reg [WIDTH-1:0] readdata;


	input [ELEMENTS_W-1:0] writeaddress;
	input write;
	input [WIDTH-1:0] writedata;

reg [WIDTH-1:0] storage[ELEMENTS-1:0];

always @(posedge clk) begin
	if(write) begin
		storage[writeaddress] <= writedata;
	end
	if(read)
		readdata <= storage[readaddress];
end

endmodule


`include "armleocpu_undef.vh"

