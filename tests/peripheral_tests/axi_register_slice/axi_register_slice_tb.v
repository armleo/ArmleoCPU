`timescale 1ns/1ns

module axi_register_slice_testbench;


initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
	#1000
	$display("!ERROR! End reached but test is not done");
	$fatal;
end

reg clk = 0;
reg rst_n = 1;
reg clk_enable = 0;
initial begin
	clk_enable = 1;
	rst_n = 0;
	#20 rst_n = 1;
end
always begin
	#10 clk <= clk_enable ? !clk : clk;
end

`include "assert.vh"

localparam DW = 16;

reg in_valid;
reg [DW-1:0] in_data;
wire in_ready;

wire out_valid;
wire [DW-1:0] out_data;
reg out_ready;

armleocpu_axi_register_slice #(DW) axi_register_slice (
	.*
);



initial begin
	in_valid = 0;
	out_ready = 0;
	@(posedge rst_n)
	@(negedge clk)
	`assert_equal(out_valid, 0)

	$display("Test case: Input is fed and no buffered data, no stall");
	in_valid = 1;
	in_data = 16'hFE0B;
	`assert_equal(in_ready, 1)
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0B)
	out_ready = 1;
	in_valid = 0;

	@(negedge clk)
	`assert_equal(out_valid, 0)
	



	$display("Test case: Two cycles Input is fed and no buffered data, no stall");
	in_valid = 1;
	in_data = 16'hFE0A;
	`assert_equal(in_ready, 1)
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0A)
	out_ready = 1;
	in_data = 16'hFE0C;
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0C)
	in_valid = 0;
	@(negedge clk)
	`assert_equal(out_valid, 0)
	

	$display("Test case: One cycle, then output is stalled");
	in_valid = 1;
	in_data = 16'hFE0A;
	`assert_equal(in_ready, 1)
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0A)
	in_data = 16'hFE0B;
	`assert_equal(in_ready, 1)
	out_ready = 0;
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0A)
	out_ready = 1;
	in_valid = 0;
	@(negedge clk)
	`assert_equal(out_valid, 1)
	`assert_equal(out_data, 16'hFE0B)


	@(negedge clk)
	@(negedge clk)
	$finish;
end


endmodule