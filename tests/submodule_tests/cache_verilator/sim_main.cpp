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

#define AXI_ADDR_TYPE uint64_t
#define AXI_ID_TYPE uint8_t
#define AXI_DATA_TYPE uint32_t
#define AXI_STROBE_TYPE uint8_t

#define AXI_SIMPLIFIER_TEMPLATED axi_simplifier<AXI_ADDR_TYPE, AXI_ID_TYPE, AXI_DATA_TYPE, AXI_STROBE_TYPE>


axi_addr<AXI_ADDR_TYPE, AXI_ID_TYPE> * ar;
axi_r<AXI_ID_TYPE, AXI_DATA_TYPE> * r;

axi_addr<AXI_ADDR_TYPE, AXI_ID_TYPE> * aw;
axi_w<AXI_DATA_TYPE, AXI_STROBE_TYPE> * w;
axi_b<AXI_ID_TYPE> * b;
axi_interface<AXI_ADDR_TYPE, AXI_ID_TYPE, AXI_DATA_TYPE, AXI_STROBE_TYPE> * interface;

AXI_SIMPLIFIER_TEMPLATED * simplifier;

const int DEPTH_WORDS = 16 * 1024;
const int DEPTH_BYTES = DEPTH_WORDS * sizeof(AXI_DATA_TYPE);

AXI_DATA_TYPE storage[DEPTH_WORDS * 2]; // Two sections, one cached one not


void paddr_to_location(AXI_ADDR_TYPE * paddr, uint32_t * location, uint8_t * location_missing) {
    bool cached_location = (*paddr & (1 << 31)) ? 1 : 0;
    bool inside_cached_location = ((*paddr & (~(1UL << 31))) < DEPTH_WORDS);
    // cout << cached_location << endl << inside_cached_location << endl;
    if(*paddr < DEPTH_BYTES) {
        *location = *paddr;
        *location_missing = 0;
    } else if(cached_location && inside_cached_location) { // Cache location storage
        *location = (*paddr - (1 << 31)) + DEPTH_WORDS;
        *location_missing = 0;
    } else {
        *location_missing = 1;
    }
}

void read_callback(AXI_SIMPLIFIER_TEMPLATED * simplifier, AXI_ADDR_TYPE addr, AXI_DATA_TYPE * rdata, uint8_t * rresp) {
    
    uint32_t location;
    uint8_t location_missing;
    paddr_to_location(&addr, &location, &location_missing);

    if(location_missing) {
        *rresp = 0b11; // address error
        *rdata = 0xDEADBEEF;
    } else {
        *rresp = 0b00;
        *rdata = storage[location];
    }
    cout << "Read callback: addr = " << addr << ", rdata = " << *rdata << ", rresp = " << (int)(*rresp) << endl;
}

void write_callback(AXI_SIMPLIFIER_TEMPLATED * simplifier, AXI_ADDR_TYPE addr, AXI_DATA_TYPE * wdata, uint8_t * wresp) {
    cout << "Write callback: addr = " << addr << ", rdata = " << *wdata << ", rresp = " << (int)(*wresp) << endl;
}

void update_callback(AXI_SIMPLIFIER_TEMPLATED * simplifier) {
    cout << "Update callback" << endl;
    TOP->eval();
}

uint8_t axi_arsize = 3;
AXI_ID_TYPE axi_arid = 0;

AXI_ID_TYPE axi_rid = 0;

uint8_t axi_awlen = 0;
uint8_t axi_awsize = 3;
AXI_ID_TYPE axi_awid = 0;
uint8_t axi_awburst = 0b01; // INCR

AXI_ID_TYPE axi_bid = 0;

