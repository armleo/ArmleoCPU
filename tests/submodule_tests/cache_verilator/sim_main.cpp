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

const uint8_t LOAD_WORD = 0b010;

void cache_operation(uint8_t op, uint32_t addr, uint8_t type) {
    TOP->req_valid = 1;
    TOP->req_cmd = op;
    TOP->req_address = addr;
    if(op == CACHE_CMD_LOAD) {
        TOP->req_load_type = type;
        TOP->req_store_type = rand() & 0b11;
        TOP->req_store_data = rand();
    }
    int timeout = 0;
    while((!TOP->req_ready) && timeout < 100) {
        timeout++;
        next_cycle();
    }
    check(TOP->req_ready, "Cache operation not accepted in time");
    TOP->req_valid = 0;
    next_cycle();
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

#include "verilator_template_main_start.cpp"
    utils_init();
    TOP->rst_n = 0;
    next_cycle();
    TOP->rst_n = 1;
    
    cache_configure();

    start_test("Cache: Missaligned");
    cache_operation(CACHE_CMD_LOAD, 1, LOAD_WORD);

#include <verilator_template_footer.cpp>
