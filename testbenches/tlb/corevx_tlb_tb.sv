`timescale 1ns/1ns

module tlb_testbench;

`include "../sync_clk_gen_template.svh"

initial begin
	#500
	$finish;
end

reg [1:0] command;
// invalidate
reg [ENTRIES_W:0] invalidate_set_index;
// write
reg [7:0]  accesstag_w;
reg [21:0] phys_w;
reg [19:0]	virtual_address_w;

// read
reg [19:0]	virtual_address;
wire hit;
wire [7:0] accesstag_r;
wire [21:0] phys_r;


corevx_tlb tlb(
	.*
);

/*
	Test cases:
		invalidate all
		resolve w/ invalid -> miss
		write valid entry
			to 0 entry
			to 1 entry
			to 2 entry
		resolve
			from 1, 2, 3 entry (8,9, 10,11, 12,13)
		write valid entry
			to 0 entry with different tag
			to 1 entry with different tag
			to 2 entry with different tag
		resolve
			from 0, 1, 2 entry
		resolve to other entry -> miss
		invalidate
		resolve -> miss
		write valid entry
			to 0 entry
		resolve -> hit
		resolve to other entry -> miss
*/

initial begin
	@(posedge rst_n)
	@(negedge clk)
	command
	/*
	@(posedge rst_n)

	@(negedge clk) // 0 cycle begin
			$display("Testing enable = 0");
			invalidate = 0;
			enable = 0;
			write = 0;
			resolve = 1;
			virtual_address = 32'h0000_0000;
	@(posedge clk)  // 0 cycle end
		`assert(done, 0)
		`assert(miss, 0)
	@(posedge clk) // 1 cycle end
		`assert(done, 1)
		`assert(miss, 0)
		$display("Testing enable = 0 done");
	@(negedge clk) // 2 cycle begin
		resolve = 0;
	@(negedge clk) // 3 cycle begin
		$display("Testing resolve to invalid");
		enable = 1;
		resolve = 1;
	@(negedge clk) // 4 cycle begin
		resolve = 0;
	@(posedge clk) // 4 cycle end
		`assert(done, 1)
		`assert(miss, 1)
		$display("Testing resolve to invalid done");
	@(negedge clk) // 5 cycle begin
		$display("Testing write");
		write = 1;
		virtual_address_w = 20'h2_0000;
		phys_w = 22'h1_0000;
		accesstag_w = 8'b1011_0001;
	@(negedge clk) // 6 cycle begin
		write = 1;
		virtual_address_w = 20'h2_0001;
		phys_w = 22'h1_0001;
		accesstag_w = 8'b1011_0011;
	@(negedge clk) // 7 cycle begin
		write = 1;
		virtual_address_w = 20'h2_0002;
		phys_w = 22'h1_0002;
		accesstag_w = 8'b1011_0101;
	@(negedge clk) // 8 cycle begin
		write = 0;
		virtual_address = 20'h2_0000;
		resolve = 1;
	@(negedge clk) // 9 cycle begin
		resolve = 0;
	@(posedge clk)
		`assert(done, 1)
		`assert(miss, 0)
		`assert(accesstag_r, 8'b1011_0001);
		`assert(phys_r, 20'h1_0000);
		
	@(negedge clk) // 10 cycle begin
		virtual_address = 20'h2_0001;
		enable = 1;
		resolve = 1;
	@(negedge clk) // 11 cycle begin
		resolve = 0;
	
	@(posedge clk) // 11 cycle end
		`assert(done, 1)
		`assert(miss, 0)
		`assert(accesstag_r, 8'b1011_0011);
		`assert(phys_r, 20'h1_0001);
	@(negedge clk) // 12 cycle begin
		virtual_address = 20'h2_0002;
		enable = 1;
		resolve = 1;
	@(negedge clk) // 13 cycle begin
		resolve = 0;
	@(posedge clk) // 13 cycle end
		`assert(done, 1)
		`assert(miss, 0)
		`assert(accesstag_r, 8'b1011_0101);
		`assert(phys_r, 20'h1_0002);
		$display("Testing write done");
	@(negedge clk) // 14 cycle begin
		$display("Testing invalidate");
		invalidate = 1;
	@(negedge clk) // 15 cycle begin
		invalidate = 0;
		virtual_address = 20'h2_0002;
		enable = 1;
		resolve = 1;
	@(negedge clk) // 16 cycle begin
		resolve = 0;
	@(posedge clk) // 16 cycle end
		`assert(done, 1)
		`assert(miss, 1)
	@(negedge clk) // 17 cycle begin
		invalidate = 0;
		enable = 0;
		write = 0;
		resolve = 0;
		virtual_address = 32'h0000_0000;
	$display("Testing done");
	*/
end


endmodule