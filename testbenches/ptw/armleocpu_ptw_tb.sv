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

reg [8192:0] pma_error;
reg [31:0] mem [8191:0];

always @* begin
	avl_waitrequest = avl_read;
end
always @(posedge clk) begin
	if(avl_read) begin
		
		avl_readdata <= mem[avl_address >> 2];
		avl_readdatavalid <= 1;
		
		if(pma_error[avl_address >> 2] === 1) begin
			avl_response = 2'b11;
		end else begin
			avl_response = 2'b00;
		end
	end else begin
		avl_readdatavalid <= 1;
		avl_response = 2'b11;
	end
end

// Test cases:
// Megapage PMA Error mem[1]
// Leaf PMA Error mem[2] mem[4096]
// valid Megapage and page w/
//               w/ rwx mem[3] || mem[8], mem[4097]
//               w/ rw mem[4]  || mem[9], mem[4098]
//               w/ rx mem[5]  || mem[10], mem[4099]
//               w/ r mem[6]   || mem[11], mem[4100]
//               w/ x mem[7]   || mem[12], mem[4101]
// Test for missaligned megapage mem[13]
// Test for missaligned page (should always be false) mem[14], mem[4102]
// Test for invalid megapage leaf
//                        w/ i mem[15]
//                        w/ w mem[16]
//                        w/ xw mem[17]
// Test for invalid leaf
//                        w/ i mem[18], mem[4102]
//                        w/ w mem[19], mem[4103]
//                        w/ xw mem[20], mem[4104]


initial begin
	// Megapage PMA Error mem[1]
	pma_error[1] = 1;
	mem[1] = 0;
	resolve_request = 1;
	resolve_virtual_address = {10'h1, 10'h0, 12'h001};
	@(negedge resolve_done)

	// Leaf PMA Error mem[2] mem[4096]
	pma_error[2] = 0;
	mem[2] = {12'h0, 10'h00, 10'h01};
	pma_error[4096] = 1;
	resolve_request = 1;
	resolve_virtual_address = {10'h2, 10'h0, 12'h001};
	@(negedge resolve_done)
	resolve_request = 0;
/*

	pma_error[2] = 0;
	mem[2] = {12'h001, 10'h000, 10'h000}; // invalid megapage
	
	pma_error[3] = 0;
	mem[3] = {12'h001, 10'h000, 10'h00F}; // aligned megapage
	mem[4] = {12'h001, 10'h001, 10'h00F}; // missaligned megapage
*/
	// mem[4096] = {}
	// mem[4097] = {}
end


endmodule