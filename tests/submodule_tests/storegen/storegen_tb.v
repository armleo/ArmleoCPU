`timescale 1ns/1ns
module storegen_testbench;

`include "async_clk_gen.vh"
`include "assert.vh"
`include "sim_dump.vh"

`include "armleocpu_defines.vh"

initial begin
	#100
	`assert(0)
end

reg [1:0] inword_offset;
reg [1:0] storegen_type;

reg [31:0] storegen_datain;

wire [31:0] storegen_dataout;
wire [3:0] storegen_datamask;
wire storegen_missaligned;
wire storegen_unknowntype;

armleocpu_storegen storegen(
	.*
);

integer m;

initial begin
	@(negedge clk)
	storegen_type = 2'b11;
	@(posedge clk)
	`assert_equal(storegen_unknowntype, 1);


	@(negedge clk)
	storegen_type = `STORE_BYTE;
	storegen_datain = 32'hAA;
	for(m = 0; m < 4; m = m + 1) begin
		@(negedge clk)
		inword_offset = m;
		@(posedge clk)
		//$display(storegen_datamask, 1 << m);
		`assert_equal(storegen_datamask, 1 << m)
		`assert_equal(storegen_dataout, storegen_datain << (m * 8))
		`assert_equal(storegen_missaligned, 0);
		`assert_equal(storegen_unknowntype, 0);
		$display("Test Byte - Done inword_offset=%d", inword_offset);
	end

	storegen_type = `STORE_HALF;
	storegen_datain = 32'hAAAA;

	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		inword_offset = m << 1;
		@(posedge clk)
		`assert_equal(storegen_unknowntype, 0);
		//$display(storegen_datamask, 2'b11 << (m * 2));
		`assert_equal(storegen_datamask, 2'b11 << (m * 2));
		`assert_equal(storegen_dataout, storegen_datain << (m * 16));
		`assert_equal(storegen_missaligned, 0);
		$display("Test Halfword - Done inword_offset=%d", inword_offset);
	end

	@(negedge clk)
	storegen_type = `STORE_WORD;
	storegen_datain = 32'hAAAABBBB;
	inword_offset = 0;
	@(posedge clk)
	`assert_equal(storegen_unknowntype, 0);
	`assert_equal(storegen_datamask, 4'b1111);
	`assert_equal(storegen_dataout, storegen_datain);
	`assert_equal(storegen_missaligned, 0);
	$display("Test Word - Done inword_offset=%d", inword_offset);

	for(m = 1; m < 4; m = m + 1) begin
		@(negedge clk)
		storegen_type = `STORE_WORD;
		storegen_datain = 32'hAAAABBBB;
		inword_offset = m;
		@(posedge clk)
		`assert_equal(storegen_unknowntype, 0);
		`assert_equal(storegen_missaligned, 1);
		$display("Test missaligned Word - Done inword_offset=%d", inword_offset);
	end

	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		storegen_type = `STORE_WORD;
		storegen_datain = 32'hAAAABBBB;
		inword_offset = (m << 1) | 1;
		@(posedge clk)
		`assert_equal(storegen_unknowntype, 0);
		`assert_equal(storegen_missaligned, 1);
		$display("Test missaligned half - Done inword_offset=%d", inword_offset);
	end
	$finish;
end


endmodule