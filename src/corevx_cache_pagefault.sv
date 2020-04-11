`timescale 1ns/1ns
module corevx_cache_pagefault(
    input                   csr_satp_mode_r, // Mode = 0 -> physical access,
    input                   os_csr_mstatus_mprv,
    input                   os_csr_mstatus_mxr,
    input                   os_csr_mstatus_sum,
    input [1:0]             os_csr_mstatus_mpp,
    input [1:0]             os_csr_mcurrent_privilege,

    input [3:0]             os_cmd,
    input [7:0]             tlb_read_accesstag,

    output reg              pagefault
);

`include "corevx_cache.svh"
`include "corevx_accesstag_defs.svh"
`include "corevx_privilege.svh"
`ifdef DEBUG
reg [1000:0] reason;
`endif

wire tlb_accesstag_readable     = tlb_read_accesstag[ACCESSTAG_READ_BIT_NUM];
wire tlb_accesstag_writable     = tlb_read_accesstag[ACCESSTAG_WRITE_BIT_NUM];
wire tlb_accesstag_executable   = tlb_read_accesstag[ACCESSTAG_EXECUTE_BIT_NUM];
wire tlb_accesstag_dirty        = tlb_read_accesstag[ACCESSTAG_DIRTY_BIT_NUM];
wire tlb_accesstag_access       = tlb_read_accesstag[ACCESSTAG_ACCESS_BIT_NUM];
wire tlb_accesstag_user         = tlb_read_accesstag[ACCESSTAG_USER_BIT_NUM];
wire tlb_accesstag_valid        = (tlb_accesstag_executable || tlb_accesstag_readable) && tlb_read_accesstag[0];

reg [1:0] current_privilege;

always @* begin
    `ifdef DEBUG
    reason = "NONE";
    `endif
    pagefault = 0;
    current_privilege = os_csr_mstatus_mprv ? os_csr_mstatus_mpp : os_csr_mcurrent_privilege;
    // if address translation enabled

    if(current_privilege == `COREVX_PRIVILEGE_MACHINE || csr_satp_mode_r == 1'b0) begin
        //pagefault = 0;
    end else begin
        if(!tlb_accesstag_valid) begin
            pagefault = 1;
            `ifdef DEBUG
                reason = "ACCESSTAG_INVALID";
            `endif
        end
        // currently in supervisor mode and page is marked as user and supervisor cannot access user pages
        if(current_privilege == `COREVX_PRIVILEGE_SUPERVISOR) begin
            if(tlb_accesstag_user && !os_csr_mstatus_sum) begin
                pagefault = 1;
                `ifdef DEBUG
                    reason = "SUPERVISOR_ACCESSING_USER_PAGE";
                `endif
            end
        end else if(current_privilege == `COREVX_PRIVILEGE_USER) begin
            // currently in user mode and page is not accessible for users
            if(!tlb_accesstag_user) begin
                pagefault = 1;
                `ifdef DEBUG
                    reason = "USER_ACCESSING_NOT_USER_PAGE";
                `endif
            end
        end
        if(!tlb_accesstag_access) begin
            pagefault = 1;
            `ifdef DEBUG
                reason = "ACCESS_BIT_DEASSERTED";
            `endif
        end else if(os_cmd == `CACHE_CMD_STORE) begin
            // page not marked dirty already
            if(!tlb_accesstag_dirty) begin
                pagefault = 1;
                `ifdef DEBUG
                    reason = "DIRTY_BIT_DEASSERTED";
                `endif
            end else if(!tlb_accesstag_writable) begin
                pagefault = 1;
                `ifdef DEBUG
                    reason = "STORE_TO_UNWRITTABLE";
                `endif
            end
        end else if(os_cmd == `CACHE_CMD_LOAD) begin
            // load from not readable
            if(!tlb_accesstag_readable) begin
                // but load from executable that is also readable
                if(os_csr_mstatus_mxr && tlb_accesstag_executable) begin
                    //pagefault = 0;
                end else begin
                    pagefault = 1;
                    `ifdef DEBUG
                        reason = "LOAD_FROM_UNREADABLE";
                    `endif
                end
            end
        end else if(os_cmd == `CACHE_CMD_EXECUTE) begin
            if(!tlb_accesstag_executable) begin
                pagefault = 1;
                `ifdef DEBUG
                    reason = "EXECUTE_FROM_NOT_EXECUTABLE";
                `endif
            end
        end
    end
end
endmodule