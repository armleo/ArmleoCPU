
reg clk = 0;
reg rst_n = 1;
reg clk_enable = 0;
initial begin
	#1 rst_n = 0;
	#1 rst_n = 1;
	#1 clk_enable = 1;
end
always begin
	#1 clk <= clk_enable ? !clk : clk;
end
`include "assert.vh"

initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
end

