////////////////////////////////////////////////////////////////////////////////
// 
// This file is part of ArmleoCPU.
// ArmleoCPU is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// ArmleoCPU is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with ArmleoCPU.  If not, see <https://www.gnu.org/licenses/>.
// 
// Copyright (C) 2016-2021, Arman Avetisyan, see COPYING file or LICENSE file
// SPDX-License-Identifier: GPL-3.0-or-later
// 

`define TIMEOUT 1000
`define SYNC_RST
`define CLK_HALF_PERIOD 1

`include "template.vh"

reg [1:0] cmd;

// write
reg [7:0]  new_entry_metadata_input;
reg [21:0] new_entry_ptag_input;
reg [19:0]	vaddr_input;

// result
wire hit;
wire [7:0] resolve_metadata_output;
wire [21:0] resolve_ptag_output;
wire [1:0] resolve_way;

localparam ENTRIES_W = 1;

armleocpu_tlb #(ENTRIES_W, 3) tlb(
	.*
);

/*
genvar i;
for(i = 0; i < 3; i = i + 1)
	initial $dumpvars(1, tlb.valid[i]);
for(i = 0; i < 3; i = i + 1)
	initial $dumpvars(1, tlb.valid_nxt[i]);
*/

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

task tlb_write;
input [19:0] vaddr;
input [7:0] metadata;
input [21:0] ptag;
begin
	cmd <= `TLB_CMD_NEW_ENTRY;
	vaddr_input <= vaddr;
	new_entry_metadata_input <= metadata;
	new_entry_ptag_input <= ptag;
	@(negedge clk)
	cmd <= `TLB_CMD_NONE;
end
endtask

task tlb_resolve;
input [19:0] vaddr;
begin
	cmd <= `TLB_CMD_RESOLVE;
	vaddr_input <= vaddr;
	@(negedge clk)
	cmd <= `TLB_CMD_NONE;
end
endtask


initial begin
	@(posedge rst_n)

	// invalidate all
	@(negedge clk)
	cmd <= `TLB_CMD_INVALIDATE_ALL;
	@(negedge clk)
	cmd <= `TLB_CMD_NONE;

	// tlb invalidate done

	// tlb write 100 -> F5
	tlb_write(20'h100, 8'hFF, 22'hF5); // way = 0

	// tlb write 101 -> F, F1
	tlb_write(20'h101, 8'hF, 22'hF1); // way = 1

	// tlb write 55 -> FE
	tlb_write(20'h55, 8'hFF, 22'hFE); // way = 2
	

	// tlb write 56 -> F5
	tlb_write(20'h56, 8'hFF, 22'hF5); // way = 0
	
	
	$display("resolve test 55 -> FE");
	tlb_resolve(20'h55);
	`assert_equal(hit, 1'b1);
	`assert_equal(resolve_ptag_output, 22'hFE);
	`assert_equal(resolve_metadata_output, 8'hFF);
	`assert_equal(resolve_way, 2);


	$display("resolve test 56 -> F5");
	tlb_resolve(20'h56);

	`assert_equal(hit, 1'b1);
	`assert_equal(resolve_ptag_output, 22'hF5);
	`assert_equal(resolve_metadata_output, 8'hFF);
	`assert_equal(resolve_way, 0);

	$display("resolve test 100");
	tlb_resolve(20'h100);
	`assert_equal(hit, 1'b0); // Overwritten by 56


	$display("resolve test 101 -> F, F1");
	tlb_resolve(20'h101);
	`assert_equal(hit, 1'b1);
	`assert_equal(resolve_ptag_output, 22'hF1);
	`assert_equal(resolve_metadata_output, 8'hF);
	`assert_equal(resolve_way, 1);

	// invalidate requests

	cmd <= `TLB_CMD_INVALIDATE_ALL;
	@(negedge clk)
	
	tlb_resolve(20'h55);
	// test invalidation
	`assert_equal(hit, 1'b0);

	tlb_resolve(20'h56);
	`assert_equal(hit, 1'b0);

	tlb_resolve(20'h100);
	`assert_equal(hit, 1'b0);

	tlb_resolve(20'h101);
	`assert_equal(hit, 1'b0);


	// TODO: Add more tests testing limits of TLB (including victim_way checking)
	$finish;
end


endmodule