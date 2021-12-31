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

#include <bitset>

#include "random_utils.cpp"
#include "utils.cpp"


const uint8_t CACHE_CMD_NONE = 0;
const uint8_t CACHE_CMD_EXECUTE = 1;
const uint8_t CACHE_CMD_LOAD = 2;
const uint8_t CACHE_CMD_STORE = 3;
const uint8_t CACHE_CMD_FLUSH_ALL = 4;
const uint8_t CACHE_CMD_LOAD_RESERVE = 5;
const uint8_t CACHE_CMD_STORE_CONDITIONAL = 6;

const uint8_t BYTE = 0b00;
const uint8_t HALF = 0b01;
const uint8_t WORD = 0b10;

const uint8_t MACHINE = 0b11;
const uint8_t SUPERVISOR = 0b01;
const uint8_t USER = 0b00;

const uint32_t PTE_VALID_MASK = 1 << 0;
const uint32_t PTE_READ_MASK = 1 << 1;
const uint32_t PTE_WRITE_MASK = 1 << 2;
const uint32_t PTE_EXECUTE_MASK = 1 << 3;
const uint32_t PTE_USER_MASK = 1 << 4;
// 5th bit is just ignored by this cache
const uint32_t PTE_ACCESS_MASK = 1 << 6;
const uint32_t PTE_DIRTY_MASK = 1 << 7;

const uint32_t PTE_POINTER = PTE_VALID_MASK;

const uint32_t PTE_ALL = PTE_DIRTY_MASK |
    PTE_ACCESS_MASK |
    PTE_EXECUTE_MASK |
    PTE_WRITE_MASK | 
    PTE_READ_MASK | 
    PTE_VALID_MASK;

const uint8_t CACHE_RESPONSE_SUCCESS = (0);
const uint8_t CACHE_RESPONSE_ACCESSFAULT = (1);
const uint8_t CACHE_RESPONSE_PAGEFAULT = (2);
const uint8_t CACHE_RESPONSE_MISSALIGNED = (3);
const uint8_t CACHE_RESPONSE_UNKNOWNTYPE = (4);
const uint8_t CACHE_RESPONSE_ATOMIC_FAIL = (5);

class expected_response {
    public:
        bool check_read_data;
        uint32_t read_data;
        uint8_t status;
};

string to_string(expected_response resp) {
    return "status = 0x" + to_string(resp.status) + ", " +
        "check_read_data = " + to_string(resp.check_read_data) +
        (resp.check_read_data ? ", read_data = " + to_string(resp.read_data) : "");
}

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

const int DEPTH_WORDS = 8 * 1024 * 1024; // At least 2 megapages
const int DEPTH_BYTES = DEPTH_WORDS * sizeof(AXI_DATA_TYPE);

AXI_DATA_TYPE back_storage[DEPTH_WORDS * 2]; // Two sections, one cached one not, back storage is what is written or read in axi
AXI_DATA_TYPE expected_load_data[DEPTH_WORDS * 2]; // Same layout, but contains expected load data



void paddr_to_location(AXI_ADDR_TYPE paddr, uint32_t * location, uint8_t * location_missing) {
    AXI_ADDR_TYPE paddr_masked = (paddr & (~(1UL << 31)));
    bool cached_location = (paddr & (1UL << 31)) ? 1 : 0;
    bool inside_cached_location = (paddr_masked) < DEPTH_BYTES;
    
    // cout << hex << paddr << dec << endl
    //     << paddr_masked << endl
    //     << cached_location << endl
    //     << inside_cached_location << endl;
    if(paddr < DEPTH_BYTES) {
        *location = (paddr) >> 2;
        *location_missing = 0;
        cout << "[" << simulation_time << "][paddr_to_location] non cached, location = 0x" << hex << *location << dec << endl;
    } else if(cached_location && inside_cached_location) { // Cached location
        *location = (paddr_masked >> 2) + DEPTH_WORDS;
        *location_missing = 0;
        cout << "[" << simulation_time << "][paddr_to_location] cached, location = 0x" << hex << *location << dec << endl;
    } else {
        *location = 0;
        *location_missing = 1;
        cout << "[" << simulation_time << "][paddr_to_location] missing location = " << hex << *location << dec << endl;
    }
}