void test_init() {
    expected_response_queue = new queue<expected_response>;
    

    aw = new axi_addr<AXI_ADDR_TYPE, AXI_ID_TYPE>(
        &TOP->io_axi_awvalid,
        &TOP->io_axi_awready,
        &TOP->io_axi_awaddr,
        &axi_awlen,
        &axi_awsize,
        &axi_awburst,
        &axi_awid,
        &TOP->io_axi_awprot,
        &TOP->io_axi_awlock
    );
    w = new axi_w<AXI_DATA_TYPE, AXI_STROBE_TYPE>(
        &TOP->io_axi_wvalid,
        &TOP->io_axi_wready,
        &TOP->io_axi_wdata,
        &TOP->io_axi_wstrb,
        &TOP->io_axi_wlast
    );
    b = new axi_b<AXI_ID_TYPE>(
        &TOP->io_axi_bvalid,
        &TOP->io_axi_bready,
        &axi_bid,
        &TOP->io_axi_bresp
    );
    

    ar = new axi_addr<AXI_ADDR_TYPE, AXI_ID_TYPE>(
        &TOP->io_axi_arvalid,
        &TOP->io_axi_arready,
        &TOP->io_axi_araddr,
        &TOP->io_axi_arlen,
        &axi_arsize,
        &TOP->io_axi_arburst,
        &axi_arid,
        &TOP->io_axi_arprot,
        &TOP->io_axi_arlock
    );
    r = new axi_r<AXI_ID_TYPE, AXI_DATA_TYPE>(
        &TOP->io_axi_rvalid,
        &TOP->io_axi_rready,
        &TOP->io_axi_rresp,
        &TOP->io_axi_rdata,
        &axi_rid,
        &TOP->io_axi_rlast
    );

    interface = new axi_interface<AXI_ADDR_TYPE, AXI_ID_TYPE, AXI_DATA_TYPE, AXI_STROBE_TYPE>(
        ar, r,
        aw, w, b
    );
    simplifier = new axi_simplifier<AXI_ADDR_TYPE, AXI_ID_TYPE, AXI_DATA_TYPE, AXI_STROBE_TYPE>(
        interface, &read_callback, &write_callback, &update_callback);
}

void resp_calculate() {
    static uint8_t resp_stalled_counter = 0;

    
    expected_response resp;

    TOP->resp_ready = 0;
    TOP->eval();
    if(TOP->resp_valid && (!expected_response_queue->empty())) {
        cout << "Checking response resp_valid = " << (int)(TOP->resp_valid) << endl;
        resp = expected_response_queue->front();
        check(!expected_response_queue->empty(), "Unexpected response");
        check(resp.status == TOP->resp_status, "Status " + to_string(TOP->resp_status) + " does not match expected " + to_string(resp.status));
        if((resp.status == CACHE_RESPONSE_SUCCESS) && resp.check_load_data) {
            check(TOP->resp_load_data == resp.load_data, "Unexpected load data value");
        }
        
        if(resp_stalled_counter == 2) {
            TOP->resp_ready = 1;
            expected_response_queue->pop();
            resp_stalled_counter = 0;
            cout << "[" << to_string(simulation_time) << "]" << "Accepting response remaining responses: " << expected_response_queue->size() << endl;
            
        } else {
            resp_stalled_counter++;
        }
    }
}

