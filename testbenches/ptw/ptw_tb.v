`timescale 1ns/1ns
module ptw_testbench;

`include "../sync_clk_gen_template.vh"

`include "armleobus_defs.vh"

initial begin
	#10000
	$finish;
end

reg resolve_request = 1'b0;
reg [31:0] resolve_virtual_address;

wire [19:0] virtual_address = resolve_virtual_address[31:12];


wire 		m_transaction;
wire [2:0]  m_cmd;
wire [33:0] m_address;
reg [2:0]  m_transaction_response;
reg        m_transaction_done;
wire [31:0] m_rdata;

wire temp_m_transaction_done;
wire [2:0]  temp_m_transaction_response;

wire resolve_ack, resolve_done, resolve_pagefault, resolve_accessfault;
wire [7:0] resolve_access_bits;
wire [21:0] resolve_physical_address;

wire satp_mode = 1;
wire [21:0] satp_ppn = 0;

armleocpu_ptw ptw(
	.*
);

reg [8191:0] pma_error = 0;
always @* begin
	if((pma_error[m_address >> 2] === 1) && temp_m_transaction_done) begin
		m_transaction_done = 1;
		m_transaction_response = `ARMLEOBUS_UNKNOWN_ADDRESS;
	end else begin
		m_transaction_done = temp_m_transaction_done;
		m_transaction_response = temp_m_transaction_response;
	end
end

armleobus_scratchmem #(16, 2) scratchmem(
	.clk(clk),

	.transaction(m_transaction),
	.cmd(m_cmd),
	.transaction_done(temp_m_transaction_done),
	.transaction_response(temp_m_transaction_response),
	.address(m_address[17:0]),
	.wdata(),
	.wbyte_enable(),
	.rdata(m_rdata)
);

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
//                        w/ i mem[14]
//                        w/ w mem[15]
//                        w/ xw mem[16]
// Test for invalid leaf
//                        w/ i mem[17], mem[1030]
//                        w/ w mem[18], mem[1031]
//                        w/ xw mem[19], mem[1032]

