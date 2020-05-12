`timescale 1ns/1ns
module ptw_testbench;

`include "../clk_gen_template.inc"

`include "ld_type.inc"
initial begin
	#100
	$finish;
end


reg [1:0] inwordOffset;
reg [2:0] loadType;


reg [31:0] LoadGenDataIn;

wire [31:0] LoadGenDataOut;
wire LoadMissaligned;
wire LoadUnknownType;


corevx_loadgen loadgen(
	.*
);

integer m;
reg [31:0] tempword;
initial begin
	@(negedge clk)
	loadType = `LOAD_BYTE;
	LoadGenDataIn = 32'h8888_8888;
	for(m = 0; m < 4; m = m + 1) begin
		@(negedge clk)
		inwordOffset = m;
		@(posedge clk)
		tempword = LoadGenDataIn >> (m * 8);
		`assert($signed(LoadGenDataOut), $signed(tempword[7:0]));
		`assert(LoadMissaligned, 0);
		$display("Test signed byte - Done inwordOffset=%d", inwordOffset);
	end

	@(negedge clk)
	loadType = `LOAD_BYTE_UNSIGNED;
	LoadGenDataIn = 32'h8888_8888;
	for(m = 0; m < 4; m = m + 1) begin
		@(negedge clk)
		inwordOffset = m;
		@(posedge clk)
		tempword = LoadGenDataIn >> (m * 8);
		`assert(LoadGenDataOut, tempword[7:0]);
		`assert(LoadMissaligned, 0);
		$display("Test unsigned byte - Done inwordOffset=%d", inwordOffset);
	end


	@(negedge clk)
	loadType = `LOAD_HALF_UNSIGNED;
	LoadGenDataIn = 32'h8888_8888;
	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		inwordOffset = m << 1;
		@(posedge clk)
		tempword = LoadGenDataIn >> (m * 16);
		`assert(LoadGenDataOut, tempword[15:0]);
		`assert(LoadMissaligned, 0);
		$display("Test aligned unsigned Halfword - Done inwordOffset=%d", inwordOffset);

		@(negedge clk)
		inwordOffset = (m << 1) + 1;
		@(posedge clk)
		`assert(LoadMissaligned, 1);
		$display("Test missaligned unsigned Halfword - Done inwordOffset=%d", inwordOffset);
	end

	@(negedge clk)
	loadType = `LOAD_HALF;
	LoadGenDataIn = 32'h8888_8888;
	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		inwordOffset = m << 1;
		@(posedge clk)
		tempword = LoadGenDataIn >> (m * 16);
		`assert($signed(LoadGenDataOut), $signed(tempword[15:0]));
		`assert(LoadMissaligned, 0);
		$display("Test aligned signed Halfword - Done inwordOffset=%d", inwordOffset);

		@(negedge clk)
		inwordOffset = (m << 1) + 1;
		@(posedge clk)
		`assert(LoadMissaligned, 1);
		$display("Test missaligned signed Halfword - Done inwordOffset=%d", inwordOffset);
	end

	@(negedge clk)
	loadType = `LOAD_WORD;
	LoadGenDataIn = 32'h8888_8888;
	inwordOffset = 0;
	@(negedge clk)
	`assert(LoadMissaligned, 0);
	`assert(LoadGenDataOut, LoadGenDataIn);
	$display("Test aligned word = Done inwordOffset=%d", inwordOffset);

	for(m = 1; m < 4; m = m + 1) begin
		@(negedge clk)
		inwordOffset = m;
		@(posedge clk)
		`assert(LoadMissaligned, 1);
		$display("Test aligned word = Done inwordOffset=%d", inwordOffset);
	end
	

end
endmodule