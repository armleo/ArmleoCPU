`timescale 1ns/1ns

module tlb_way_testbench;

`include "../sync_clk_gen_template.inc"

initial begin
	#500
	$finish;
end

reg [1:0] command;
// invalidate
reg [ENTRIES_W-1:0] invalidate_set_index;
// write
reg [7:0]  accesstag_w;
reg [21:0] phys_w;
reg [19:0]	virtual_address_w;

// read
reg [19:0]	virtual_address;
wire hit;
wire [7:0] accesstag_r;
wire [21:0] phys_r;

localparam ENTRIES_W = 1;

armleocpu_tlb #(ENTRIES_W, 1, 0) tlb(
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

	// invalidate all
	@(negedge clk)
	command <= `TLB_CMD_INVALIDATE;
	invalidate_set_index <= 0;
	@(posedge clk)
	@(negedge clk)
	invalidate_set_index <= 1;
	@(posedge clk)

	// tlb invalidate done

	// tlb write 100 -> F5
	@(negedge clk)

	command <= `TLB_CMD_WRITE;
	accesstag_w = 8'hFF;
	phys_w <= 22'hF5;
	virtual_address_w <= 20'h100;
	@(negedge clk)

	// tlb write 101 -> F5
	command <= `TLB_CMD_WRITE;
	accesstag_w = 8'hFF;
	phys_w <= 22'hF5;
	virtual_address_w <= 20'h101;
	@(negedge clk)

	// tlb write 55 -> FE
	command <= `TLB_CMD_WRITE;
	accesstag_w = 8'hFF;
	phys_w <= 22'hFE;
	virtual_address_w <= 20'h55;
	@(negedge clk)

	// tlb write 56 -> F5
	command <= `TLB_CMD_WRITE;
	accesstag_w = 8'hFF;
	phys_w <= 22'hF5;
	virtual_address_w <= 20'h56;
	@(negedge clk)
	
	// resolve test 55 -> FE
	command <= `TLB_CMD_RESOLVE;
	virtual_address <= 20'h55;

	@(negedge clk)

	`assert(hit, 1'b1);
	`assert(accesstag_r, 8'hFF);
	`assert(phys_r, 22'hFE);

	// resolve test 56 -> F5
	virtual_address <= 20'h56;

	@(negedge clk)

	`assert(hit, 1'b1);
	`assert(accesstag_r, 8'hFF);
	`assert(phys_r, 22'hF5);

	// resolve test 100 -> F5
	virtual_address <= 20'h100;

	@(negedge clk)

	`assert(hit, 1'b1);
	`assert(accesstag_r, 8'hFF);
	`assert(phys_r, 22'hF5);

	// resolve test 101 -> F5
	virtual_address <= 20'h101;

	@(negedge clk)

	`assert(hit, 1'b1);
	`assert(accesstag_r, 8'hFF);
	`assert(phys_r, 22'hF5);

	// invalidate requests

	command <= `TLB_CMD_INVALIDATE;
	invalidate_set_index <= 0;
	@(posedge clk)
	@(negedge clk)
	invalidate_set_index <= 1;
	@(posedge clk)
	@(negedge clk)
	command <= `TLB_CMD_RESOLVE;
	virtual_address <= 20'h55;
	// test invalidation
	@(negedge clk)
	`assert(hit, 1'b0);
	virtual_address <= 20'h56;
	@(negedge clk)
	`assert(hit, 1'b0);
	virtual_address <= 20'h100;
	@(negedge clk)
	`assert(hit, 1'b0);
	virtual_address <= 20'h101;
	@(negedge clk)
	`assert(hit, 1'b0);
	$finish;
end


endmodule