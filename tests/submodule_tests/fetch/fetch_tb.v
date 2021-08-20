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
////////////////////////////////////////////////////////////////////////////////

`define TIMEOUT 1000
`define SYNC_RST
`define CLK_HALF_PERIOD 5

`define MAXIMUM_ERRORS 1

`include "template.vh"


wire req_valid;
wire [3:0] req_cmd;
wire [31:0] req_address;
reg req_ready;

reg resp_valid;
reg [3:0] resp_status;
reg [31:0] resp_read_data;

reg interrupt_pending;


reg dbg_mode;
wire dbg_pipeline_busy;

wire f2d_valid;
wire [`F2E_TYPE_WIDTH-1:0] f2d_type;
wire [31:0] f2d_instr;
wire [31:0] f2d_pc;
wire [3:0] f2d_status;

reg d2f_ready;
reg [`ARMLEOCPU_D2F_CMD_WIDTH-1:0] d2f_cmd;
reg [31:0] d2f_branchtarget;

reg dbg_cmd_valid;
reg [`DEBUG_CMD_WIDTH-1:0] dbg_cmd;
reg [31:0] dbg_arg0_i;
wire [31:0] dbg_arg0_o;

wire dbg_cmd_ready;

armleocpu_fetch u0 (
    .*
);


reg [31:0] pc = 1000;
reg [31:0] instr_pc = 1000;

task req_assert(input [3:0] op, input [31:0] addr);
`assert_equal(req_valid, op != `CACHE_CMD_NONE);
`assert_equal(req_cmd, op);
if(op != `CACHE_CMD_NONE) begin
    `assert_equal(req_address, addr);
end
endtask

task req_assert_execute(input [31:0] addr);
    req_assert(`CACHE_CMD_EXECUTE, addr);
endtask

task f2d_assert_invalid;
f2d_assert(0, `CACHE_RESPONSE_SUCCESS, `F2E_TYPE_INSTR, 32'hXXXX_XXXX, 32'hXXXX_XXXX);
endtask

task f2d_assert(
    input valid,
    input [3:0] status,
    input [`F2E_TYPE_WIDTH-1:0] typ,
    input [31:0] read_data,
    input [31:0] addr
);
`assert_equal(f2d_valid, valid);
if(valid) begin
    `assert_equal(f2d_type, typ);
    if(typ == `F2E_TYPE_INSTR) begin
        `assert_equal(f2d_instr, read_data);
        `assert_equal(f2d_status, status);
    end
    `assert_equal(f2d_pc, addr);
end
endtask

task dbg_assert_busy;
`assert_equal(dbg_pipeline_busy, 1);
`assert_equal(dbg_cmd_ready, 0);
endtask


task cache_resp(input rdy, input vld, input [31:0] read_data, input [3:0] status);
    req_ready = rdy;
    resp_valid = vld;
    resp_read_data = read_data;
    resp_status = status;
endtask

task cache_resp_req_ready;
cache_resp(1, 0, 32'hXXXX_XXXX, 4'hX);
endtask

task d2f_resp(input rdy, input [`ARMLEOCPU_D2F_CMD_WIDTH-1:0] cmd, input [31:0] btarget);
    d2f_ready = rdy;
    d2f_cmd = cmd;
    d2f_branchtarget = btarget;
endtask


task d2f_resp_ready;
d2f_resp(1, `ARMLEOCPU_D2F_CMD_NONE, 32'hXXXX_XXXX);
endtask


// Cache accepts data, no response, d2f is ready
// Next request is generated
task test_case_cache_accept();

cache_resp_req_ready();
d2f_resp_ready();

#1

req_assert_execute(pc);
f2d_assert_invalid();
dbg_assert_busy();

@(negedge clk);


instr_pc = pc;
pc = pc + 4;

endtask

task test_case_cache_flushaccept();

cache_resp_req_ready();
d2f_resp_ready();

#1

req_assert(`CACHE_CMD_FLUSH_ALL, pc);
f2d_assert_invalid();
dbg_assert_busy();

@(negedge clk);


instr_pc = pc;

endtask



