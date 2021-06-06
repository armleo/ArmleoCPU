`timescale 1ns/1ns
module loadgen_testbench;

`include "../clk_gen_template.vh"

`include "armleocpu_defines.vh"

initial begin
	#100
	$finish;
end


reg [1:0] inword_offset;
reg [2:0] loadgen_type;


reg [31:0] loadgen_datain;

wire [31:0] loadgen_dataout;
wire loadgen_missaligned;
wire loadgen_unknowntype;
// TODO: Add test for loadgen_unknowntype


armleocpu_loadgen loadgen(
	.*
);

integer m;
reg [31:0] tempword;
initial begin
	@(negedge clk)
	loadgen_type = `LOAD_BYTE;
	loadgen_datain = 32'h8888_8888;
	for(m = 0; m < 4; m = m + 1) begin
		@(negedge clk)
		inword_offset = m;
		@(posedge clk)
		tempword = loadgen_datain >> (m * 8);
		`assert($signed(loadgen_dataout), $signed(tempword[7:0]));
		`assert(loadgen_missaligned, 0);
		$display("Test signed byte - Done inword_offset=%d", inword_offset);
	end

	@(negedge clk)
	loadgen_type = `LOAD_BYTE_UNSIGNED;
	loadgen_datain = 32'h8888_8888;
	for(m = 0; m < 4; m = m + 1) begin
		@(negedge clk)
		inword_offset = m;
		@(posedge clk)
		tempword = loadgen_datain >> (m * 8);
		`assert(loadgen_dataout, tempword[7:0]);
		`assert(loadgen_missaligned, 0);
		$display("Test unsigned byte - Done inword_offset=%d", inword_offset);
	end


	@(negedge clk)
	loadgen_type = `LOAD_HALF_UNSIGNED;
	loadgen_datain = 32'h8888_8888;
	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		inword_offset = m << 1;
		@(posedge clk)
		tempword = loadgen_datain >> (m * 16);
		`assert(loadgen_dataout, tempword[15:0]);
		`assert(loadgen_missaligned, 0);
		$display("Test aligned unsigned Halfword - Done inword_offset=%d", inword_offset);

		@(negedge clk)
		inword_offset = (m << 1) + 1;
		@(posedge clk)
		`assert(loadgen_missaligned, 1);
		$display("Test missaligned unsigned Halfword - Done inword_offset=%d", inword_offset);
	end

	@(negedge clk)
	loadgen_type = `LOAD_HALF;
	loadgen_datain = 32'h8888_8888;
	for(m = 0; m < 2; m = m + 1) begin
		@(negedge clk)
		inword_offset = m << 1;
		@(posedge clk)
		tempword = loadgen_datain >> (m * 16);
		`assert($signed(loadgen_dataout), $signed(tempword[15:0]));
		`assert(loadgen_missaligned, 0);
		$display("Test aligned signed Halfword - Done inword_offset=%d", inword_offset);

		@(negedge clk)
		inword_offset = (m << 1) + 1;
		@(posedge clk)
		`assert(loadgen_missaligned, 1);
		$display("Test missaligned signed Halfword - Done inword_offset=%d", inword_offset);
	end

	@(negedge clk)
	loadgen_type = `LOAD_WORD;
	loadgen_datain = 32'h8888_8888;
	inword_offset = 0;
	@(negedge clk)
	`assert(loadgen_missaligned, 0);
	`assert(loadgen_dataout, loadgen_datain);
	$display("Test aligned word = Done inword_offset=%d", inword_offset);

	for(m = 1; m < 4; m = m + 1) begin
		@(negedge clk)
		inword_offset = m;
		@(posedge clk)
		`assert(loadgen_missaligned, 1);
		$display("Test aligned word = Done inword_offset=%d", inword_offset);
	end
	

end
endmodule