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
            $display($time, "ASSERTION FAILED in %m: signal != value"); \
            $finish; \
        end


initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
	#10000
	$finish;
end

reg resolve_request = 1'b0;
reg [31:0] resolve_virtual_address;

wire [19:0] virtual_address = resolve_virtual_address[31:12];

wire [33:0] avl_address;
wire avl_read;
reg avl_waitrequest, avl_readdatavalid;
reg [31:0] avl_readdata;
reg [1:0] avl_response;
wire resolve_ack, resolve_done, resolve_pagefault, resolve_accessfault;
wire [7:0] resolve_access_bits;
wire [21:0] resolve_physical_address;

wire matp_mode = 1;
wire [21:0] matp_ppn = 0;
wire [24:0] state_debug_output;

armleocpu_ptw ptw(
	.*
);

reg [8191:0] pma_error = 0;
reg [31:0] mem [8191:0];

always @* begin
	avl_waitrequest = !avl_read && !avl_readdatavalid;
end

wire k = pma_error[avl_address >> 2];
wire [31:0] m = avl_address >> 2;
always @(posedge clk) begin
	if(avl_read) begin
		avl_readdata <= mem[avl_address >> 2];
		avl_readdatavalid <= 1;
		
		if(pma_error[avl_address >> 2] === 1) begin
			avl_response <= 2'b11;
		end else begin
			avl_response <= 2'b00;
		end
	end else begin
		avl_readdatavalid <= 0;
		avl_response <= 2'b11;
	end
end

// Test cases:
// Megapage PMA Error mem[1]
// Leaf PMA Error mem[2] mem[1024]
// valid Megapage and page w/
//               w/ rwx mem[3] || mem[8], mem[1025]
//               w/ rw mem[4]  || mem[9], mem[1026]
//               w/ rx mem[5]  || mem[10], mem[1027]
//               w/ r mem[6]   || mem[11], mem[1028]
//               w/ x mem[7]   || mem[12], mem[1029]
// Test for missaligned megapage mem[13]
// Test for invalid megapage leaf
//                        w/ i mem[15]
//                        w/ w mem[16]
//                        w/ xw mem[17]
// Test for invalid leaf
//                        w/ i mem[18], mem[1031]
//                        w/ w mem[19], mem[1032]
//                        w/ xw mem[20], mem[1033]

reg [9:0] t = 0;
reg [9:0] r;
reg [9:0] w;
reg [9:0] x;

initial begin
	r = 10'b000000_0010;
	w = 10'b000000_0100;
	x = 10'b000000_1000;


	// Megapage PMA Error mem[1]
	pma_error[1] = 1;
	mem[1] = 0;
	resolve_request = 1;
	resolve_virtual_address = {10'h1, 10'h0, 12'h001};
	@(posedge resolve_done);
	`assert(resolve_done, 1'b1);
	`assert(resolve_accessfault, 1'b1);
	`assert(resolve_pagefault, 1'b0);

	// Leaf PMA Error mem[2] mem[1024]
	pma_error[2] = 0;
	mem[2] = {12'h0, 10'h01, 10'h01};
	pma_error[1024] = 1;


	$display("-------------- PMA Tests");
	resolve_request = 1;
	resolve_virtual_address = {10'h2, 10'h0, 12'h001};
	@(posedge clk)
	@(posedge clk)
	@(posedge clk)
	@(posedge clk)
	@(posedge clk)
	@(posedge clk)
	`assert(resolve_done, 1'b1);
	`assert(resolve_accessfault, 1'b1);
	`assert(resolve_pagefault, 1'b0);
	$display("------------- PMA Tests done\n\n");

	$display("------------- Megapage valid leaf Tests");
	mem[3] = {12'h1, 10'h00, 10'h01} | r | w | x;
	mem[4] = {12'h1, 10'h00, 10'h01} | r | w;
	mem[5] = {12'h1, 10'h00, 10'h01} | r | x;
	mem[6] = {12'h1, 10'h00, 10'h01} | r;
	mem[7] = {12'h1, 10'h00, 10'h01} | x;


	

	mem[1025] = mem[3];
	mem[1026] = mem[4];
	mem[1027] = mem[5];
	mem[1028] = mem[6];
	mem[1029] = mem[7];


	t = 3;
	repeat(5) begin
	resolve_request = 0;
	resolve_virtual_address = {t, 10'h0, 12'h001};
	@(posedge clk)
	@(posedge clk)
	@(posedge clk)
	resolve_request = 1;
	resolve_virtual_address = {t, 10'h0, 12'h001};
	@(posedge clk)
	@(posedge clk)
	resolve_request = 0;
	`assert(resolve_done, 1'b1);
	`assert(resolve_pagefault, 1'b0);
	`assert(resolve_accessfault, 1'b0);
	`assert(resolve_access_bits, avl_readdata[9:0]);
	`assert(resolve_physical_address, {avl_readdata[31:20], 10'h0});
	$display("------------- Megapage valid leaf for case N = %d/5\n", t - 2);
	t = t + 1;
	end
	$display("------------- Megapage valid leaf Tests done\n\n");


	resolve_request = 0;
	resolve_virtual_address = {10'd13, 10'h0, 12'h001};
	@(posedge clk)
	@(posedge clk)
	@(posedge clk)



	// missaligned megapage
	mem[13] = {12'h1, 10'h01, 10'h01} | r | w | x;

	resolve_request = 1;
	resolve_virtual_address = {1'd13, 10'h0, 12'h001};
	@(posedge clk)
	@(posedge clk)
	resolve_request = 0;
	`assert(resolve_done, 1'b1);
	`assert(resolve_pagefault, 1'b1);
	`assert(resolve_accessfault, 1'b0);
end


endmodule