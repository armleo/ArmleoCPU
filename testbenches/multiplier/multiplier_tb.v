`timescale 1ns/1ns
module multiplier_testbench;

`include "../clk_gen_template.vh"

initial begin
	#1000
	`assert(1, 0);
	$finish;
end

reg         valid;
reg [31:0]  factor0;
reg [31:0]  factor1;

wire         ready;
wire [63:0]  result;

armleocpu_multiplier mult(
	.*
);

initial begin
	valid <= 0;
	@(posedge rst_n);
	@(posedge clk)
	valid <= 1;
	factor0 <= 64;
	factor1 <= 53;
	@(posedge clk)
	valid <= 0;
	while(ready != 1)
		@(posedge clk);
	`assert(result, 64*53);
	@(posedge clk);
	valid <= 1;
	factor0 <= 32'hFFFF_FFFF;
	factor1 <= 32'hFFFF_FFFF;
	@(posedge clk)
	valid <= 0;

	while(ready != 1)
		@(posedge clk);
	`assert(result, 64'hFFFF_FFFE_0000_0001);
	`assert(result, 64'hFFFF_FFFF * 64'hFFFF_FFFF);
	@(posedge clk);
	$finish;
end
endmodule