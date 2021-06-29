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
// Copyright (C) 2021, Arman Avetisyan, see COPYING file
//
// Filename:    armleocpu_cache_pagefault.v
// Project:	ArmleoCPU
//
// Purpose:	ArmleoCPU's Cache pagefault generation logic,
//      designed for RISC-V
//
////////////////////////////////////////////////////////////////////////////////


`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_cache_pagefault(
    input                   csr_satp_mode_r, // Mode = 0 -> physical access,
    input [1:0]             csr_mcurrent_privilege,
    input                   csr_mstatus_mprv,
    input                   csr_mstatus_mxr,
    input                   csr_mstatus_sum,
    input [1:0]             csr_mstatus_mpp,
    

    input [3:0]             os_cmd,
    /* verilator lint_off UNUSED */
    input [7:0]             tlb_read_metadata,
    /* verilator lint_on UNUSED */
    output reg              pagefault
    `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
    , output reg [30*8-1:0] reason
    `endif /* verilator lint_on WIDTH */
);

wire tlb_metadata_readable     = tlb_read_metadata[`ARMLEOCPU_PAGE_METADATA_READ_BIT_NUM];
wire tlb_metadata_writable     = tlb_read_metadata[`ARMLEOCPU_PAGE_METADATA_WRITE_BIT_NUM];
wire tlb_metadata_executable   = tlb_read_metadata[`ARMLEOCPU_PAGE_METADATA_EXECUTE_BIT_NUM];
wire tlb_metadata_dirty        = tlb_read_metadata[`ARMLEOCPU_PAGE_METADATA_DIRTY_BIT_NUM];
wire tlb_metadata_access       = tlb_read_metadata[`ARMLEOCPU_PAGE_METADATA_ACCESS_BIT_NUM];
wire tlb_metadata_user         = tlb_read_metadata[`ARMLEOCPU_PAGE_METADATA_USER_BIT_NUM];
wire tlb_metadata_valid        = (tlb_metadata_executable || tlb_metadata_readable) && tlb_read_metadata[0];

reg [1:0] current_privilege;

always @* begin
    `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
    reason = "NONE";
    `endif /* verilator lint_on WIDTH */
    `ifdef SIMULATION
    #1
    `endif
    pagefault = 0;
    current_privilege = ((csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) && csr_mstatus_mprv) ? csr_mstatus_mpp : csr_mcurrent_privilege;
    // if address translation enabled

    if(current_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE || csr_satp_mode_r == 1'b0) begin
        //pagefault = 0;
    end else begin
        if(!tlb_metadata_valid) begin
            pagefault = 1;
            `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                reason = "ARMLEOCPU_PAGE_METADATA_INVALID";
            `endif /* verilator lint_on WIDTH */
        end
        // currently in supervisor mode and page is marked as user and supervisor cannot access user pages
        if(current_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR) begin
            if(tlb_metadata_user && !csr_mstatus_sum) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "SUPERVISOR_ACCESSING_USER_PAGE";
                `endif /* verilator lint_on WIDTH */
            end
        end else if(current_privilege == `ARMLEOCPU_PRIVILEGE_USER) begin
            // currently in user mode and page is not accessible for users
            if(!tlb_metadata_user) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "USER_ACCESSING_NOT_USER_PAGE";
                `endif /* verilator lint_on WIDTH */
            end
        end
        if(!tlb_metadata_access) begin
            pagefault = 1;
            `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                reason = "ACCESS_BIT_DEASSERTED";
            `endif /* verilator lint_on WIDTH */
        end else if(os_cmd == `CACHE_CMD_STORE || os_cmd == `CACHE_CMD_STORE_CONDITIONAL) begin
            // page not marked dirty already
            if(!tlb_metadata_dirty) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "DIRTY_BIT_DEASSERTED";
                `endif /* verilator lint_on WIDTH */
            end else if(!tlb_metadata_writable) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "STORE_TO_UNWRITTABLE";
                `endif /* verilator lint_on WIDTH */
            end
        end else if(os_cmd == `CACHE_CMD_LOAD || os_cmd == `CACHE_CMD_LOAD_RESERVE) begin
            // load from not readable
            if(!tlb_metadata_readable) begin
                // but load from executable that is also readable
                if(csr_mstatus_mxr && tlb_metadata_executable) begin
                    //pagefault = 0;
                end else begin
                    pagefault = 1;
                    `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                        reason = "LOAD_FROM_UNREADABLE";
                    `endif /* verilator lint_on WIDTH */
                end
            end
        end else if(os_cmd == `CACHE_CMD_EXECUTE) begin
            if(!tlb_metadata_executable) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "EXECUTE_FROM_NOT_EXECUTABLE";
                `endif /* verilator lint_on WIDTH */
            end
        end
    end
end
endmodule


`include "armleocpu_undef.vh"
