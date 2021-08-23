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




XTYPE randx() {
    return rand();
}

uint8_t rand4() {
    return rand() & 0xF;
}

uint8_t rand8() {
    return rand() & 0xFF;
}

uint8_t rand1() {
    return rand() & 1;
}

XTYPE pc;
XTYPE instr_pc;

XTYPE saved_pc;
XTYPE saved_instr;
uint8_t saved_status;

class test_values {
    public:
    // assert
    // Hidden req_valid == (req_cmd != CACHE_CMD_NONE)
    uint8_t req_cmd;
    XTYPE req_address;

    // poke
    uint8_t req_ready;


    // Poke:
    uint8_t resp_valid;
    uint8_t resp_status;
    XTYPE resp_read_data;


    // Assert
    uint8_t f2d_check_type;
    /*
    uint8_t f2d_valid;
    uint8_t f2d_type;
    XTYPE f2d_instr;
    XTYPE f2d_pc;
    uint8_t f2d_status;
    */


    // Poke
    uint8_t d2f_ready;
    uint8_t d2f_cmd;
    XTYPE d2f_branchtarget;


    // Poke:
    uint8_t interrupt_pending;

    // Poke:
    uint8_t dbg_mode;
    uint8_t dbg_cmd_valid;
    uint8_t dbg_cmd;
    XTYPE dbg_arg0_i;

    // Assert:
    XTYPE dbg_arg0_o;
    uint8_t dbg_cmd_ready;
    uint8_t dbg_pipeline_busy;


};

void test_poke_assert(test_values t) {
    // ---- REQUEST POKE ----
    TOP->req_ready = t.req_ready;

    // ---- CACHE RESPONSE POKE ----
    TOP->resp_valid = t.resp_valid;
    if(t.resp_valid) {
        TOP->resp_status = t.resp_status;
        TOP->resp_read_data = t.resp_read_data;
    } else {
        TOP->resp_status = rand4();
        TOP->resp_read_data = randx();
    }

    // ---- D2F POKE ----
    TOP->d2f_ready = t.d2f_ready;
    if(t.d2f_ready) {
        TOP->d2f_cmd = t.d2f_cmd;
    } else {
        TOP->d2f_cmd = rand4();
    }

    if((t.d2f_ready == 1) && ((t.d2f_cmd == ARMLEOCPU_D2F_CMD_START_BRANCH) || (t.d2f_cmd == ARMLEOCPU_D2F_CMD_FLUSH))) {
        TOP->d2f_branchtarget = t.d2f_branchtarget;
    } else {
        TOP->d2f_branchtarget = randx();
    }


    // ---- INTERRUPT_PENDING ----
    TOP->interrupt_pending = t.interrupt_pending;

    // ---- DEBUG ----
    TOP->dbg_mode = t.dbg_mode;
    if(t.dbg_mode) {
        TOP->dbg_cmd_valid = t.dbg_cmd_valid;
        if(t.dbg_cmd_valid) {
            TOP->dbg_cmd = t.dbg_cmd;
            TOP->dbg_arg0_i = t.dbg_arg0_i;
        } else {
            TOP->dbg_cmd = rand4();
            TOP->dbg_arg0_i = randx();
        }
    } else {
        TOP->dbg_cmd_valid = rand1();
        TOP->dbg_cmd = rand4();
        TOP->dbg_arg0_i = randx();
    }

    // ---- UPDATE OUTPUT PINS ----
    TOP->eval();


    

    // ---- F2D ----
    // TODO: Add F2D Calculation



    if(t.f2d_check_type == 0) {
        check_equal(TOP->f2d_valid, 0);
    } else if(t.f2d_check_type == 1) { // Interrupt pending
        check_equal(TOP->f2d_valid, 1);
        check_equal(TOP->f2d_type, F2E_TYPE_INTERRUPT_PENDING);
        check_equal(TOP->f2d_pc, instr_pc);
    } else if(t.f2d_check_type == 2) { // F2D from cache
        check_equal(TOP->f2d_valid, 1);
        check_equal(TOP->f2d_type, F2E_TYPE_INSTR);
        check_equal(TOP->f2d_pc, instr_pc);
        check_equal(TOP->f2d_instr, TOP->resp_read_data);
        check_equal(TOP->f2d_status, TOP->resp_status);

        saved_instr = TOP->f2d_instr;
        saved_status = TOP->f2d_status;
        saved_pc = TOP->f2d_pc;
    } else if(t.f2d_check_type == 3) { // F2D from saved
        check_equal(TOP->f2d_valid, 1);
        check_equal(TOP->f2d_type, F2E_TYPE_INSTR);
        check_equal(TOP->f2d_pc, saved_pc);
        check_equal(TOP->f2d_instr, saved_instr);
        check_equal(TOP->f2d_status, saved_status);
    }

    // ---- CACHE REQUEST ----
    check_equal(TOP->req_valid, (t.req_cmd != CACHE_CMD_NONE));
    check_equal(TOP->req_cmd, t.req_cmd);
    if(t.req_cmd != CACHE_CMD_NONE) {
        check_equal(TOP->req_address, pc);
    }

    check_equal(TOP->dbg_pipeline_busy, t.dbg_pipeline_busy);
    check_equal(TOP->dbg_cmd_ready, t.dbg_cmd_ready);
    if(t.dbg_cmd_ready) {
        check_equal(TOP->dbg_arg0_o, t.dbg_arg0_o);
    }
    
    next_cycle();
}

