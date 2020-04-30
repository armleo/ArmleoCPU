`timescale 1ns/1ns

module pagefault_testbench;


initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
end

`include "corevx_cache.svh"
`include "assert.svh"

reg csr_satp_mode_r = 0, os_csr_mstatus_mprv = 0, os_csr_mstatus_mxr = 0, os_csr_mstatus_sum = 0;

reg [1:0] os_csr_mstatus_mpp = 0;
reg [1:0] os_csr_mcurrent_privilege = 0;

reg [3:0] os_cmd = `CACHE_CMD_NONE;
reg [7:0] tlb_read_accesstag = 8'b0001_0000;
wire pagefault;

wire [30*8-1:0] reason;

corevx_cache_pagefault pf(
    .*
);

localparam USER_ACCESSTAG = 8'b1101_1111;

initial begin
    $display("%d, Test case machine mode no mprv", $time);
    csr_satp_mode_r = 0;
    os_csr_mcurrent_privilege = `COREVX_PRIVILEGE_MACHINE;
    #1;
    `assert(pagefault, 0);
    
    $display("%d, Test case machine mode no mprv, mode = 1", $time);
    csr_satp_mode_r = 1;
    os_csr_mcurrent_privilege = `COREVX_PRIVILEGE_MACHINE;
    #1;
    `assert(pagefault, 0);

    $display("%d, Test case supervisor mode no mprv, user page access", $time);
    os_csr_mcurrent_privilege = `COREVX_PRIVILEGE_SUPERVISOR;
    os_csr_mstatus_sum = 0;// dont allow supervisor to access user pages
    tlb_read_accesstag = USER_ACCESSTAG;
    #1;
    `assert(pagefault, 1);





    $display("Executable test cases");
    $display("%d, Test case execute on unexecutable", $time);
    os_csr_mcurrent_privilege = `COREVX_PRIVILEGE_USER;
    os_cmd = `CACHE_CMD_EXECUTE;
    tlb_read_accesstag = 8'b1101_0111;
    #1
    `assert(pagefault, 1);
    $display("%d, Test case execute on executable", $time);
    tlb_read_accesstag = 8'b1101_1001;
    #1
    `assert(pagefault, 0);


    $display("Storable test cases");
    $display("%d, Test case store on unstorable", $time);
    os_csr_mcurrent_privilege = `COREVX_PRIVILEGE_USER;
    os_cmd = `CACHE_CMD_STORE;
    tlb_read_accesstag = 8'b1101_1011;
    #1
    `assert(pagefault, 1);
    $display("%d, Test case store on storable", $time);
    tlb_read_accesstag = 8'b1101_0111;
    #1
    `assert(pagefault, 0);

    $display("Loadable test cases");
    $display("%d, Test case load on unloadable", $time);
    os_csr_mcurrent_privilege = `COREVX_PRIVILEGE_USER;
    os_cmd = `CACHE_CMD_LOAD;
    tlb_read_accesstag = 8'b1101_1001;
    #1
    `assert(pagefault, 1);
    $display("%d, Test case load on loadable", $time);
    tlb_read_accesstag = 8'b1101_0011;
    #1
    `assert(pagefault, 0);

    $display("mxr test cases");
    $display("%d, Load from executable, mxr = 1", $time);
    tlb_read_accesstag = 8'b1101_1001;
    os_cmd = `CACHE_CMD_LOAD;
    os_csr_mstatus_mxr = 1;
    #1
    `assert(pagefault, 0);

    $display("%d, Load from executable, mxr = 0", $time);
    tlb_read_accesstag = 8'b1101_1001;
    os_cmd = `CACHE_CMD_LOAD;
    os_csr_mstatus_mxr = 0;
    #1
    `assert(pagefault, 1);

    $display("dirty = 0");
    $display("%d, Load, dirty = 0", $time);
    os_cmd = `CACHE_CMD_LOAD;
    tlb_read_accesstag = 8'b0101_1111;
    #1
    `assert(pagefault, 0);

    $display("%d, Store, dirty = 0", $time);
    os_cmd = `CACHE_CMD_STORE;
    tlb_read_accesstag = 8'b0101_1111;
    #1
    `assert(pagefault, 1);

    $display("%d, Execute, dirty = 0", $time);
    os_cmd = `CACHE_CMD_EXECUTE;
    tlb_read_accesstag = 8'b0101_1111;
    #1
    `assert(pagefault, 0);


    $display("access = 0");
    $display("%d, Load, access = 0", $time);
    os_cmd = `CACHE_CMD_LOAD;
    tlb_read_accesstag = 8'b1001_1111;
    #1
    `assert(pagefault, 1);

    $display("%d, Store, access = 0", $time);
    os_cmd = `CACHE_CMD_STORE;
    tlb_read_accesstag = 8'b1001_1111;
    #1
    `assert(pagefault, 1);

    $display("%d, Execute, access = 0", $time);
    os_cmd = `CACHE_CMD_EXECUTE;
    tlb_read_accesstag = 8'b1001_1111;
    #1
    `assert(pagefault, 1);

    $display("Bulk 1");
    os_csr_mstatus_sum = 1;
    os_csr_mcurrent_privilege = `COREVX_PRIVILEGE_USER;
    repeat (2) begin
        os_cmd = `CACHE_CMD_EXECUTE;
        repeat (3) begin
            $display("%d, Test case invalid accesstag cmd = %d, privilege = %d", $time, os_cmd, os_csr_mcurrent_privilege);
            tlb_read_accesstag = 8'b1101_1110;
            #1
            `assert(pagefault, 1);

            $display("%d, Test case valid accesstag cmd = %d, privilege = %d", $time, os_cmd, os_csr_mcurrent_privilege);
            tlb_read_accesstag = 8'b1101_1111;
            #1
            `assert(pagefault, 0);

            if(os_cmd == `CACHE_CMD_EXECUTE)
                os_cmd = `CACHE_CMD_LOAD;
            else if(os_cmd == `CACHE_CMD_LOAD)
                os_cmd = `CACHE_CMD_STORE;
        end
        os_csr_mcurrent_privilege = `COREVX_PRIVILEGE_SUPERVISOR;
    end

end


endmodule