// Cache generates response, does not accept new command and d2f is ready
// Next request is generated
task test_case_cache_resp_stall(input [31:0] data);
    cache_resp(0, 1, data, `CACHE_RESPONSE_SUCCESS);
    d2f_resp_ready();

    #1

    
    f2d_assert(1, `CACHE_RESPONSE_SUCCESS, `F2E_TYPE_INSTR, data, instr_pc);
    req_assert(`CACHE_CMD_EXECUTE, pc);
    dbg_assert_busy();

    @(negedge clk);

    instr_pc = pc;
endtask

task test_case_cache_resp_stall_branch(input [31:0] data, input [31:0] branchtarget);
    cache_resp(0, 1, data, `CACHE_RESPONSE_SUCCESS);
    d2f_resp(1, `ARMLEOCPU_D2F_CMD_START_BRANCH, branchtarget);

    #1

    pc = branchtarget;
    f2d_assert(1, `CACHE_RESPONSE_SUCCESS, `F2E_TYPE_INSTR, data, instr_pc);
    req_assert(`CACHE_CMD_EXECUTE, pc);
    dbg_assert_busy();

    @(negedge clk);
    
    instr_pc = pc;
endtask



task test_case_cache_resp_stall_flush(input [31:0] data, input [31:0] branchtarget);
    cache_resp(0, 1, data, `CACHE_RESPONSE_SUCCESS);
    d2f_resp(1, `ARMLEOCPU_D2F_CMD_FLUSH, branchtarget);

    #1

    pc = branchtarget;

    f2d_assert(1, `CACHE_RESPONSE_SUCCESS, `F2E_TYPE_INSTR, data, instr_pc);
    req_assert(`CACHE_CMD_FLUSH_ALL, pc);
    dbg_assert_busy();

    @(negedge clk);
    
    instr_pc = pc;

endtask

// Cache generates response, accepts new command and d2f is ready
// Next request is generated
task test_case_cache_resp_accept(input [31:0] data);
    cache_resp(1, 1, data, `CACHE_RESPONSE_SUCCESS);
    d2f_resp_ready();

    #1

    
    f2d_assert(1, `CACHE_RESPONSE_SUCCESS, `F2E_TYPE_INSTR, data, instr_pc);
    req_assert(`CACHE_CMD_EXECUTE, pc);
    dbg_assert_busy();

    @(negedge clk);

    instr_pc = pc;
    pc = pc + 4;
endtask



task test_case_cache_flushresp_accept(input [31:0] data);
    cache_resp(1, 1, data, `CACHE_RESPONSE_SUCCESS);
    d2f_resp_ready();

    #1

    
    f2d_assert_invalid();
    req_assert(`CACHE_CMD_EXECUTE, pc);
    dbg_assert_busy();

    @(negedge clk);

    instr_pc = pc;
    pc = pc + 4;
endtask