/*
void test_case_resp(uint8_t cache_poke_type = READY,
    uint8_t d2f_poke_type = READY,

    // Cache resp data
    XTYPE read_data = 66,
    uint8_t status = CACHE_RESPONSE_SUCCESS,

    // D2F data
    XTYPE branchtarget = 0xFFFFFFFF) {
    
    cache_resp(cache_poke_type, read_data, status);
    d2f_resp(d2f_poke_type, branchtarget);

    TOP->eval();
}


void test_case_assert() {
    req_assert();
    f2d_assert();
    dbg_assert();

    next_cycle();
}

void test_case_cacheacceptexecute_d2fready(
    
) {
    cache_resp(READY);
    d2f_resp(READY);

    TOP->eval();

    req_assert(cache_cmd, pc);
    f2d_assert(INVALID);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
    pc = pc + 4;
}

void test_case_cacheresp_d2fready(XTYPE data, uint8_t cache_cmd = CACHE_CMD_EXECUTE) {
    cache_resp(VALID, data, CACHE_RESPONSE_SUCCESS);
    d2f_resp(READY);

    TOP->eval();

    
    f2d_assert(VALID, instr_pc, data);
    req_assert(CACHE_CMD_EXECUTE, pc);
    dbg_assert(BUSY);

    next_cycle();

    instr_pc = pc;
}

void test_case_cacheresp_d2fbranch(XTYPE data, XTYPE branchtarget) {
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

void test_case_cacheresp_d2fready_flush(XTYPE data, XTYPE branchtarget) {
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
*/

#include "verilator_template_main_start.cpp"
TOP->rst_n = 0;
next_cycle();
TOP->rst_n = 1;


// Each test assumes that pc contains current pc
// instr_pc contains expected pc for next instruction/interrupt
// And each test assumes that fetch is in idle (req_done == 0)


start_test("Starting fetch testing");




#define REQ_EXECUTE .req_cmd = CACHE_CMD_EXECUTE
#define REQ_FLUSH .req_cmd = CACHE_CMD_FLUSH_ALL

#define CACHE_RESP_NONE .req_ready = 0, .resp_valid = 0, .resp_status = rand4(), .resp_read_data = randx()
#define CACHE_RESP_READY .req_ready = 1, .resp_valid = 0, .resp_status = rand4(), .resp_read_data = randx()
#define CACHE_RESP_VALID .req_ready = 0, .resp_valid = 1, .resp_status = rand4(), .resp_read_data = randx()

