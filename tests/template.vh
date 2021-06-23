`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module `TOP_TB();

`ifndef CLK_HALF_PERIOD
    `define CLK_HALF_PERIOD 1
`endif

reg clk = 0;
reg rst_n = 1;
reg clk_enable = 0;

`ifndef ASYNC_RST
`define SYNC_RST
`endif

`ifdef ASYNC_RST
initial begin
	#`CLK_HALF_PERIOD rst_n = 0;
	#`CLK_HALF_PERIOD rst_n = 1;
	#`CLK_HALF_PERIOD clk_enable = 1;
end
`endif

`ifdef SYNC_RST
initial begin
    rst_n = 0;
	clk_enable = 1;
	#`CLK_HALF_PERIOD; #`CLK_HALF_PERIOD rst_n = 1;
end
`endif

always begin
	#`CLK_HALF_PERIOD  clk <= clk_enable ? !clk : clk;
end

`ifndef TIMEOUT
`define TIMEOUT 1000
`endif


`include "assert.vh"


initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars(0, `TOP_TB);
    #`TIMEOUT
    `assert_equal(0, 1)
end



