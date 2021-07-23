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

`define TIMEOUT 10000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"



// TODO: Add tests for atomic operations / commands

reg csr_satp_mode = 0, csr_mstatus_mprv = 0, csr_mstatus_mxr = 0, csr_mstatus_sum = 0;

reg [1:0] csr_mstatus_mpp = 0;
reg [1:0] csr_mcurrent_privilege = 0;

reg [3:0] cmd = `CACHE_CMD_NONE;
reg [7:0] tlb_read_metadata = 8'b0001_0000;
wire pagefault;

wire [30*8-1:0] reason;

armleocpu_cache_pagefault pf(
    .*
);

localparam USER_METATAG = 8'b1101_1111;

initial begin
    $display("%d, Test case machine mode no mprv", $time);
    csr_satp_mode = 0;
    csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_MACHINE;
    #10;
    `assert_equal(pagefault, 0);
    
    $display("%d, Test case machine mode no mprv, mode = 1", $time);
    csr_satp_mode = 1;
    csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_MACHINE;
    #10;
    `assert_equal(pagefault, 0);

    $display("%d, Test case supervisor mode no mprv, user page access", $time);
    csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_SUPERVISOR;
    csr_mstatus_sum = 0;// dont allow supervisor to access user pages
    tlb_read_metadata = USER_METATAG;
    #10;
    `assert_equal(pagefault, 1);





    $display("Executable test cases");
    $display("%d, Test case execute on unexecutable", $time);
    csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_USER;
    cmd = `CACHE_CMD_EXECUTE;
    tlb_read_metadata = 8'b1101_0111;
    #10
    `assert_equal(pagefault, 1);
    $display("%d, Test case execute on executable", $time);
    tlb_read_metadata = 8'b1101_1001;
    #10
    `assert_equal(pagefault, 0);


    $display("Storable test cases");
    $display("%d, Test case store on unstorable", $time);
    csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_USER;
    cmd = `CACHE_CMD_STORE;
    tlb_read_metadata = 8'b1101_1011;
    #10
    `assert_equal(pagefault, 1);

    $display("%d, Test case store_conditional on unstorable", $time);
    csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_USER;
    cmd = `CACHE_CMD_STORE_CONDITIONAL;
    tlb_read_metadata = 8'b1101_1011;
    #10
    `assert_equal(pagefault, 1);

    $display("%d, Test case store on storable", $time);
    tlb_read_metadata = 8'b1101_0111;
    cmd = `CACHE_CMD_STORE;
    #10
    `assert_equal(pagefault, 0);

    $display("%d, Test case store conditional on storable", $time);
    tlb_read_metadata = 8'b1101_0111;
    cmd = `CACHE_CMD_STORE_CONDITIONAL;
    #10
    `assert_equal(pagefault, 0);

    $display("Loadable test cases");
    $display("%d, Test case load on unloadable", $time);
    csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_USER;
    cmd = `CACHE_CMD_LOAD;
    tlb_read_metadata = 8'b1101_1001;
    #10
    `assert_equal(pagefault, 1);

    $display("%d, Test case load reserve on unloadable", $time);
    csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_USER;
    cmd = `CACHE_CMD_LOAD_RESERVE;
    tlb_read_metadata = 8'b1101_1001;
    #10
    `assert_equal(pagefault, 1);

    $display("%d, Test case load on loadable", $time);
    cmd = `CACHE_CMD_LOAD;
    tlb_read_metadata = 8'b1101_0011;
    #10
    `assert_equal(pagefault, 0);


    $display("%d, Test case load reserve on loadable", $time);
    cmd = `CACHE_CMD_LOAD_RESERVE;
    tlb_read_metadata = 8'b1101_0011;
    #10
    `assert_equal(pagefault, 0);

    $display("mxr test cases");
    $display("%d, Load from executable, mxr = 1", $time);
    tlb_read_metadata = 8'b1101_1001;
    cmd = `CACHE_CMD_LOAD;
    csr_mstatus_mxr = 1;
    #10
    `assert_equal(pagefault, 0);

    $display("%d, Load from executable, mxr = 0", $time);
    tlb_read_metadata = 8'b1101_1001;
    cmd = `CACHE_CMD_LOAD;
    csr_mstatus_mxr = 0;
    #10
    `assert_equal(pagefault, 1);

    $display("dirty = 0");
    $display("%d, Load, dirty = 0", $time);
    cmd = `CACHE_CMD_LOAD;
    tlb_read_metadata = 8'b0101_1111;
    #10
    `assert_equal(pagefault, 0);

    $display("%d, Store, dirty = 0", $time);
    cmd = `CACHE_CMD_STORE;
    tlb_read_metadata = 8'b0101_1111;
    #10
    `assert_equal(pagefault, 1);

    $display("%d, Execute, dirty = 0", $time);
    cmd = `CACHE_CMD_EXECUTE;
    tlb_read_metadata = 8'b0101_1111;
    #10
    `assert_equal(pagefault, 0);


    $display("access = 0");
    $display("%d, Load, access = 0", $time);
    cmd = `CACHE_CMD_LOAD;
    tlb_read_metadata = 8'b1001_1111;
    #10
    `assert_equal(pagefault, 1);

    $display("%d, Store, access = 0", $time);
    cmd = `CACHE_CMD_STORE;
    tlb_read_metadata = 8'b1001_1111;
    #10
    `assert_equal(pagefault, 1);

    $display("%d, Execute, access = 0", $time);
    cmd = `CACHE_CMD_EXECUTE;
    tlb_read_metadata = 8'b1001_1111;
    #10
    `assert_equal(pagefault, 1);

    $display("Bulk 1");
    csr_mstatus_sum = 1;
    csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_USER;
    repeat (2) begin
        cmd = `CACHE_CMD_EXECUTE;
        repeat (3) begin
            $display("%d, Test case invalid metadata cmd = %d, privilege = %d", $time, cmd, csr_mcurrent_privilege);
            tlb_read_metadata = 8'b1101_1110;
            #10
            `assert_equal(pagefault, 1);

            $display("%d, Test case valid metadata cmd = %d, privilege = %d", $time, cmd, csr_mcurrent_privilege);
            tlb_read_metadata = 8'b1101_1111;
            #10
            `assert_equal(pagefault, 0);

            if(cmd == `CACHE_CMD_EXECUTE)
                cmd = `CACHE_CMD_LOAD;
            else if(cmd == `CACHE_CMD_LOAD)
                cmd = `CACHE_CMD_STORE;
        end
        csr_mcurrent_privilege = `ARMLEOCPU_PRIVILEGE_SUPERVISOR;
    end

    $finish;
end


endmodule