void cache_cycle() {
    next_cycle();
    simplifier->cycle();
    TOP->resp_ready = 0;
    TOP->eval();
    resp_calculate();
    TOP->eval();
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


void virtual_resolve(AXI_ADDR_TYPE * paddr, uint32_t * location, uint8_t * pagefault, uint8_t * accessfault) {
    uint8_t location_missing = 0;

    // TODO: Proper implementation
    assert(TOP->req_csr_satp_mode_in == 0);
    assert(TOP->req_csr_mstatus_mprv_in == 0);

    *paddr = TOP->req_address;
    paddr_to_location(paddr, location, &location_missing);
    *pagefault = 0;
    *accessfault = 0 || location_missing;
}

void calculate_cache_response() {
    expected_response resp;
    uint8_t pagefault, accessfault;
    AXI_ADDR_TYPE paddr;
    uint32_t location;
    resp.check_load_data = 0;

    check(TOP->req_valid, "calculate_cache_response called without request");
    if(TOP->req_cmd == CACHE_CMD_LOAD) {
        virtual_resolve(&paddr, &location, &pagefault, &accessfault);
        if(!check_load_type(TOP->req_load_type)) {
            resp.status = CACHE_RESPONSE_UNKNOWNTYPE;
        } else if(!check_alignment(TOP->req_load_type, TOP->req_address)) {
            resp.status = CACHE_RESPONSE_MISSALIGNED;
        } else if(pagefault) {
            resp.status = CACHE_RESPONSE_PAGEFAULT;
        } else if(accessfault) {
            resp.status = CACHE_RESPONSE_ACCESSFAULT;
        } else {
            resp.check_load_data = 1;
            resp.status = CACHE_RESPONSE_SUCCESS;
            if(TOP->req_load_type == LOAD_WORD) {
                resp.load_data = storage[location];
            } else if(TOP->req_load_type == LOAD_HALF_UNSIGNED) {
                resp.load_data = storage[location] & 0xFFFF;
            } else if(TOP->req_load_type == LOAD_HALF) {
                resp.load_data = storage[location] & 0xFFFF;
                resp.load_data = resp.load_data | ((resp.load_data >> 14) & 1 ? 0xFFFF0000 : 0);
            } else if(TOP->req_load_type == LOAD_BYTE_UNSIGNED) {
                resp.load_data = storage[location] & 0xFF;
            } else if(TOP->req_load_type == LOAD_BYTE) {
                resp.load_data = storage[location] & 0xFF;
                resp.load_data = resp.load_data | ((resp.load_data >> 6) & 1 ? 0xFFFFFF00 : 0);
            }
            
            //check(0, "Unimplemented check, please implement it");
        }
    // TODO: Implement other operations including atomics
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
    TOP->eval();
    int timeout = 0;

    while((!(TOP->req_ready)) && timeout < 100) {
        timeout++;
        cache_cycle();
        cout << "[" << simulation_time << "]" << "one cycle inside while ready = " << int(TOP->req_ready) << endl;
    }
    cout << "[" << simulation_time << "]" << "Outside while ready = " << int(TOP->req_ready) << endl;
    cache_cycle();

    TOP->req_valid = 0;
    TOP->eval();
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
    check(timeout < 100, "Waiting for all response timeout");

    cout << "[" << to_string(simulation_time) << "]" << "Cache wait: All responses done" << endl;

    timeout = 0;
    while((simplifier->state != 0) && timeout < 100) {
        timeout++;
        cache_cycle();
    }
    check(timeout < 100, "Waiting for all AXI transactions timeout");
}

#include "verilator_template_main_start.cpp"
    utils_init();
    test_init();
    TOP->rst_n = 0;
    TOP->req_valid = 0;
    TOP->resp_ready = 0;
    cache_configure();
    cache_cycle();
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

    
    
    storage[0] = 0xAABBCCDD; // Just some test value
    storage[DEPTH_WORDS] = 0xBBCCDDEE;
    // Word
    start_test("Cache: First Read from uncached");
    cache_operation(CACHE_CMD_LOAD, 0, LOAD_WORD);

    start_test("Cache: First Read from cached");
    cache_operation(CACHE_CMD_LOAD, (1 << 31), LOAD_WORD);


    // half
    start_test("Cache: Half UNSIGNED Read from uncached");
    cache_operation(CACHE_CMD_LOAD, 0, LOAD_HALF_UNSIGNED);

    start_test("Cache: Half Read from cached");
    cache_operation(CACHE_CMD_LOAD, (1 << 31), LOAD_HALF_UNSIGNED);

    start_test("Cache: Half Read from uncached");
    cache_operation(CACHE_CMD_LOAD, 0, LOAD_HALF);

    start_test("Cache: Half Read from cached");
    cache_operation(CACHE_CMD_LOAD, (1 << 31), LOAD_HALF);


    // BYte
    start_test("Cache: Byte UNSIGNED Read from uncached");
    cache_operation(CACHE_CMD_LOAD, 0, LOAD_BYTE_UNSIGNED);

    start_test("Cache: Byte UNSIGNED Read from cached");
    cache_operation(CACHE_CMD_LOAD, (1 << 31), LOAD_BYTE_UNSIGNED);

    start_test("Cache: Byte Read from uncached");
    cache_operation(CACHE_CMD_LOAD, 0, LOAD_BYTE);

    start_test("Cache: Byte Read from cached");
    cache_operation(CACHE_CMD_LOAD, (1 << 31), LOAD_BYTE);
    

    start_test("Cache: flushing all responses");
    cache_wait_for_all_responses();
    cache_cycle();
    cache_cycle();
    cache_cycle();
    cache_cycle();

    // TODO: Make sure that queue is empty before leaving
#include <verilator_template_footer.cpp>
