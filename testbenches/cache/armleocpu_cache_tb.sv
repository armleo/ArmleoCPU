`timescale 1ns/1ns
module cache_testbench;

`include "../clk_gen_template.svh"

`include "../../src/armleocpu_defs.sv"

initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
	#100
	$finish;
end



/*
task read();
input cached;
input vtag, set, way, offset;
input load_type;

@(negedge clk);

@(posedge clk);
// assertions and waits

endtask

initial begin

end

*/


endmodule