
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

`define assert(signal, value) \
        if ((signal) !== (value)) begin \
            $display("[%d] ASSERTION FAILED in %m: signal(%d) != value(%d)", $time, signal, value); \
             $finish_and_return(1); \
        end

