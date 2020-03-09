`timescale 1ns/1ns
module ptw_testbench;

`include "../clk_gen_template.svh"


initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
	#100
	$finish;
end

endmodule