#define F2D_NONE .f2d_check_type = 0
#define F2D_INTERRUPT_PENDING .f2d_check_type = 1, .f2d_pc = instr_pc
#define F2D_CACHED .f2d_check_type = 2
#define F2D_SAVED .f2d_check_type = 3

#define D2F_READY .d2f_ready = 1, .d2f_cmd = ARMLEOCPU_D2F_CMD_NONE, .d2f_branchtarget = randx()
#define D2F_BRANCH .d2f_ready = 1, .d2f_cmd = ARMLEOCPU_D2F_CMD_START_BRANCH, .d2f_branchtarget = randx()
#define D2F_FLUSH .d2f_ready = 1, .d2f_cmd = ARMLEOCPU_D2F_CMD_FLUSH, .d2f_branchtarget = randx()

#define DBG_BUSY .dbg_mode = 0, .dbg_cmd_valid = 0, .dbg_cmd = rand4(), .dbg_arg0_i = randx() \
, .dbg_cmd_ready = 0, .dbg_pipeline_busy = 1

#define INTERRUPT_IDLE .interrupt_pending = 0

test_values t;


start_test("start of fetch should start from 0x1000");
pc = 0x1000;
t = {REQ_EXECUTE, CACHE_RESP_READY, F2D_NONE, D2F_READY, INTERRUPT_IDLE, DBG_BUSY
}; test_poke_assert(t);



start_test("After one fetch and no d2f/dbg_mode next fetch should start");
instr_pc = pc;
pc = pc + 4;
t = {REQ_EXECUTE, CACHE_RESP_VALID, F2D_CACHED, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);



start_test("After cache stall PC + 4 should not increment twice");
t = {REQ_EXECUTE, CACHE_RESP_READY, F2D_NONE, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);

instr_pc = pc;
pc = pc + 4;
t = {REQ_EXECUTE, CACHE_RESP_VALID, F2D_CACHED, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);




start_test("Fetch should handle cache response stalled 2 cycle");
t = {REQ_EXECUTE, CACHE_RESP_NONE, F2D_NONE, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);

test_poke_assert(t);

t = {REQ_EXECUTE, CACHE_RESP_READY, F2D_NONE, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);

instr_pc = pc;
pc = pc + 4;
t = {REQ_EXECUTE, CACHE_RESP_VALID, F2D_CACHED, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);


start_test("Fetch then branch should work properly");
for(int i = 0; i < 100; i++) {
    t = {REQ_EXECUTE, CACHE_RESP_READY, F2D_NONE, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);
    t = {REQ_EXECUTE, CACHE_RESP_VALID, F2D_CACHED, D2F_BRANCH, INTERRUPT_IDLE, DBG_BUSY}; instr_pc = pc; pc = t.d2f_branchtarget; test_poke_assert(t);
}


start_test("Fetch then flush should work properly");
for(int i = 0; i < 100; i++) {
    t = {REQ_EXECUTE, CACHE_RESP_READY, F2D_NONE, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);
    t = {REQ_FLUSH, CACHE_RESP_VALID, F2D_CACHED, D2F_FLUSH, INTERRUPT_IDLE, DBG_BUSY}; instr_pc = pc; pc = t.d2f_branchtarget; test_poke_assert(t);
    t = {REQ_FLUSH, CACHE_RESP_READY, F2D_NONE, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);
    t = {REQ_EXECUTE, CACHE_RESP_VALID, F2D_NONE, D2F_READY, INTERRUPT_IDLE, DBG_BUSY}; test_poke_assert(t);
}
/*
//



start_test("Flush while not active request");
test_case_cache_stall();
test_case_cache_stall_flush(0xF000);

test_case_cache_flushaccept();
test_case_cache_flushresp_accept();
test_case_cacheresp_d2fready(0x77);
*/
/*
start_test("Enter debug mode while cache request is active");
test_case_cache_stall();
test_case_cacheacceptexecute_d2fready();
TOP->dbg_mode = 1;
test_case_cache_resp_no_req_stall(0x99);
*/
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