// Cache does not response and does not accept request
// Request needs to be valid
task test_case_cache_stall;
    cache_resp(0, 0, 32'hXXXX_XXXX, `CACHE_RESPONSE_SUCCESS);
    d2f_resp_ready();

    #1

    
    f2d_assert_invalid();
    req_assert(`CACHE_CMD_EXECUTE, pc);
    dbg_assert_busy();

    @(negedge clk);

    instr_pc = pc;
endtask


`define TESTBENCH_START(str) \
    $display("Time: %t, Testbench: %s", $time, ``str``);

initial begin
    req_ready = 0;
    resp_valid = 0;
    resp_status = `CACHE_RESPONSE_SUCCESS;
    resp_read_data = 0;


    interrupt_pending = 0;

    dbg_mode = 0;
    dbg_cmd_valid = 0;
    dbg_cmd = `DEBUG_CMD_NONE;

    d2f_ready = 0;
    d2f_cmd = `ARMLEOCPU_D2F_CMD_NONE;
    d2f_branchtarget = 0;
    


    @(posedge rst_n);

    `TESTBENCH_START("Testbench: Starting fetch testing");
    

    
    @(negedge clk);

    `TESTBENCH_START("Testbench: Test case, start of fetch should start from 0x1000");

    pc = 32'h1000;
    test_case_cache_accept();


    `TESTBENCH_START("Testbench: After one fetch and no d2f/dbg_mode next fetch should start");
    
    test_case_cache_resp_stall(32'h88);
    

    `TESTBENCH_START("Testbench: after cache stall PC + 4 should not increment twice");
    test_case_cache_accept();
    test_case_cache_resp_stall(32'h99);
    

    `TESTBENCH_START("Testbench: Fetch should handle cache response stalled 2 cycle");
    test_case_cache_stall();
    test_case_cache_accept();
    test_case_cache_resp_accept(32'h88);
    test_case_cache_resp_stall(32'h77);

    `TESTBENCH_START("Testbench: Fetch then branch");
    test_case_cache_accept();
    test_case_cache_resp_stall_branch(32'h123, 32'h2000);
    


    `TESTBENCH_START("Testbench: Fetch then flush");
    test_case_cache_accept();
    test_case_cache_resp_stall_flush(32'h456, 32'h3000);
    test_case_cache_flushaccept();
    test_case_cache_flushresp_accept(32'h88);


    /*resp_valid = 1;
    resp_read_data = 32'h99;
    
    d2f_ready = 1;

    #1

    `assert_equal(req_valid, 1);
    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, pc + 4);
    f2d_assert_invalid();
    `assert_equal(f2d_status, `CACHE_RESPONSE_SUCCESS);
    dbg_assert_busy();

    pc = pc + 4;
    */
    /*
    @(negedge clk);

    `TESTBENCH_START("Testbench: Branch should be handled properly");

    resp_valid = 0;
    req_ready = 0;
    d2f_ready = 1;
    
    
    #1

    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_valid, 1);
    `assert_equal(req_address, pc + 4);
    f2d_assert_invalid();
    dbg_assert_busy();


    
    `TESTBENCH_START("Testbench: Branch should be handled properly part 2");
    @(negedge clk);

    resp_valid = 0;
    req_ready = 1;
    d2f_ready = 1;
    d2f_branchtarget = 32'h200;
    d2f_cmd = `ARMLEOCPU_D2F_CMD_START_BRANCH;
    
    #1

    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_valid, 1);
    `assert_equal(req_address, d2f_branchtarget);
    dbg_assert_busy();

    pc = d2f_branchtarget;


    @(negedge clk);


    `TESTBENCH_START("Testbench: Fetch should handle cache response stalled 2 cycle, with decode stalling 1 cycle and then flushing");
    
    resp_valid = 1;
    resp_read_data = 32'h77;
    req_ready = 0;
    d2f_ready = 1;
    
    
    #1

    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_valid, 1);
    `assert_equal(req_address, pc);
    `assert_equal(f2d_valid, 1);
    `assert_equal(f2d_type, `F2E_TYPE_INSTR);
    `assert_equal(f2d_instr, 32'h77);
    `assert_equal(f2d_pc, pc);
    dbg_assert_busy();

    @(negedge clk);

    `TESTBENCH_START("Testbench: Testing the flush");
    resp_valid = 0;
    req_ready = 0;
    d2f_ready = 1;
    d2f_branchtarget = 32'h200;
    d2f_cmd = `ARMLEOCPU_D2F_CMD_FLUSH;
    
    #1
    `assert_equal(req_valid, 1);
    `assert_equal(req_cmd, `CACHE_CMD_FLUSH_ALL);
    `assert_equal(req_valid, 1);
    `assert_equal(req_address, d2f_branchtarget);
    dbg_assert_busy();

    pc = d2f_branchtarget;


    @(negedge clk);

    `TESTBENCH_START("Testbench: Testing flush after it was deasserted");
    resp_valid = 0;
    req_ready = 1;
    d2f_ready = 1;
    d2f_branchtarget = 32'h10000;
    d2f_cmd = `ARMLEOCPU_D2F_CMD_NONE;

    #1

    `assert_equal(req_valid, 1);
    `assert_equal(req_cmd, `CACHE_CMD_FLUSH_ALL);
    `assert_equal(req_valid, 1);
    `assert_equal(req_address, pc);
    dbg_assert_busy();

    @(negedge clk);

    `TESTBENCH_START("Testbench: Testing flush after complete");

    req_ready = 0;
    resp_valid = 1;
    #1

    `assert_equal(req_valid, 1);
    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_valid, 1);
    `assert_equal(req_address, pc);
    dbg_assert_busy();



    `TESTBENCH_START("Testbench: Fetch should handle cache response stalled 2 cycle, with decode stalling 1 cycle while interrupt is pending");
    
    d2f_cmd = `ARMLEOCPU_D2F_CMD_NONE;
    req_ready = 1;

    #1

    `assert_equal(req_valid, 1);
    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, pc);
    f2d_assert_invalid();
    dbg_assert_busy();

    @(negedge clk);

    req_ready = 0;
    interrupt_pending = 1;

    #1

    `assert_equal(req_valid, 0);
    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    //`assert_equal(req_address, pc);
    `assert_equal(f2d_valid, 1);
    `assert_equal(f2d_type, `F2E_TYPE_INTERRUPT_PENDING);
    `assert_equal(f2d_pc, pc);
    dbg_assert_busy();

    @(negedge clk);

    interrupt_pending = 0;


    `TESTBENCH_START("Testbench: Fetch should handle cache response stalled 2 cycle");
    
    resp_valid = 0;
    req_ready = 1;

    #1

    `assert_equal(req_valid, 1);
    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, pc + 4);
    f2d_assert_invalid();
    dbg_assert_busy();

    pc = pc + 4;

    @(negedge clk);

    req_ready = 0;
    resp_valid = 0;

    #1

    `assert_equal(req_valid, 0);
    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    //`assert_equal(req_address, pc);
    dbg_assert_busy();

    // TODO: Test debug while cache request active
    // TODO: Test debug
    // TODO: Test flush while cache request active
    // TODO: Test branch while cache request active

    // TODO: Move to verilator

    /*

    c_done = 1;
    resp_read_data = 205;

    d2f_ready = 0;

    #1
    
    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    `assert_equal(f2d_valid, 1);
    `assert_equal(f2d_type, `F2E_TYPE_INSTR);
    `assert_equal(f2d_instr, 205);
    `assert_equal(f2d_status, `CACHE_RESPONSE_SUCCESS);
    `assert_equal(f2d_pc, 32'h500);
    dbg_assert_busy();

    @(negedge clk);

    c_done = 0;
    d2f_ready = 1;
    #1

    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    `assert_equal(f2d_valid, 1);
    `assert_equal(f2d_type, `F2E_TYPE_INSTR);
    `assert_equal(f2d_instr, 205);
    `assert_equal(f2d_status, `CACHE_RESPONSE_SUCCESS);
    `assert_equal(f2d_pc, 32'h500);
    dbg_assert_busy();

    @(negedge clk);

    c_done = 0;

    #1

    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    `assert_equal(f2d_valid, 1);
    `assert_equal(f2d_type, `F2E_TYPE_INTERRUPT_PENDING);
    `assert_equal(f2d_instr, 205);
    `assert_equal(f2d_pc, 32'h500);
    `assert_equal(f2d_status, `CACHE_RESPONSE_SUCCESS);
    dbg_assert_busy();

    @(negedge clk);

    interrupt_pending = 0;
    c_done = 0;
    d2f_ready = 1;
    d2f_cmd = `ARMLEOCPU_D2F_CMD_START_BRANCH;
    d2f_branchtarget = 32'h208;

    #1

    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h208);

    f2d_assert_invalid();

    dbg_assert_busy();
    @(negedge clk);

/*
    `TESTBENCH_START("Testbench: Fetch should handle cache response stalled 2 cycle, with decode stalling 1 cycle while dbg pending, then issue jump");
    
    d2f_cmd = `ARMLEOCPU_D2F_CMD_NONE;
    c_done = 0;

    #1

    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h208);
    f2d_assert_invalid();
    dbg_assert_busy();

    @(negedge clk);

    c_done = 0;
    dbg_mode = 1;
    #1

    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h208);
    f2d_assert_invalid();
    dbg_assert_busy();

    @(negedge clk);

    c_done = 1;
    resp_read_data = 109;

    d2f_ready = 0;

    #1
    
    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    `assert_equal(f2d_valid, 1);
    `assert_equal(f2d_type, `F2E_TYPE_INSTR);
    `assert_equal(f2d_instr, 109);
    `assert_equal(f2d_pc, 32'h208);
    `assert_equal(f2d_status, `CACHE_RESPONSE_SUCCESS);
    dbg_assert_busy();

    @(negedge clk);

    c_done = 0;
    d2f_ready = 1;
    #1

    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    `assert_equal(f2d_valid, 1);
    `assert_equal(f2d_type, `F2E_TYPE_INSTR);
    `assert_equal(f2d_instr, 109);
    `assert_equal(f2d_pc, 32'h208);
    `assert_equal(f2d_status, `CACHE_RESPONSE_SUCCESS);
    `assert_equal(dbg_pipeline_busy, 0);
    `assert_equal(dbg_cmd_ready, 0);

    @(negedge clk);

    c_done = 0;

    #1

    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    f2d_assert_invalid();
    `assert_equal(dbg_pipeline_busy, 0);
    `assert_equal(dbg_cmd_ready, 0);

    @(negedge clk);

    dbg_cmd_valid = 1;
    dbg_cmd = `DEBUG_CMD_JUMP;
    dbg_arg0_i = 32'h304;

    #1

    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    f2d_assert_invalid();
    `assert_equal(dbg_pipeline_busy, 0);
    `assert_equal(dbg_cmd_ready, 1);


    @(negedge clk);

    dbg_mode = 0;

    c_done = 0;

    #1

    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h304);
    f2d_assert_invalid();
    dbg_assert_busy();

    @(negedge clk);


    `TESTBENCH_START("Testbench: Start fetch then stall cache and branch while active cache request");

    c_done = 0;
    #1

    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h304);
    f2d_assert_invalid();
    dbg_assert_busy();

    @(negedge clk);

    c_done = 0;
    d2f_ready = 1;
    d2f_branchtarget = 32'h200;
    d2f_cmd = `ARMLEOCPU_D2F_CMD_START_BRANCH;

    #1


    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h304);
    f2d_assert_invalid();
    dbg_assert_busy();
    

    @(negedge clk);

    d2f_ready = 1;
    d2f_cmd = `ARMLEOCPU_D2F_CMD_NONE;

    #1

    // Cache still stalled
    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h304);
    f2d_assert_invalid();
    dbg_assert_busy();
    `assert_equal(u0.branched, 1);

    @(negedge clk);

    // Cache finally responds
    c_done = 1;
    resp_read_data = 104;

    d2f_ready = 1;

    #1

    // Next fetch should start from branch location
    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h200);

    // No valid should go high
    f2d_assert_invalid();
    dbg_assert_busy();

    @(negedge clk);

    c_done = 0;
    d2f_ready = 1;

    #1

    // Stalled cycle
    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h200);

    // No valid should go high
    f2d_assert_invalid();
    dbg_assert_busy();

    @(negedge clk);

    c_done = 1;
    resp_read_data = 1501;
    d2f_ready = 0;

    #1

    `assert_equal(req_cmd, `CACHE_CMD_NONE);
    `assert_equal(f2d_valid, 1);
    `assert_equal(f2d_status, `CACHE_RESPONSE_SUCCESS);
    `assert_equal(f2d_type, `F2E_TYPE_INSTR);
    `assert_equal(f2d_instr, 1501);
    `assert_equal(f2d_pc, 32'h200);
    dbg_assert_busy();

    @(negedge clk);

    c_done = 0;
    d2f_ready = 1;

    #1

    `assert_equal(req_cmd, `CACHE_CMD_EXECUTE);
    `assert_equal(req_address, 32'h204);
    `assert_equal(f2d_valid, 1);
    `assert_equal(f2d_status, `CACHE_RESPONSE_SUCCESS);
    `assert_equal(f2d_type, `F2E_TYPE_INSTR);
    `assert_equal(f2d_instr, 1501);
    `assert_equal(f2d_pc, 32'h200);
    dbg_assert_busy();


    @(negedge clk);

    // TODO: Add test case: 

    // TODO: Add f2d_status non success values testing


    // TODO: Add f2d_valid && (f2d_type == `F2E_TYPE_INTERRUPT_PENDING) && !d2f_ready test case
    
    // TODO: ADd DEBUG_CMD_READ_PC test
    
    
    

    // TODO: Test cases: 
    // Debug entering and debug commands
    */

    `TESTBENCH_START("Testbench: Tests passed");
    $finish;
end


endmodule
