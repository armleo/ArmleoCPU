`timescale 1ns/1ns
module armleocpu_cache_pagefault(
    input                   csr_satp_mode_r, // Mode = 0 -> physical access,
    input [1:0]             os_csr_mcurrent_privilege,
    input                   os_csr_mstatus_mprv,
    input                   os_csr_mstatus_mxr,
    input                   os_csr_mstatus_sum,
    input [1:0]             os_csr_mstatus_mpp,
    

    input [3:0]             os_cmd,
    input [7:0]             tlb_read_accesstag,

    output reg              pagefault
    `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
    , output reg [30*8-1:0] reason
    `endif /* verilator lint_on WIDTH */
);

`include "armleocpu_cache.inc"
`include "armleocpu_accesstag_defs.inc"
`include "armleocpu_privilege.inc"


wire tlb_accesstag_readable     = tlb_read_accesstag[`ACCESSTAG_READ_BIT_NUM];
wire tlb_accesstag_writable     = tlb_read_accesstag[`ACCESSTAG_WRITE_BIT_NUM];
wire tlb_accesstag_executable   = tlb_read_accesstag[`ACCESSTAG_EXECUTE_BIT_NUM];
wire tlb_accesstag_dirty        = tlb_read_accesstag[`ACCESSTAG_DIRTY_BIT_NUM];
wire tlb_accesstag_access       = tlb_read_accesstag[`ACCESSTAG_ACCESS_BIT_NUM];
wire tlb_accesstag_user         = tlb_read_accesstag[`ACCESSTAG_USER_BIT_NUM];
wire tlb_accesstag_valid        = (tlb_accesstag_executable || tlb_accesstag_readable) && tlb_read_accesstag[0];

reg [1:0] current_privilege;

always @* begin
    `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
    reason = "NONE";
    `endif /* verilator lint_on WIDTH */
    pagefault = 0;
    current_privilege = ((os_csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) && os_csr_mstatus_mprv) ? os_csr_mstatus_mpp : os_csr_mcurrent_privilege;
    // if address translation enabled

    if(current_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE || csr_satp_mode_r == 1'b0) begin
        //pagefault = 0;
    end else begin
        if(!tlb_accesstag_valid) begin
            pagefault = 1;
            `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                reason = "ACCESSTAG_INVALID";
            `endif /* verilator lint_on WIDTH */
        end
        // currently in supervisor mode and page is marked as user and supervisor cannot access user pages
        if(current_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR) begin
            if(tlb_accesstag_user && !os_csr_mstatus_sum) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "SUPERVISOR_ACCESSING_USER_PAGE";
                `endif /* verilator lint_on WIDTH */
            end
        end else if(current_privilege == `ARMLEOCPU_PRIVILEGE_USER) begin
            // currently in user mode and page is not accessible for users
            if(!tlb_accesstag_user) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "USER_ACCESSING_NOT_USER_PAGE";
                `endif /* verilator lint_on WIDTH */
            end
        end
        if(!tlb_accesstag_access) begin
            pagefault = 1;
            `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                reason = "ACCESS_BIT_DEASSERTED";
            `endif /* verilator lint_on WIDTH */
        end else if(os_cmd == `CACHE_CMD_STORE) begin
            // page not marked dirty already
            if(!tlb_accesstag_dirty) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "DIRTY_BIT_DEASSERTED";
                `endif /* verilator lint_on WIDTH */
            end else if(!tlb_accesstag_writable) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "STORE_TO_UNWRITTABLE";
                `endif /* verilator lint_on WIDTH */
            end
        end else if(os_cmd == `CACHE_CMD_LOAD) begin
            // load from not readable
            if(!tlb_accesstag_readable) begin
                // but load from executable that is also readable
                if(os_csr_mstatus_mxr && tlb_accesstag_executable) begin
                    //pagefault = 0;
                end else begin
                    pagefault = 1;
                    `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                        reason = "LOAD_FROM_UNREADABLE";
                    `endif /* verilator lint_on WIDTH */
                end
            end
        end else if(os_cmd == `CACHE_CMD_EXECUTE) begin
            if(!tlb_accesstag_executable) begin
                pagefault = 1;
                `ifdef DEBUG_PAGEFAULT /* verilator lint_off WIDTH */
                    reason = "EXECUTE_FROM_NOT_EXECUTABLE";
                `endif /* verilator lint_on WIDTH */
            end
        end
    end
end
endmodule