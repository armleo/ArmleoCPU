`timescale 1ns/1ns
module ptw_testbench;

`include "../clk_gen_template.svh"

`include "../../src/armleocpu_defs.sv"

initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
	#100
	$finish;
end

reg [1:0] inwordOffset;
reg [1:0] st_type;

reg [31:0] storeDataIn;

wire [31:0] storeDataOut;
wire [3:0] storeDataMask;
wire storeMissAligned;


armleocpu_storegen storegen(
	.*
);

integer m;

initial begin
	@(negedge clk)
	st_type = ST_SB;
	storeDataIn = 32'hAA;
	for(m = 0; m < 4; m = m + 1) begin
		@(negedge clk)
		inwordOffset = m;
		@(posedge clk)
		//$display(storeDataMask, 1 << m);
		`assert(storeDataMask, 1 << m)
		`assert(storeDataOut, storeDataIn << (m * 8))
		`assert(storeMissAligned, 0);
		$display("Test Byte - Done inwordOffset=%d", inwordOffset);
	end

	st_type = ST_SH;
	storeDataIn = 32'hAAAA;

	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		inwordOffset = m << 1;
		@(posedge clk)
		//$display(storeDataMask, 2'b11 << (m * 2));
		`assert(storeDataMask, 2'b11 << (m * 2));
		`assert(storeDataOut, storeDataIn << (m * 16));
		`assert(storeMissAligned, 0);
		$display("Test Halfword - Done inwordOffset=%d", inwordOffset);
	end

	@(negedge clk)
	st_type = ST_SW;
	storeDataIn = 32'hAAAABBBB;
	inwordOffset = 0;
	@(posedge clk)
	`assert(storeDataMask, 4'b1111);
	`assert(storeDataOut, storeDataIn);
	`assert(storeMissAligned, 0);
	$display("Test Word - Done inwordOffset=%d", inwordOffset);

	for(m = 1; m < 4; m = m + 1) begin
		@(negedge clk)
		st_type = ST_SW;
		storeDataIn = 32'hAAAABBBB;
		inwordOffset = m;
		@(posedge clk)
		`assert(storeMissAligned, 1);
		$display("Test missaligned Word - Done inwordOffset=%d", inwordOffset);
	end

	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		st_type = ST_SW;
		storeDataIn = 32'hAAAABBBB;
		inwordOffset = (m << 1) | 1;
		@(posedge clk)
		`assert(storeMissAligned, 1);
		$display("Test missaligned half - Done inwordOffset=%d", inwordOffset);
	end

end


endmodule