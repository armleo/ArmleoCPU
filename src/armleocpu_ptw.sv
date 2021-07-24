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
// Filename: armleocpu_ptw.v
// Project:	ArmleoCPU
//
// Purpose:	RISC-V SV32 Page table walker, always outputs 4K Pages
//		
//
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE



module armleocpu_ptw(
    input clk,
    input rst_n,

    output reg          axi_arvalid,
    input wire          axi_arready,
    output wire [33:0]  axi_araddr,
    

    input wire          axi_rvalid,
    output reg          axi_rready,
    input wire  [1:0]   axi_rresp,
    input wire          axi_rlast,
    input wire  [31:0]  axi_rdata,

    input wire          resolve_request,
    input wire [19:0]   virtual_address,

    output reg          resolve_done,
    output reg          resolve_pagefault,
    output reg          resolve_accessfault,

    output wire  [7:0]  resolve_metadata,
    output wire  [21:0] resolve_physical_address,

    // SATP_MODE is assumed one, because otherwise no PTW is required
    input wire [21:0]   satp_ppn
);

`ifdef DEBUG_PTW
`include "assert.vh"
`endif

localparam STATE_IDLE = 2'd0;
localparam STATE_AR = 2'd1;
localparam STATE_R = 2'd2;
localparam STATE_TABLE_WALKING = 2'd3;

localparam false = 1'b0;
localparam true = 1'b1;

`DEFINE_REG_REG_NXT(2, state, state_nxt, clk)
`DEFINE_REG_REG_NXT(1, current_level, current_level_nxt, clk)
`DEFINE_REG_REG_NXT(22, current_table_base, current_table_base_nxt, clk)
`DEFINE_REG_REG_NXT(20, saved_virtual_address, saved_virtual_address_nxt, clk)
`DEFINE_REG_REG_NXT(32, saved_rdata, saved_rdata_nxt, clk)
`DEFINE_REG_REG_NXT(1, pma_error, pma_error_nxt, clk)


// local states

wire [9:0] virtual_address_vpn[1:0];
assign virtual_address_vpn[0] = saved_virtual_address[9:0];
assign virtual_address_vpn[1] = saved_virtual_address[19:10];

// PTE Decoding
wire pte_valid   = saved_rdata[`ARMLEOCPU_PAGE_METADATA_VALID_BIT_NUM];
wire pte_read    = saved_rdata[`ARMLEOCPU_PAGE_METADATA_READ_BIT_NUM];
wire pte_write   = saved_rdata[`ARMLEOCPU_PAGE_METADATA_WRITE_BIT_NUM];
wire pte_execute = saved_rdata[`ARMLEOCPU_PAGE_METADATA_EXECUTE_BIT_NUM];
/*verilator lint_off UNUSED*/
//wire [11:0] pte_ppn0 = saved_rdata[31:20];
/*verilator lint_off UNUSED*/
wire [9:0]  pte_ppn1 = saved_rdata[19:10];

wire pte_invalid = !pte_valid || (!pte_read && pte_write);
wire pte_missaligned = (current_level == 1) && (pte_ppn1 != 0);
        // missaligned if current level is zero is impossible
wire pte_is_leaf = pte_read || pte_execute;
wire pte_pointer = saved_rdata[3:0] == 4'b0001;

assign axi_araddr = {current_table_base, virtual_address_vpn[current_level], 2'b00};

// Resolve resolved physical address
assign resolve_physical_address = {
    saved_rdata[31:20],
    current_level ? saved_virtual_address[9:0] : saved_rdata[19:10]
};
// resolved access bits
assign resolve_metadata = saved_rdata[7:0];


always @* begin
    `ifdef SIMULATION
    #1
    `endif
    state_nxt = state;
    current_level_nxt = current_level;
    current_table_base_nxt = current_table_base;
    saved_virtual_address_nxt = saved_virtual_address;
    saved_rdata_nxt = saved_rdata;
    pma_error_nxt = pma_error;
    
    
    axi_rready = 0;
    axi_arvalid = 0;

    resolve_accessfault = 0;
    resolve_done = 0;
    resolve_pagefault = 0;

    if(!rst_n) begin
        state_nxt = STATE_IDLE;
    end else begin
        case(state)
            STATE_IDLE: begin
                current_level_nxt = 1'b1;
                saved_virtual_address_nxt = virtual_address;
                current_table_base_nxt = satp_ppn;
                if(resolve_request) begin
                    state_nxt = STATE_AR;
                end
            end
            STATE_AR: begin
                axi_arvalid = 1'b1;
                if(axi_arready) begin
                    state_nxt = STATE_R;
                end
            end
            STATE_R: begin
                if(axi_rvalid) begin
                    state_nxt = STATE_TABLE_WALKING;
                    saved_rdata_nxt = axi_rdata;
                    pma_error_nxt = axi_rresp != 0;
                    axi_rready = 1'b1;
                    
                    if(!axi_rlast) begin
                        `ifdef DEBUG_PTW
                        $display("!ERROR!: Error: PTW: AXI RLAST is not one when supposed");
                        `assert_equal(0, 1)
                        `endif
                    end
                end
            end
            STATE_TABLE_WALKING: begin
                if(pma_error) begin
                    resolve_accessfault = true;
                    resolve_done = true;
                end else if(pte_invalid) begin
                    resolve_pagefault = true;
                    resolve_done = true;
                end else if(pte_is_leaf) begin
                    if(pte_missaligned) begin
                        resolve_pagefault = true;
                        resolve_done = true;
                    end else if(!pte_missaligned) begin
                        resolve_done = true;
                    end
                end else if(pte_pointer) begin
                    if(current_level == 1'b0) begin
                        resolve_pagefault = true;
                        resolve_done = true;
                    end else if(current_level == 1'b1) begin
                        current_level_nxt = 1'b0;
                        current_table_base_nxt = saved_rdata[31:10];
                        state_nxt = STATE_AR;
                    end
                end
                if(resolve_done) begin
                    state_nxt = STATE_IDLE;
                end
            end
        endcase
    end
end



/*
`ifdef DEBUG_PTW
task debug_write_all; begin
    debug_write_request();
    debug_write_state();
    debug_write_pte();
end endtask

task debug_write_request; begin
    $display("[%m][%d] [PTW]\tRequested virtual address = 0x%H", $time, {saved_virtual_address, 12'hXXX});
end endtask

task debug_write_state; begin
    $display("[%m][%d] [PTW]\tstate = %s, current_level = %s, current_table_base = 0x%X",
            $time,
            state == 1 ? "IDLE" : "TABLE_WALKING",
            current_level ? "megapage": "page",
            {current_table_base, 12'hXXX});
end endtask

task debug_write_pte; begin
    $display("[%m][%d] [PTW]\tPTE value = 0x%X, avl_response = %s, m_address = 0x%X", $time, saved_rdata, m_transaction_response == `ARMLEOBUS_RESPONSE_SUCCESS ? "VALID": "ERROR", m_address);
    $display("[%m][%d] [PTW]\tvalid? = %s, access_bits = %s%s%s\t", $time, pte_valid ? "VALID" : "INVALID", (pte_read ? "r" : " "), (pte_write ? "w" : " "), (pte_execute ? "x" : " "));
    $display("[%m][%d] [PTW]\tpte_ppn0 = 0x%X, pte_ppn1 = 0x%X", $time, pte_ppn0, pte_ppn1);
    if(pma_error) begin
                                $display("[%m][%d] [PTW]\tPMA_Error", $time);
    end else if(pte_invalid) begin
                                $display("[%m][%d] [PTW]\tPTE_Invalid", $time);
    end else if(pte_is_leaf) begin
        if(!pte_missaligned)    $display("[%m][%d] [PTW]\tAligned page", $time);
        else                    $display("[%m][%d] [PTW]\tMissaligned megapage", $time);
    end else if(pte_pointer) begin
        if(current_level)       $display("[%m][%d] [PTW]\tGoing deeper", $time);
        else                    $display("[%m][%d] [PTW]\tPage leaf expected, insted pointer found", $time);
    end
end endtask
`endif



always @* begin
    resolve_done = false;
    resolve_pagefault = false;
    resolve_accessfault = false;
    case(state)
        STATE_IDLE: begin

        end
        STATE_TABLE_WALKING: begin
            if(m_transaction_done) begin
                if(pma_error) begin
                    resolve_accessfault = true;
                    resolve_done = true;
                end else if(pte_invalid) begin
                    resolve_pagefault = true;
                    resolve_done = true;
                end else if(pte_is_leaf) begin
                    if(pte_missaligned) begin
                        resolve_pagefault = true;
                        resolve_done = true;
                    end else if(!pte_missaligned) begin
                        resolve_done = true;
                    end
                end else if(pte_pointer) begin
                    if(current_level == 1'b0) begin
                        resolve_pagefault = true;
                        resolve_done = true;
                    end
                    //else if(current_level == 1'b1) begin end;  
                end
            end
        end
    endcase
end

always @(posedge clk) begin
    if(!rst_n) begin
        state <= STATE_IDLE;
    end else if(clk) begin
        case(state)
            STATE_IDLE: begin
                current_level <= 1'b1;
                saved_virtual_address <= virtual_address;
                current_table_base <= satp_ppn;
                if(resolve_request) begin
                    state <= STATE_TABLE_WALKING;
                    `ifdef DEBUG_PTW
                    $display("[%m][%d] [PTW] Page table walk request for address = 0x%X, w/ satp_mode = %b", $time, {virtual_address, 12'hXXX}, satp_mode);
                    `endif
                end
            end
            STATE_TABLE_WALKING: begin
                if(m_transaction_done) begin
                    if(pma_error) begin
                        state <= STATE_IDLE;
                        `ifdef DEBUG_PTW
                        $display("[%m][%d] [PTW] Request failed because of PMA", $time);
                        debug_write_all();
                        `endif
                    end else if(pte_invalid) begin
                        state <= STATE_IDLE;
                        `ifdef DEBUG_PTW
                        $display("[%m][%d] [PTW] Request failed because PTE", $time);
                        debug_write_all();
                        `endif
                    end else if(pte_is_leaf) begin
                        state <= STATE_IDLE;
                        if(pte_missaligned) begin
                            `ifdef DEBUG_PTW
                            $display("[%m][%d] [PTW] Request failed because PTE is missalligned", $time);
                            debug_write_all();
                            `endif
                        end else if(!pte_missaligned) begin
                            `ifdef DEBUG_PTW
                            $display("[%m][%d] [PTW] Request successful completed", $time);
                            debug_write_all();
                            `endif
                        end
                    end else if(pte_pointer) begin
                        if(current_level == 1'b0) begin
                            state <= STATE_IDLE;
                            `ifdef DEBUG_PTW
                            $display("[%m][%d] [PTW] Resolve pagefault", $time);
                            debug_write_all();
                            `endif
                        end else if(current_level == 1'b1) begin
                            current_level <= 1'b0;
                            current_table_base <= saved_rdata[31:10];
                            `ifdef DEBUG_PTW
                            $display("[%m][%d] [PTW] Resolve going to next level", $time);
                            debug_write_all();
                            `endif
                        end
                    end
                end
            end
        endcase
    end
end
*/

endmodule


`include "armleocpu_undef.vh"
