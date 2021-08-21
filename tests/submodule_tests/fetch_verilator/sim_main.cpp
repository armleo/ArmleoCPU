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


#include <Varmleocpu_fetch.h>
#define TRACE
#define TOP_MODULE_DECLARATION Varmleocpu_fetch * armleocpu_fetch;
#define TOP_ALLOCATION armleocpu_fetch = new Varmleocpu_fetch;
#include "verilator_template_header.cpp"

#include <bitset>
#include <stdint.h>

const uint8_t CACHE_CMD_NONE = 0;
const uint8_t CACHE_CMD_EXECUTE = 1;
const uint8_t CACHE_CMD_FLUSH_ALL = 4;


const uint8_t CACHE_RESPONSE_SUCCESS = (0);
const uint8_t CACHE_RESPONSE_ACCESSFAULT = (1);
const uint8_t CACHE_RESPONSE_PAGEFAULT = (2);
const uint8_t CACHE_RESPONSE_MISSALIGNED = (3);
const uint8_t CACHE_RESPONSE_UNKNOWNTYPE = (4);
const uint8_t CACHE_RESPONSE_ATOMIC_FAIL = (5);

const uint8_t F2E_TYPE_INSTR = 0;
const uint8_t F2E_TYPE_INTERRUPT_PENDING = 1;

const uint8_t ARMLEOCPU_D2F_CMD_FLUSH = (2);
const uint8_t ARMLEOCPU_D2F_CMD_START_BRANCH = (1);
const uint8_t ARMLEOCPU_D2F_CMD_NONE = (0);

const uint8_t DEBUG_CMD_NONE = (0);
// Reserved command NONE

// handled by debug unit:
const uint8_t DEBUG_CMD_IFLUSH = (1);
const uint8_t DEBUG_CMD_READ_PC = (2);

typedef uint32_t XTYPE;

XTYPE pc;
XTYPE instr_pc;

void req_assert(uint8_t op, XTYPE addr) {
    check_equal(TOP->req_valid, (op != CACHE_CMD_NONE));
    check_equal(TOP->req_cmd, op);
    if(op != CACHE_CMD_NONE) {
        check_equal(TOP->req_address, addr);
    }
}

const uint8_t INVALID = 0;
const uint8_t INSTR = 1;
const uint8_t INTERRUPT_PENDING = 2;

void f2d_assert(uint8_t assert_type,
        XTYPE assert_pc = pc,
        XTYPE assert_instr = 0,
        uint8_t assert_status = CACHE_RESPONSE_SUCCESS
    ) {
    if(assert_type == INVALID) {
        check_equal(TOP->f2d_valid, 0);
    } else if(assert_type == INSTR) {
        check_equal(TOP->f2d_valid, 1);
        check_equal(TOP->f2d_type, F2E_TYPE_INSTR);
        check_equal(TOP->f2d_pc, assert_pc);
        check_equal(TOP->f2d_instr, assert_instr);
        check_equal(TOP->f2d_status, assert_status);
    } else if(assert_type == INTERRUPT_PENDING) {
        check_equal(TOP->f2d_valid, 1);
        check_equal(TOP->f2d_type, F2E_TYPE_INTERRUPT_PENDING);
        check_equal(TOP->f2d_pc, assert_pc);
    }
}



const uint8_t BUSY = 3;

void dbg_assert(uint8_t assert_type) {
    if(assert_type == BUSY) {
        check_equal(TOP->dbg_pipeline_busy, 1);
        check_equal(TOP->dbg_cmd_ready, 0);
    } else check_equal(0, 1); // TODO: Implement NOT READY
}

const uint8_t READY = 4;
const uint8_t VALID = 5;
const uint8_t READY_VALID = 6;
const uint8_t NONE = 7;

void cache_resp(uint8_t poke_type,
    XTYPE read_data = 66,
    uint8_t status = CACHE_RESPONSE_SUCCESS
    ) {
    assert((poke_type == READY_VALID) || (poke_type == READY)|| (poke_type == VALID) || (poke_type == NONE));
    TOP->req_ready = (poke_type == READY_VALID) || (poke_type == READY);
    TOP->resp_valid = (poke_type == READY_VALID) || (poke_type == VALID);

    if(TOP->resp_valid) {
        TOP->resp_read_data = read_data;
        TOP->resp_status = status;
    } else {
        TOP->resp_read_data = rand();
        TOP->resp_status = rand();
    }
}


const uint8_t BRANCH = 8;
const uint8_t FLUSH = 9;
const uint8_t NOT_READY = 10;

void d2f_resp(uint8_t poke_type,
    XTYPE branchtarget = 0x6660
) {
    if(poke_type == READY) {
        TOP->d2f_ready = 1;
        TOP->d2f_cmd = ARMLEOCPU_D2F_CMD_NONE;
        TOP->d2f_branchtarget = rand();
    } else if(poke_type == NOT_READY) {
        TOP->d2f_ready = 0;
        TOP->d2f_cmd = rand();
        TOP->d2f_branchtarget = rand();
    } else if((poke_type == BRANCH) || (poke_type == FLUSH)) {
        TOP->d2f_ready = 1;
        TOP->d2f_cmd = (poke_type == BRANCH) ? ARMLEOCPU_D2F_CMD_START_BRANCH : ARMLEOCPU_D2F_CMD_FLUSH;
        TOP->d2f_branchtarget = branchtarget;
    } else {
        assert(0);
    }
}

void test_case_cache_accept() {
    cache_resp(READY);
    d2f_resp(READY);

    TOP->eval();

    req_assert(CACHE_CMD_EXECUTE, pc);
    f2d_assert(INVALID);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
    pc = pc + 4;
}

