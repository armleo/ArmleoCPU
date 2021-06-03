`timescale 1ns/1ns
module cache_tb;

reg clk = 0;
reg rst_n = 1;
reg clk_enable = 0;
initial begin
	clk_enable = 1;
	rst_n = 0;
	#2 rst_n = 1;
end
always begin
	#1 clk <= clk_enable ? !clk : clk;
end

`include "assert.vh"

initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
end

// TODO: Add the axi4 ram

initial begin
	// TODO: Write tests
	$finish;
end


endmodule