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
// Filename:    armleocpu_tlb.v
// Project:	ArmleoCPU
//
// Purpose:	RISC-V TLB for ArmleoCPU. Only accepts 4K pages.
// Parameters:
//      ENTRIES_W:
//          Specifies how many entries will be stored in this TLB by following formula:
//              ENTRIES = 2**ENTRIES_W;
//      WAYS:
//          How many ways are implemented.
//          
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE


module armleocpu_tlb(
    clk, rst_n, cmd, vaddr_input, hit, resolve_metadata_output, resolve_ptag_output, resolve_way, new_entry_metadata_input, new_entry_ptag_input);

    parameter ENTRIES_W = 4;
    localparam ENTRIES = 2**ENTRIES_W;
    parameter WAYS = 3;
    localparam WAYS_CLOG2 = $clog2(WAYS);


    
    input clk;
    input rst_n;

    
    // commands
    input [1:0]         cmd;

    input       [19:0]  vaddr_input; // Used to resolve ptag/accesstag
    
    
    output reg          hit; // TLB has data about that page
    // Accesstag and PTAG output for resolution result
    output reg   [7:0]  resolve_metadata_output; // valid only if hit is set
    output reg  [21:0]  resolve_ptag_output; // valid only if hit is set
    output reg  [WAYS_CLOG2-1:0]
                        resolve_way; // way which had the data

    // Data input for writing
    // Victim is selected by TLB
    // vaddr_input is used to select which lane to write it to
    input        [7:0]  new_entry_metadata_input;
    input       [21:0]  new_entry_ptag_input;


reg valid_write;
reg valid_invalidate;

reg [ENTRIES-1:0] valid[WAYS-1:0]; // Metadata LSB bit
genvar way_num;
generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : valid_reg_for
    always @(posedge clk) begin
        if(valid_invalidate)
            valid[way_num] <= 0;
        if(valid_write && victim_way == way_num)
            valid[way_num][vaddr_input_entry_index] <= new_entry_metadata_input[0];
    end
end
endgenerate


`DEFINE_REG_REG_NXT(WAYS_CLOG2, victim_way, victim_way_nxt, clk)
`DEFINE_REG_REG_NXT((20-ENTRIES_W), output_stage_vtag, output_stage_vtag_nxt, clk)
`DEFINE_REG_REG_NXT((ENTRIES_W), output_stage_entry_index, output_stage_entry_index_nxt, clk)

wire [ENTRIES_W-1:0] vaddr_input_entry_index = vaddr_input[ENTRIES_W-1:0];
wire [20-ENTRIES_W-1:0] vaddr_input_vtag = vaddr_input[19:ENTRIES_W];

reg read;
reg [WAYS-1:0] write;

wire [20-ENTRIES_W-1:0] vtag_readdata       [WAYS-1:0];
wire [21:0]             ptag_readdata       [WAYS-1:0];
wire  [6:0]             metadata_readdata   [WAYS-1:0];


generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : mem_generate_for

    armleocpu_mem_1rw #(
        .ELEMENTS_W(ENTRIES_W),
        .WIDTH(20-ENTRIES_W)
    ) vtag_storage (
        .clk(clk),
        .address(vaddr_input_entry_index),

        .read(read),
        .readdata(vtag_readdata[way_num]),

        .write(write[way_num]),
        .writedata(vaddr_input_vtag)
    );

    armleocpu_mem_1rw #(
        .ELEMENTS_W(ENTRIES_W),
        .WIDTH(22)
    ) ptag_storage (
        .clk(clk),
        .address(vaddr_input_entry_index),

        .read(read),
        .readdata(ptag_readdata[way_num]),

        .write(write[way_num]),
        .writedata(new_entry_ptag_input)
        
    );

    armleocpu_mem_1rw #(
        .ELEMENTS_W(ENTRIES_W),
        .WIDTH(7)
    ) metadata_storage (
        .clk(clk),
        .address(vaddr_input_entry_index),

        .read(read),
        .readdata(metadata_readdata[way_num]),

        .write(write[way_num]),
        .writedata(new_entry_metadata_input[7:1])
    );
end
endgenerate

always @* begin

    write = 0;
    read = 0;

    victim_way_nxt = victim_way;
    output_stage_vtag_nxt = output_stage_vtag;
    output_stage_entry_index_nxt = output_stage_entry_index;
    valid_invalidate = 0;
    valid_write = 0;

    if((cmd == `TLB_CMD_INVALIDATE_ALL) || !rst_n) begin
        valid_invalidate = 1;
        victim_way_nxt = 0;
    end else if(cmd == `TLB_CMD_RESOLVE) begin
        read = 1;
        output_stage_vtag_nxt = vaddr_input_vtag;
        output_stage_entry_index_nxt = vaddr_input_entry_index;
    end else if(cmd == `TLB_CMD_NEW_ENTRY) begin
        write[victim_way] = 1;
        valid_write = 1;
        
        // verilator lint_off WIDTH
        if(victim_way == WAYS-1)
        // verilator lint_on WIDTH
            victim_way_nxt = 0;
        else
            victim_way_nxt = victim_way + 1;
    end
end

reg [WAYS-1:0] tlbway_hit;

integer i;
always @* begin
    resolve_way = 0;
    for(i = 0; i < WAYS; i = i + 1) begin
        tlbway_hit[i] = valid[i][output_stage_entry_index] && (output_stage_vtag == vtag_readdata[i]);
    end

    hit = tlbway_hit[0];
    
    resolve_ptag_output = ptag_readdata[0];
    resolve_metadata_output = {metadata_readdata[0], valid[0][output_stage_entry_index]};

    for(i = 0; i < WAYS; i = i + 1) begin
        if(tlbway_hit[i]) begin
            /* verilator lint_off WIDTH */
            resolve_way = i;
            /* verilator lint_on WIDTH */
            hit = tlbway_hit[resolve_way];
            resolve_ptag_output = ptag_readdata[resolve_way];
            resolve_metadata_output = {metadata_readdata[resolve_way], valid[resolve_way][output_stage_entry_index]};
        end
    end
end

endmodule


`include "armleocpu_undef.vh"