void test_case_cache_resp_stall(XTYPE data) {
    cache_resp(VALID, data, CACHE_RESPONSE_SUCCESS);
    d2f_resp(READY);

    TOP->eval();

    
    f2d_assert(VALID, instr_pc, data);
    req_assert(CACHE_CMD_EXECUTE, pc);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
}

void test_case_cache_resp_stall_branch(XTYPE data, XTYPE branchtarget) {
    cache_resp(VALID, data, CACHE_RESPONSE_SUCCESS);
    d2f_resp(BRANCH, branchtarget);

    TOP->eval();

    pc = branchtarget;
    f2d_assert(VALID, instr_pc, data);
    req_assert(CACHE_CMD_EXECUTE, pc);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
}

void test_case_cache_stall_flush(XTYPE branchtarget) {
    cache_resp(NONE);
    d2f_resp(FLUSH, branchtarget);

    TOP->eval();

    pc = branchtarget;
    f2d_assert(INVALID);
    req_assert(CACHE_CMD_FLUSH_ALL, pc);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;

}

void test_case_cache_resp_stall_flush(XTYPE data, XTYPE branchtarget) {
    cache_resp(VALID, data, CACHE_RESPONSE_SUCCESS);
    d2f_resp(FLUSH, branchtarget);

    TOP->eval();

    pc = branchtarget;
    f2d_assert(VALID, instr_pc, data);
    req_assert(CACHE_CMD_FLUSH_ALL, pc);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
}
void test_case_cache_flushaccept() {
    cache_resp(READY);
    d2f_resp(READY);

    TOP->eval();

    f2d_assert(INVALID);
    req_assert(CACHE_CMD_FLUSH_ALL, pc);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
}

void test_case_cache_flushresp_accept() {
    cache_resp(READY_VALID, rand(), CACHE_RESPONSE_SUCCESS);
    d2f_resp(READY);

    TOP->eval();

    f2d_assert(INVALID);
    req_assert(CACHE_CMD_EXECUTE, pc);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
    pc = pc + 4;
}


void test_case_cache_resp_accept(XTYPE data) {
    cache_resp(READY_VALID, data, CACHE_RESPONSE_SUCCESS);
    d2f_resp(READY);

    TOP->eval();

    
    f2d_assert(VALID, instr_pc, data);
    req_assert(CACHE_CMD_EXECUTE, pc);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
    pc = pc + 4;
}


void test_case_cache_stall() {
    cache_resp(NONE);
    d2f_resp(READY);

    TOP->eval();

    
    f2d_assert(INVALID);
    req_assert(CACHE_CMD_EXECUTE, pc);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
}


#include "verilator_template_main_start.cpp"
    TOP->rst_n = 0;
    next_cycle();
    TOP->rst_n = 1;
    
    cache_resp(NONE);

    TOP->interrupt_pending = 0;

    TOP->dbg_mode = 0;
    TOP->dbg_cmd_valid = 0;
    TOP->dbg_cmd = DEBUG_CMD_NONE;

    d2f_resp(NOT_READY);

    // Each test assumes that pc contains current pc
    // instr_pc contains expected pc for next instruction/interrupt
    // And each test assumes that fetch is in idle (req_done == 0)


    start_test("Starting fetch testing");

    start_test("start of fetch should start from 0x1000");
    pc = 0x1000;
    test_case_cache_accept();
    

    start_test("After one fetch and no d2f/dbg_mode next fetch should start");
    test_case_cache_resp_stall(0x88);

    start_test("After cache stall PC + 4 should not increment twice");
    test_case_cache_accept();
    test_case_cache_resp_stall(0x99);

    start_test("Fetch should handle cache response stalled 2 cycle");
    test_case_cache_stall();
    test_case_cache_accept();
    test_case_cache_resp_accept(0x88);
    test_case_cache_resp_stall(0x77);

    start_test("Fetch then branch should work properly");
    test_case_cache_accept();
    test_case_cache_resp_stall_branch(0x123, 0x2000);

    start_test("Fetch then branch should work properly pc = 0xFFFFFFFF");
    test_case_cache_accept();
    test_case_cache_resp_stall_branch(0x123, 0xFFFFFFFF);
    
    start_test("Fetch then flush should work properly");
    test_case_cache_accept();
    test_case_cache_resp_stall_flush(0x456, 0x3000);
    test_case_cache_flushaccept();
    test_case_cache_flushresp_accept();
    test_case_cache_resp_stall(0x77);


    start_test("Fetch then flush should work properly pc = 0xFFFFFFFF");
    test_case_cache_accept();
    test_case_cache_resp_stall_flush(0x456, 0xFFFFFFFF);
    test_case_cache_flushaccept();
    test_case_cache_flushresp_accept();
    test_case_cache_resp_stall(0x77);


    
    start_test("Flush while not active request");
    test_case_cache_stall();
    test_case_cache_stall_flush(0xF000);

    test_case_cache_flushaccept();
    test_case_cache_flushresp_accept();
    test_case_cache_resp_stall(0x77);



    // TODO: Test debug while cache request active
    // TODO: Test debug while cache flush request active
    // TODO: Test debug commands: read_pc, leave debug
    // TODO: Test debug then just leave

    // TODO; Test flush, branch while saved data, cache request active
    // TODO: Test stalled d2f
    // TODO: Test interrupt pending
    // TODO: Test flush, branch, debug -> interrupt pending
    // TODO: Fetch should handle cache response stalled 2 cycle, with decode stalling 1 cycle while dbg pending, then issue jump


    next_cycle();
#include <verilator_template_footer.cpp>
