`timescale 1ns/1ns
module ptw_testbench;

reg clk = 0;
reg async_rst_n = 1;

initial begin
	#1 async_rst_n = 0;
	#1 async_rst_n = 1;
	
end
always begin
	#5 clk <= !clk;
end

`define assert(signal, value) \
        if (signal !== value) begin \
            $display("ASSERTION FAILED in %m: signal != value"); \
            $finish; \
        end


initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
	#100
	$finish;
end

reg [31:0] mem [8191:0];



ptw_resolve_request = 1
ptw_resolve_virtual_address = {20'hF00A1, 12'hFFF};




endmodule