reg [9:0] t = 0;
reg [9:0] r;
reg [9:0] w;
reg [9:0] x;
reg [31:0] resolve_physical_address_expected;
reg dummy = 0;
initial begin
	r = 10'b000000_0010;
	w = 10'b000000_0100;
	x = 10'b000000_1000;

	$display("-------------- PMA Tests ----------- BEGIN ------");
	// Megapage PMA Error mem[1]
	pma_error[1] = 1;
	scratchmem.mem[1] = 0;
	@(negedge rst_n)
	@(posedge rst_n)
	resolve_request = 1;
	resolve_virtual_address = {10'h1, 10'h0, 12'h001};
	@(posedge clk);
	@(posedge clk);
	@(negedge clk);
	@(negedge clk);
	`assert(resolve_done, 1'b1);
	`assert(resolve_accessfault, 1'b1);
	`assert(resolve_pagefault, 1'b0);


	// Leaf PMA Error mem[2] mem[1024]
	pma_error[2] = 0;
	scratchmem.mem[2] = {12'h0, 10'h01, 10'h01};
	pma_error[1024] = 1;


	
	resolve_request = 1;
	resolve_virtual_address = {10'h2, 10'h0, 12'h001};
	@(posedge clk)
	// send request cycle


	@(posedge clk)
	@(posedge clk)
	// read done cycle
	@(posedge clk)
	@(posedge clk)
	// leaf read done: wait for negedge for outputs to be stable
	@(negedge clk)
	@(negedge clk);
	@(negedge clk);
	`assert(resolve_done, 1'b1);
	`assert(resolve_accessfault, 1'b1);
	`assert(resolve_pagefault, 1'b0);
	// IDLE cycles
	resolve_request = 0;
	@(posedge clk)
	
	$display("------------- PMA Tests done ----------- END ------\n\n");





	$display("------------- Megapage valid leaf Tests ----------- BEGIN ------");
	scratchmem.mem[3] = {12'h1, 10'h00, 10'h01} | r | w | x;
	scratchmem.mem[4] = {12'h1, 10'h00, 10'h01} | r | w;
	scratchmem.mem[5] = {12'h1, 10'h00, 10'h01} | r | x;
	scratchmem.mem[6] = {12'h1, 10'h00, 10'h01} | r;
	scratchmem.mem[7] = {12'h1, 10'h00, 10'h01} | x;




	scratchmem.mem[1025] = scratchmem.mem[3];
	scratchmem.mem[1026] = scratchmem.mem[4];
	scratchmem.mem[1027] = scratchmem.mem[5];
	scratchmem.mem[1028] = scratchmem.mem[6];
	scratchmem.mem[1029] = scratchmem.mem[7];


	t = 3;
	repeat(5) begin
		resolve_request = 0;
		resolve_virtual_address = {t, 10'h0, 12'h001};
		@(posedge clk)
		// Idle cycles

		// Request
		@(negedge clk)
		resolve_request = 1;
		@(posedge clk)
		// read request
		@(posedge clk)
		// read request done
		@(negedge clk)
		@(negedge clk);
		resolve_request = 0;
		`assert(resolve_done, 1'b1);
		`assert(resolve_pagefault, 1'b0);
		`assert(resolve_accessfault, 1'b0);
		`assert(resolve_access_bits, m_rdata[9:0]);
		resolve_physical_address_expected = {m_rdata[31:20], 10'h0};
		`assert(resolve_physical_address, resolve_physical_address_expected);
		$display("------------- Megapage valid leaf for case N = %d/5 done\n", t - 2);
		t = t + 1;
	end
	@(negedge clk)
	resolve_request = 0;
	resolve_virtual_address = {10'd13, 10'h0, 12'h001};
	@(posedge clk)
	$display("------------- Megapage valid leaf Tests done ----------- END ------\n\n");

	resolve_request = 0;
	resolve_virtual_address = {10'd13, 10'h0, 12'h001};
	@(posedge clk)
	@(posedge clk)
	



	// missaligned megapage
	$display("------------- Missaligned Megapage Tests ----------- BEGIN ------\n\n");
	@(negedge clk)
	scratchmem.mem[13] = {12'h1, 10'h01, 10'h01} | r | w | x;

	resolve_request = 1;
	resolve_virtual_address = {10'd13, 10'h0, 12'h001};
	@(posedge clk)
	@(posedge clk)
	@(negedge clk)
	@(negedge clk);
	resolve_request = 0;
	`assert(resolve_done, 1'b1);
	`assert(resolve_pagefault, 1'b1);
	`assert(resolve_accessfault, 1'b0);
	@(posedge clk)
	@(posedge clk)
	dummy = dummy;
	$display("------------- Missaligned Megapage Tests ----------- DONE ------\n\n");

	$display("------------- Invalid Megapage Tests ----------- BEGIN ------\n\n");
	// Test for invalid megapage leaf
	//                        w/ i mem[14]
	//                        w/ w mem[15]
	//                        w/ xw mem[16]
	scratchmem.mem[14] = {22'h0, 10'h0};
	scratchmem.mem[15] = {22'h0, 10'h1} | w;
	scratchmem.mem[16] = {22'h0, 10'h1} | x | w;


	for(t = 0; t < 3; t = t + 1) begin
		@(negedge clk)

		resolve_request = 1;
		resolve_virtual_address = {10'd14 + t, 10'h0, 12'h001};

		@(posedge clk)
		@(posedge clk)
		@(posedge clk)
		@(posedge clk)
		`assert(resolve_done, 1'b1);
		`assert(resolve_pagefault, 1'b1);
		`assert(resolve_accessfault, 1'b0);
		dummy = dummy;
	end
	resolve_request = 0;
	@(posedge clk)
	@(posedge clk)
	@(posedge clk)
	$display("------------- Invalid Megapage Tests ----------- DONE ------\n\n");
	dummy = dummy;


	// Test for invalid leaf
	//                        w/ i mem[17], mem[1030]
	//                        w/ w mem[18], mem[1031]
	//                        w/ xw mem[19], mem[1032]
	$display("------------- Invalid leaf page Tests ----------- BEGIN ------\n\n");
	

	scratchmem.mem[17] = {22'h1, 10'h1};
	scratchmem.mem[18] = {22'h1, 10'h1};
	scratchmem.mem[19] = {22'h1, 10'h1};
	scratchmem.mem[1030] = {22'h0, 10'h0};
	scratchmem.mem[1031] = {22'h0, 10'h1} | w;
	scratchmem.mem[1032] = {22'h0, 10'h1} | x | w;


	for(t = 0; t < 3; t = t + 1) begin
		@(negedge clk)

		resolve_request = 1;
		resolve_virtual_address = {10'd17 + t, 10'h6 + t, 12'h001};

		@(posedge clk)
		@(posedge clk)
		@(posedge clk)
		@(posedge clk)
		@(posedge clk)
		@(posedge clk)
		@(posedge clk)
		`assert(resolve_done, 1'b1);
		`assert(resolve_pagefault, 1'b1);
		`assert(resolve_accessfault, 1'b0);
		dummy = dummy;
	end
	resolve_request = 0;
	@(posedge clk)
	@(posedge clk)
	@(posedge clk)
	$display("------------- Invalid leaf page Tests ----------- DONE ------\n\n");
	dummy = dummy;
end


endmodule