`timescale 1ns/1ns
module storegen_testbench;

`include "../clk_gen_template.vh"

`include "armleocpu_defines.vh"

initial begin
	#100
	$finish;
end

reg [1:0] inwordOffset;
reg [1:0] storegenType;

reg [31:0] storegenDataIn;

wire [31:0] storegenDataOut;
wire [3:0] storegenDataMask;
wire storegenMissAligned;
wire storegenUnknownType;

armleocpu_storegen storegen(
	.*
);

integer m;

initial begin
	@(negedge clk)
	storegenType = 2'b11;
	@(posedge clk)
	`assert(storegenUnknownType, 1);


	@(negedge clk)
	storegenType = `STORE_BYTE;
	storegenDataIn = 32'hAA;
	for(m = 0; m < 4; m = m + 1) begin
		@(negedge clk)
		inwordOffset = m;
		@(posedge clk)
		//$display(storegenDataMask, 1 << m);
		`assert(storegenDataMask, 1 << m)
		`assert(storegenDataOut, storegenDataIn << (m * 8))
		`assert(storegenMissAligned, 0);
		`assert(storegenUnknownType, 0);
		$display("Test Byte - Done inwordOffset=%d", inwordOffset);
	end

	storegenType = `STORE_HALF;
	storegenDataIn = 32'hAAAA;

	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		inwordOffset = m << 1;
		@(posedge clk)
		`assert(storegenUnknownType, 0);
		//$display(storegenDataMask, 2'b11 << (m * 2));
		`assert(storegenDataMask, 2'b11 << (m * 2));
		`assert(storegenDataOut, storegenDataIn << (m * 16));
		`assert(storegenMissAligned, 0);
		$display("Test Halfword - Done inwordOffset=%d", inwordOffset);
	end

	@(negedge clk)
	storegenType = `STORE_WORD;
	storegenDataIn = 32'hAAAABBBB;
	inwordOffset = 0;
	@(posedge clk)
	`assert(storegenUnknownType, 0);
	`assert(storegenDataMask, 4'b1111);
	`assert(storegenDataOut, storegenDataIn);
	`assert(storegenMissAligned, 0);
	$display("Test Word - Done inwordOffset=%d", inwordOffset);

	for(m = 1; m < 4; m = m + 1) begin
		@(negedge clk)
		storegenType = `STORE_WORD;
		storegenDataIn = 32'hAAAABBBB;
		inwordOffset = m;
		@(posedge clk)
		`assert(storegenUnknownType, 0);
		`assert(storegenMissAligned, 1);
		$display("Test missaligned Word - Done inwordOffset=%d", inwordOffset);
	end

	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		storegenType = `STORE_WORD;
		storegenDataIn = 32'hAAAABBBB;
		inwordOffset = (m << 1) | 1;
		@(posedge clk)
		`assert(storegenUnknownType, 0);
		`assert(storegenMissAligned, 1);
		$display("Test missaligned half - Done inwordOffset=%d", inwordOffset);
	end

end


endmodule