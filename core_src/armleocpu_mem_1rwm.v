`timescale 1ns/1ns

module armleocpu_mem_1rwm (clk, address, read, readdata, write, writeenable, writedata);
	parameter ELEMENTS_W = 7;
	localparam ELEMENTS = 2**ELEMENTS_W;
	parameter WIDTH = 32;
	parameter GRANULITY = 8;
	localparam ENABLE_WIDTH = WIDTH/GRANULITY;

	input clk;

    input [ELEMENTS_W-1:0] address;
    input read;
    output wire [WIDTH-1:0] readdata;

	input write;
	input [WIDTH/GRANULITY-1:0] writeenable;
	input [WIDTH-1:0] writedata;

`ifdef SIMULATION
	initial begin
		if((WIDTH % GRANULITY) != 0) begin
			$display("Width is not divisible by granulity");
			$fatal;
		end
	end
`endif

genvar i;
generate for(i = 0; i < WIDTH; i = i + GRANULITY) begin : mem_generate_for
	armleocpu_mem_1rw #(ELEMENTS_W, GRANULITY) storage(
		.clk(clk),
		
		.address(address),

		.read(read),
		.readdata(readdata[i + GRANULITY - 1 : i]),

		.write(write & writeenable[i/GRANULITY]),
		.writedata(writedata[i + GRANULITY - 1 : i])
	);
end
endgenerate


endmodule