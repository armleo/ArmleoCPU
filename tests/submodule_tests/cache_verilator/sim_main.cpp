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


#include <Varmleocpu_cache.h>
#define TRACE
#define TOP_MODULE_DECLARATION Varmleocpu_cache * armleocpu_cache;
#define TOP_ALLOCATION armleocpu_cache = new Varmleocpu_cache;
#include "verilator_template_header.cpp"

#include "utils.cpp"


const uint8_t CACHE_CMD_NONE = 0;
const uint8_t CACHE_CMD_EXECUTE = 1;
const uint8_t CACHE_CMD_LOAD = 2;
const uint8_t CACHE_CMD_STORE = 3;
const uint8_t CACHE_CMD_FLUSH_ALL = 4;
const uint8_t CACHE_CMD_LOAD_RESERVE = 5;
const uint8_t CACHE_CMD_STORE_CONDITIONAL = 6;


const uint8_t LOAD_BYTE = 0b000;
const uint8_t LOAD_BYTE_UNSIGNED = 0b100;
const uint8_t LOAD_HALF = 0b001;
const uint8_t LOAD_HALF_UNSIGNED = 0b101;
const uint8_t LOAD_WORD = 0b010;

const uint8_t CACHE_RESPONSE_SUCCESS = (0);
const uint8_t CACHE_RESPONSE_ACCESSFAULT = (1);
const uint8_t CACHE_RESPONSE_PAGEFAULT = (2);
const uint8_t CACHE_RESPONSE_MISSALIGNED = (3);
const uint8_t CACHE_RESPONSE_UNKNOWNTYPE = (4);
const uint8_t CACHE_RESPONSE_ATOMIC_FAIL = (5);

class expected_response {
    public:
        bool check_load_data;
        uint32_t load_data;
        uint8_t status;
};

queue<expected_response> * expected_response_queue;

void queue_init() {
    expected_response_queue = new queue<expected_response>;
}

void cache_cycle() {
    // Do: Check response data
    expected_response resp;
    if(TOP->resp_valid) {
        cout << "Checking response resp_valid = " << (int)(TOP->resp_valid) << endl;
        resp = expected_response_queue->front();
        check(!expected_response_queue->empty(), "Unexpected response");
        check(resp.status == TOP->resp_status, "Status does not match expected");
        if((resp.status == CACHE_RESPONSE_SUCCESS) && resp.check_load_data) {
            check(TOP->resp_load_data == resp.load_data, "Unexpected load data value");
        }
        uint8_t should_accept_resp = rand() % 2;
        if(should_accept_resp) {
            cout << "Accepting response" << endl;
            TOP->resp_ready = 1;
            expected_response_queue->pop();
        }
    }
    next_cycle();
    TOP->resp_ready = 0;
}

bool check_load_type(uint8_t load_type) {
    return (load_type == LOAD_WORD)
    || (load_type == LOAD_HALF_UNSIGNED)
    || (load_type == LOAD_HALF)
    || (load_type == LOAD_BYTE)
    || (load_type == LOAD_BYTE_UNSIGNED);
}

bool check_alignment(uint8_t type_in, uint32_t addr_in) {
    uint8_t addr = addr_in & 0b11;
    uint8_t type = type_in & 0b11;
    if(type == 0b10) {
        return addr == 0;
    } else if(type == 0b01) {
        return (addr & 1) == 0;
    } else if(type == 0b00) {
        return true;
    }
    return false;
}

void calculate_cache_response() {
    expected_response resp;
    check(TOP->req_valid, "calculate_cache_response called without request");
    if(TOP->req_cmd == CACHE_CMD_LOAD) {
        if(!check_load_type(TOP->req_load_type)) {
            resp.status = CACHE_RESPONSE_UNKNOWNTYPE;
        } else if(!check_alignment(TOP->req_load_type, TOP->req_address)) {
            resp.status = CACHE_RESPONSE_MISSALIGNED;
        } else {
            // TODO: Implement others
            resp.check_load_data = 1;
            resp.status = CACHE_RESPONSE_SUCCESS;
            check(0, "Unimplemented check, please implement it");
        }
    } else {
        check(0, "Unimplemented check, please implement it");
    }

    expected_response_queue->push(resp);
}

void cache_operation(uint8_t op, uint32_t addr, uint8_t type) {
    TOP->req_valid = 1;
    TOP->req_cmd = op;
    TOP->req_address = addr;
    if(op == CACHE_CMD_LOAD) {
        TOP->req_load_type = type;
        TOP->req_store_type = rand() & 0b11;
        TOP->req_store_data = rand();
    }
    calculate_cache_response();
    int timeout = 0;
    while((!TOP->req_ready) && timeout < 100) {
        timeout++;
        cache_cycle();
    }
    check(TOP->req_ready, "Cache operation not accepted in time");
    if(timeout == 0) {
        cache_cycle(); // Make sure that request was accepted
    }
    
    TOP->req_valid = 0;
}

void cache_configure(
    uint8_t satp_mode = 0,
    uint32_t satp_ppn = 0,
    uint8_t mprv = 0,
    uint8_t mxr = 0,
    uint8_t sum = 0,
    uint8_t mpp = 0,
    uint8_t mcurpriv = 0b11) {
    TOP->req_csr_satp_mode_in = satp_mode;
    TOP->req_csr_satp_ppn_in = satp_ppn;
    TOP->req_csr_mstatus_mprv_in = mprv;
    TOP->req_csr_mstatus_mxr_in = mxr;
    TOP->req_csr_mstatus_sum_in = sum;
    TOP->req_csr_mstatus_mpp_in = mpp;
    TOP->req_csr_mcurrent_privilege_in = mcurpriv;
}

void cache_wait_for_all_responses() {
    int timeout = 0;
    while((!expected_response_queue->empty()) && timeout < 100) {
        timeout++;
        cache_cycle();
    }
    check(timeout == 100, "Waiting for all response timeout");
}

#include "verilator_template_main_start.cpp"
    utils_init();
    queue_init();
    TOP->rst_n = 0;
    next_cycle();
    TOP->rst_n = 1;
    
    cache_configure();
    
    for(uint8_t i = 1; i < 4; i++) {
        start_test("Cache: Missaligned Load word addr[1:0] = " + std::to_string((int)(i)));
        cache_operation(CACHE_CMD_LOAD, i, LOAD_WORD);
    }


    for(uint8_t i = 1; i < 4; i = i + 2) {
        start_test("Cache: Missaligned Load half addr[1:0] = " + std::to_string((int)(i)));
        cache_operation(CACHE_CMD_LOAD, i, LOAD_HALF);
        cache_operation(CACHE_CMD_LOAD, i, LOAD_HALF_UNSIGNED);
    }

    start_test("Cache: Unknown type test");
    cache_operation(CACHE_CMD_LOAD, 0, 0b110);
    cache_operation(CACHE_CMD_LOAD, 0, 0b111);

    start_test("Cache: flushing all responses");
    cache_wait_for_all_responses();
    
    




    cache_cycle();
    cache_cycle();
    cache_cycle();
    cache_cycle();

    // TODO: Make sure that queue is empty before leaving
#include <verilator_template_footer.cpp>