void read_callback(AXI_SIMPLIFIER_TEMPLATED * simplifier, AXI_ADDR_TYPE addr, AXI_DATA_TYPE * rdata, uint8_t * rresp) {
    
    uint32_t location;
    uint8_t location_missing;
    paddr_to_location(addr, &location, &location_missing);

    if(location_missing) {
        *rresp = 0b11; // address error
        *rdata = 0xDEADBEEF;
    } else {
        *rresp = 0b00;
        *rdata = back_storage[location];
    }
    cout << "[" << simulation_time << "][Read callback] addr = " << hex << addr
        << ", rdata = " << *rdata
        << ", rresp = " << (int)(*rresp) << dec << endl;
}

void write_callback(AXI_SIMPLIFIER_TEMPLATED * simplifier, AXI_ADDR_TYPE addr, AXI_DATA_TYPE * wdata, AXI_STROBE_TYPE * wstrb, uint8_t * wresp) {
    
    uint32_t location;
    uint8_t location_missing;
    paddr_to_location(addr, &location, &location_missing);

    if(location_missing) {
        *wresp = *wresp | 0b11;
    } else {
        *wresp = *wresp | 0b00;
        back_storage[location] = (
            ((*wstrb & 0b0001) ? (*wdata & 0xFF)       : (back_storage[location] & 0xFF)) | 
            ((*wstrb & 0b0010) ? (*wdata & 0xFF00)     : (back_storage[location] & 0xFF00)) | 
            ((*wstrb & 0b0100) ? (*wdata & 0xFF0000)   : (back_storage[location] & 0xFF0000)) | 
            ((*wstrb & 0b1000) ? (*wdata & 0xFF000000) : (back_storage[location] & 0xFF000000))
        );
    }
    
    cout << "[" << simulation_time << "][Write callback] addr = " << addr << ", wdata = " << *wdata << ", wstrb = " << wstrb << ", wresp = " << (int)(*wresp) << endl;
}

void update_callback(AXI_SIMPLIFIER_TEMPLATED * simplifier) {
    cout << "[" << simulation_time << "][Update callback]" << endl;
    TOP->eval();
}

AXI_ID_TYPE axi_arid = 0;

AXI_ID_TYPE axi_rid = 0;

uint8_t axi_awlen = 0;
AXI_ID_TYPE axi_awid = 0;
uint8_t axi_awburst = 0b01; // INCR

AXI_ID_TYPE axi_bid = 0;

void write_to_location(uint32_t location, AXI_DATA_TYPE wdata) {
    expected_load_data[location] = back_storage[location] = wdata;
}

void test_init() {
    expected_response_queue = new queue<expected_response>;
    
    // TODO: Fill back_storage with random data;
    for(int i = 0; i < DEPTH_WORDS * 2; i++) {
        write_to_location(i, rand());
    }

    aw = new axi_addr<AXI_ADDR_TYPE, AXI_ID_TYPE>(
        &TOP->io_axi_awvalid,
        &TOP->io_axi_awready,
        &TOP->io_axi_awaddr,
        &axi_awlen,
        &TOP->io_axi_awsize,
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
        &TOP->io_axi_arsize,
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

void resp_check_cycle() {
    expected_response resp;

    TOP->eval();
    if(TOP->resp_valid) {
        check(!expected_response_queue->empty(), "Unexpected response");
        resp = expected_response_queue->front();
        cout << "[" << simulation_time << "][resp_check_cycle] " << to_string(resp) << endl;

        check(resp.status == TOP->resp_status, "Status " + to_string(TOP->resp_status) + " does not match expected " + to_string(resp.status));
        if((resp.status == CACHE_RESPONSE_SUCCESS) && resp.check_read_data) {
            check(TOP->resp_read_data == resp.read_data, "Unexpected load data value");
        }
    
        expected_response_queue->pop();
        cout << "[" << simulation_time << "][resp_check_cycle]" << "Accepting response remaining responses: " << expected_response_queue->size() << endl;
        
    }
}

void cache_cycle() {
    next_cycle();
    simplifier->cycle();
    TOP->eval();
    resp_check_cycle();
    TOP->eval();
}


void read_physical_addr(AXI_ADDR_TYPE addr, AXI_DATA_TYPE * readdata, uint8_t * accessfault) {
    uint32_t location;
    uint8_t location_missing;
    paddr_to_location(addr, &location, &location_missing);
    if(!location_missing) {
        *readdata = expected_load_data[location];
        *accessfault = 0;
    } else {
        *accessfault = 1 | location_missing;
    }
}

template<typename T>
T bit_select(T n, uint8_t end, uint8_t begin) {
    // First we shift xx00 >> 2 -> begin
    return (n >> (begin)) & ((1 << (end - begin + 1)) - 1);
}

void virtual_resolve(uint8_t op, AXI_ADDR_TYPE * paddr, uint32_t * location, uint8_t * pagefault, uint8_t * accessfault) {
    uint8_t location_missing = 0;

    AXI_DATA_TYPE readdata;

    // First we calculate effective privilege levels
    // and if virtual memori is enabled

    uint8_t vm_privilege = 0;
    uint8_t vm_enabled = 0;

    if(TOP->req_csr_mcurrent_privilege_in == 3) {
        if(TOP->req_csr_mstatus_mprv_in == 0) {
            vm_privilege = TOP->req_csr_mcurrent_privilege_in;
        } else {
            vm_privilege = TOP->req_csr_mstatus_mpp_in;
        }
    } else {
        vm_privilege = TOP->req_csr_mcurrent_privilege_in;
    }

    if(vm_privilege != 3) {
        vm_enabled = TOP->req_csr_satp_mode_in;
    } else {
        vm_enabled = 0;
    }
    cout << "[" << simulation_time << "][PTW] req_address = " << hex << TOP->req_address << dec
        << ", vm_enabled = " << int(vm_enabled) << ","
        << "vm_privilege = 0x" << hex << int(vm_privilege) << dec << endl; 

    
    // Second we do MMU Page Table Walk

    AXI_ADDR_TYPE current_table_base;
    int8_t current_level;

    if(!vm_enabled) {
        paddr_to_location(TOP->req_address, location, &location_missing);
        *pagefault = 0;
        *accessfault = 0 || location_missing;
        *paddr = TOP->req_address;
    } else {
        
        *accessfault = 0;
        *pagefault = 0;

        current_table_base = TOP->req_csr_satp_ppn_in;
        current_level = 1;

        uint32_t pte_valid;
        uint32_t pte_read;
        uint32_t pte_write;
        uint32_t pte_execute;
        uint32_t pte_user;
        uint32_t pte_access;
        uint32_t pte_dirty;
        
        while(current_level >= 0) {
            // TODO: Do selection properly below
            AXI_ADDR_TYPE pte_addr = (current_table_base << 12) | ((current_level ? bit_select(TOP->req_address, 31, 22) : bit_select(TOP->req_address, 21, 12)) << 2);
            read_physical_addr(
                pte_addr,
                &readdata, accessfault
            );
            cout << "[" << simulation_time << "][PTW] "
                << "pte_addr = " << pte_addr << ","
                << "readdata = 0x" << readdata
                << ", accessfault = " << int(*accessfault) << endl;
            
            pte_valid = (readdata & PTE_VALID_MASK) ? 1 : 0;
            pte_read = (readdata & PTE_READ_MASK) ? 1 : 0;
            pte_write = (readdata & PTE_WRITE_MASK) ? 1 : 0;
            pte_execute = (readdata & PTE_EXECUTE_MASK) ? 1 : 0;
            pte_user = (readdata & PTE_USER_MASK) ? 1 : 0;
            pte_access = (readdata & PTE_ACCESS_MASK) ? 1 : 0;
            pte_dirty = (readdata & PTE_DIRTY_MASK) ? 1 : 0;


            uint8_t pte_invalid = (!pte_valid) || ((!pte_read) && pte_write);
            if(*accessfault) {
                *accessfault = 1;
                current_level = -1;
                cout << "[" << simulation_time << "][PTW] Expected PTW result: Accessfault ptw outside memory" << endl;
            } else if(pte_invalid) { // pte invalid
                cout << "[" << simulation_time << "][PTW] Expected PTW result: PTE invalid" << endl;
                *pagefault = 1;
                current_level = -1;
            } else if(pte_read || pte_execute) { // pte is leaf
                if((current_level == 1) && (bit_select(readdata, 19, 10) != 0)) { // pte missaligned
                    *pagefault = 1;
                    current_level = -1;
                    cout << "[" << simulation_time << "][PTW] Expected PTW result: PTE Missalligned" << endl;
                } else { // done
                    // cout << bit_select(readdata, 19, 10) << endl << int(current_level) << endl;
                    
                    *paddr = 
                        (AXI_DATA_TYPE(bit_select(readdata, 31, 20)) << 22)
                        | ((
                            (current_level ?
                                bit_select(TOP->req_address, 21, 12)
                                : bit_select(readdata, 19, 10))
                            ) << 12)
                        | bit_select(TOP->req_address, 11, 0);
                    current_level = -1;
                    cout << "[" << simulation_time << "][PTW] Expected PTW result: Done" << endl;
                }
            } else if(bit_select(readdata, 3, 0) == 0b0001) { // pte pointer
                if(current_level == 0) {
                    *pagefault = 1;
                    current_level = -1;
                    cout << "[" << simulation_time << "][PTW] Expected PTW result: pte pointer, but already too deep" << endl;
                } else {
                    current_level = current_level - 1;
                    current_table_base = bit_select(readdata, 31,10);
                    cout << "[" << simulation_time << "][PTW] Expected PTW result: pte pointer, going deeper" << endl;
                }
            }
        }
        

        // Then we use PTW result to calculate if access is allowed
        cout << "[" << simulation_time << "][PTW] After PTE fetch: "
        << "pagefault = 0b" << int(*pagefault)
        << ", accessfault = 0b" << int(*accessfault) << endl;
        
        if(!(*pagefault || *accessfault)) { // If no pagefault and no accessfault
            if((!(pte_read && pte_access)) &&
                (
                    (TOP->req_cmd == CACHE_CMD_LOAD) ||
                    (TOP->req_cmd == CACHE_CMD_LOAD_RESERVE)
                )) {
                cout << "[" << simulation_time << "][PTW] Pagefault: READ NOT ALLOWED" << endl;
                *pagefault = 1;
            } else if(!(pte_write && pte_access && pte_dirty) && (
                (TOP->req_cmd == CACHE_CMD_STORE) ||
                (TOP->req_cmd == CACHE_CMD_STORE_CONDITIONAL)
            )) {
                cout << "[" << simulation_time << "][PTW] Pagefault: WRITE NOT ALLOWED" << endl;
                *pagefault = 1;
            } else if(!(pte_execute && pte_access) && (TOP->req_cmd == CACHE_CMD_EXECUTE)) {
                cout << "[" << simulation_time << "][PTW] Pagefault: EXECUTE NOT ALLOWED" << endl;
                *pagefault = 1;
            } else if(vm_privilege == 1) {
                if((readdata & PTE_USER_MASK) && !TOP->req_csr_mstatus_sum_in) { // user bit set and sum not set
                    cout << "[" << simulation_time << "][PTW] Pagefault: Read from user memory as supervisor" << endl;
                    *pagefault = 1;
                }
            } else if(vm_privilege == 0) {
                if(!(readdata & PTE_USER_MASK)) { // user bit not set
                    cout << "[" << simulation_time << "][PTW] Pagefault: Read from supervisor memory as user" << endl;
                    *pagefault = 1;
                }
            }
        }
        if(!(*pagefault || *accessfault)) {
            paddr_to_location(*paddr, location, &location_missing);
            *accessfault = location_missing;
        }
        

    }
    
    cout << "[" << simulation_time << "][PTW] after resolution" 
            << " paddr = 0x" << *paddr
            << ", location = " << *location
            << ", accessfault = " << int(*accessfault)
            << ", pagefault = " << int(*pagefault) << endl;
            
}

void calculate_cache_response() {
    expected_response resp;
    uint8_t pagefault, accessfault;
    AXI_ADDR_TYPE paddr;
    uint32_t location;
    uint8_t inword_offset = TOP->req_address & 0b11;
    uint32_t shifted_word;
    resp.check_read_data = 0;

    check(TOP->req_valid, "calculate_cache_response called without request");
    if((TOP->req_cmd == CACHE_CMD_LOAD) || (TOP->req_cmd == CACHE_CMD_EXECUTE)) {
        virtual_resolve(TOP->req_cmd, &paddr, &location, &pagefault, &accessfault);
        if(pagefault) {
            resp.status = CACHE_RESPONSE_PAGEFAULT;
        } else if(accessfault) {
            resp.status = CACHE_RESPONSE_ACCESSFAULT;
        } else {
            resp.check_read_data = 1;
            resp.status = CACHE_RESPONSE_SUCCESS;
            resp.read_data = expected_load_data[location];
        }
    } else if(TOP->req_cmd == CACHE_CMD_STORE) {
        virtual_resolve(TOP->req_cmd, &paddr, &location, &pagefault, &accessfault);
        if(pagefault) {
            resp.status = CACHE_RESPONSE_PAGEFAULT;
        } else if(accessfault) {
            resp.status = CACHE_RESPONSE_ACCESSFAULT;
        } else {
            resp.status = CACHE_RESPONSE_SUCCESS;
            uint8_t wstrb = TOP->req_write_mask;
            uint32_t wdata = TOP->req_write_data;
            expected_load_data[location] = (
                    ((wstrb & 0b0001) ? (wdata & 0xFF)       : (expected_load_data[location] & 0xFF)) | 
                    ((wstrb & 0b0010) ? (wdata & 0xFF00)     : (expected_load_data[location] & 0xFF00)) | 
                    ((wstrb & 0b0100) ? (wdata & 0xFF0000)   : (expected_load_data[location] & 0xFF0000)) | 
                    ((wstrb & 0b1000) ? (wdata & 0xFF000000) : (expected_load_data[location] & 0xFF000000))
                );
        }
    } else if(TOP->req_cmd == CACHE_CMD_FLUSH_ALL) {
        resp.check_read_data = 0;
        resp.status = CACHE_RESPONSE_SUCCESS;
    } else {
        check(0, "Unimplemented check, please implement it");
    }

    cout << "[" << simulation_time << "][calculate_cache_response] Pushin resp " << to_string(resp) << endl;
    expected_response_queue->push(resp);

}

void cache_operation(uint8_t op, uint32_t addr, uint8_t size = 0, uint32_t wdata = 0, uint32_t wstrb = 0xF) {
    TOP->req_valid = 1;
    TOP->req_cmd = op;
    TOP->req_address = rand();
    TOP->req_size = size;
    TOP->req_write_mask = rand() & 0b11;
    TOP->req_write_data = rand();
    if((op == CACHE_CMD_LOAD) || (op == CACHE_CMD_EXECUTE)) {
        TOP->req_address = addr;
        cout << "[" << simulation_time << "][cache_operation] load/execute op = " << int(op) << " addr = " << TOP->req_address << " size = " <<  int(size) << endl;
    } else if(op == CACHE_CMD_FLUSH_ALL) {
        cout << "[" << simulation_time << "][cache_operation] flush_all" << endl;
    } else if(op == CACHE_CMD_STORE) {
        TOP->req_address = addr;
        TOP->req_write_mask = wstrb;
        TOP->req_write_data = wdata;
        cout << "[" << simulation_time << "][cache_operation] store " << " addr = " << TOP->req_address << " size = " << int(size) << " write_mask = " << TOP->req_write_mask << " write_data = " << TOP->req_write_data << endl;
    } else {
        check(0, "TODO: Unimplemented cache operation");
    }
    calculate_cache_response();
    TOP->eval();
    int timeout = 0;

    while((!(TOP->req_ready)) && timeout < 100) {
        timeout++;
        cache_cycle();
        //cout << "[" << simulation_time << "]" << "one cycle inside while ready = " << int(TOP->req_ready) << endl;
    }
    //cout << "[" << simulation_time << "]" << "Outside while ready = " << int(TOP->req_ready) << endl;
    cache_cycle();

    TOP->req_valid = 0;
    TOP->eval();
}

void cache_configure(
    uint8_t satp_mode = 0,
    uint32_t satp_ppn = 0,
    uint8_t mcurpriv = 0b11,
    uint8_t mprv = 0,
    uint8_t mxr = 0,
    uint8_t sum = 0,
    uint8_t mpp = 0) {
    TOP->req_csr_satp_mode_in = satp_mode;
    TOP->req_csr_satp_ppn_in = satp_ppn;
    TOP->req_csr_mcurrent_privilege_in = mcurpriv;
    TOP->req_csr_mstatus_mprv_in = mprv;
    TOP->req_csr_mstatus_mxr_in = mxr;
    TOP->req_csr_mstatus_sum_in = sum;
    TOP->req_csr_mstatus_mpp_in = mpp;
}

void cache_wait_for_all_responses() {
    int timeout = 0;
    while((!expected_response_queue->empty()) && timeout < 100) {
        timeout++;
        cache_cycle();
    }
    check(timeout < 100, "Waiting for all response timeout");

    cout << "[" << simulation_time << "]" << "[Cache all response wait]: All responses done" << endl;

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
    cache_configure();
    cache_cycle();
    TOP->rst_n = 1;
    
    cache_configure();


    // Bit select test

    uint32_t num = 0b1101100101;
    assert(std::bitset<32>(bit_select(num, 1, 0)) == 0b01);
    assert(std::bitset<32>(bit_select(num, 2, 0)) == 0b101);
    assert(std::bitset<32>(bit_select(num, 5, 3)) == 0b100);

    
    
    write_to_location(0, 0xAABBCCDD); // Just some test value
    write_to_location(DEPTH_WORDS, 0xBBCCDDEE);
    write_to_location(DEPTH_WORDS + 1, 0xEEFFEEFF);

    uint32_t sizes[] = {0, 1, 2}; // 1, 2, 4 bytes
    for(auto size : sizes) {
        uint32_t incr = (1 << size);
        start_test("Cache: Read (size = " + to_string(size) + ")from uncached");
        for(int i = 0; i < 8; i += incr) {
            cache_operation(CACHE_CMD_LOAD, i, size);
        }

        start_test("Cache: Byte Read (size = " + to_string(size) + ") from cached");
        cache_operation(CACHE_CMD_FLUSH_ALL, 0, 0);
        for(int i = 0; i < 8; i += incr) {
            cache_operation(CACHE_CMD_LOAD, (1 << 31) + i, size);
            cache_operation(CACHE_CMD_FLUSH_ALL, 0, 0);

            cache_operation(CACHE_CMD_LOAD, (1 << 31) + i, size);
        }
    }
    start_test("Cache: flushing all responses");
    cache_wait_for_all_responses();
    for(auto size : sizes) {
        uint32_t incr = (1 << size);
        start_test("Cache: write (size = " + to_string(size) + ") from uncached");
        for(int i = 0; i < 8; i += incr) {
            cache_operation(CACHE_CMD_STORE, i, size, 0xFF00FF00);
        }

        start_test("Cache: Write (size = " + to_string(size) + ") from cached");
        cache_operation(CACHE_CMD_FLUSH_ALL, 0, 0);
        for(int i = 0; i < 8; i += incr) {
            cache_operation(CACHE_CMD_STORE, (1 << 31) + i, size, 0x00FF00FF);
            cache_operation(CACHE_CMD_FLUSH_ALL, 0, 0);

            cache_operation(CACHE_CMD_STORE, (1 << 31) + i, size, 0xEEFFEEFF);
        }
    }
    start_test("Cache: flushing all responses");
    cache_wait_for_all_responses();
    for(auto size : sizes) {
        uint32_t incr = (1 << size);
        start_test("Cache: Read (size = " + to_string(size) + ") from uncached");
        for(int i = 0; i < 8; i += incr) {
            cache_operation(CACHE_CMD_LOAD, i, size);
        }

        start_test("Cache: Byte Read (size = " + to_string(size) + ") from cached");
        cache_operation(CACHE_CMD_FLUSH_ALL, 0, 0);
        for(int i = 0; i < 8; i += incr) {
            cache_operation(CACHE_CMD_LOAD, (1 << 31) + i, size);
            cache_operation(CACHE_CMD_FLUSH_ALL, 0, 0);

            cache_operation(CACHE_CMD_LOAD, (1 << 31) + i, size);
        }
    }

    cache_wait_for_all_responses();





    // Set BRAM0 tree:
    //  0  -> Megapage invalid PTE
    //  1  -> Megapage readable, dirty, access
    //  2  -> Megapage writable, readable, dirty, access
    //  3  -> Megapage Readable, writable, executable, dirty
    //  4  -> Megapage Readable, writable, executable, access
    //  5  -> Megapage executable only
    //  6  -> Megapage all set, USER
    //  7  -> 4K Page towards Accessfault
    //  8  -> Missaligned
    //  9  -> the base of 3 level deep leaf @ second tree location -> pagefault
    //  10 -> Ponter to leaf @ third tree location
    
    start_test("Cache: Virtual Memory tests");
    write_to_location(0, (1 << 10) | 0);
    write_to_location(1, (1 << 20) | PTE_VALID_MASK | PTE_READ_MASK | PTE_DIRTY_MASK | PTE_ACCESS_MASK);
    write_to_location(2, (1 << 20) | PTE_VALID_MASK | PTE_WRITE_MASK | PTE_READ_MASK | PTE_EXECUTE_MASK | PTE_DIRTY_MASK | PTE_ACCESS_MASK);
    write_to_location(3, (1 << 20) | PTE_VALID_MASK | PTE_WRITE_MASK | PTE_READ_MASK | PTE_EXECUTE_MASK | PTE_DIRTY_MASK);
    write_to_location(4, (1 << 20) | PTE_VALID_MASK | PTE_WRITE_MASK | PTE_READ_MASK | PTE_EXECUTE_MASK | PTE_ACCESS_MASK);
    write_to_location(5, (1 << 20) | PTE_VALID_MASK | PTE_EXECUTE_MASK | PTE_ACCESS_MASK);
    write_to_location(6, (1 << 20) | PTE_VALID_MASK | PTE_READ_MASK | PTE_WRITE_MASK | PTE_EXECUTE_MASK | PTE_USER_MASK | PTE_DIRTY_MASK | PTE_ACCESS_MASK);
    write_to_location(7, (100 << 20) | PTE_VALID_MASK);
    write_to_location(8, (1 << 10) | PTE_ALL); // missaligned megapage
    write_to_location(9, (1 << 10) | PTE_POINTER); // Pointer to sub tree which first element is 3 level deep
    write_to_location(10, (1 << 10) | PTE_POINTER); // Pointer to subtree
    
    cache_operation(CACHE_CMD_FLUSH_ALL, 0, 0);

    cache_configure(
        1, // satp_mode
        0, // satp_ppn
        SUPERVISOR // priv = supervisor
    );

    auto ops = {CACHE_CMD_LOAD, CACHE_CMD_EXECUTE, CACHE_CMD_STORE};

    for(auto priv : {SUPERVISOR, USER}) {
        for(auto op : ops) {
            for(int i = 0; i < 9; i++) {
                cache_configure(
                    1, // satp_mode
                    0, // satp_ppn
                    priv // priv
                );
                cache_operation(op, i << 22, WORD);
            }
        }
    }


    



    start_test("Cache: flushing all responses");
    cache_wait_for_all_responses();
    cache_cycle();
    cache_cycle();
    cache_cycle();
    cache_cycle();

#include <verilator_template_footer.cpp>
