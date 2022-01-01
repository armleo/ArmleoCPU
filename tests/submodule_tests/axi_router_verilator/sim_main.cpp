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


#include <Varmleosoc_axi_router.h>
#define TRACE
#define TOP_MODULE_DECLARATION Varmleosoc_axi_router * armleosoc_axi_router;
#define TOP_ALLOCATION armleosoc_axi_router = new Varmleosoc_axi_router;
#include "verilator_template_header.cpp"

#include "random_utils.cpp"

#include <map>
#include <bitset>

uint32_t map_client_num_to_addr(uint32_t client_num) {
    switch(client_num) {
        case 0b00: return 0x1000;
        case 0b01: return 0x2000;
        case 0b10: return 0x3000;
        case 0b11: return 0x4000;
        default:
            return (client_num+1) << 12;
    }
}


bool is_mapped_client_num(uint32_t client_num) {
    return client_num <= 0b11;
}

uint32_t rand_client_num() {return rand2();}


std::map<std::string, uint32_t> generate_random_access(uint32_t client_num) {
    std::map<std::string, uint32_t> a;
    
    a["addr"] = (rand12() & ~(0b11)) | (map_client_num_to_addr(client_num));
    a["len"] = rand8();
    a["id"] = rand2();
    a["size"] = rand3();
    a["burst"] = rand2();
    a["lock"] = rand1();
    a["prot"] = rand3();

    return a;
}


std::map<std::string, uint32_t> generate_random_r() {
    std::map<std::string, uint32_t> r;
    
    r["resp"] = rand2();
    r["data"] = rand();
    r["last"] = rand1();
    r["id"] = rand2();

    return r;
}


//-------------------------------------
//
// Upstream AR
//
//-------------------------------------


void poke_upstream_ar(uint32_t upstream_axi_arvalid, std::map<std::string, uint32_t> value_map) {
    armleosoc_axi_router->upstream_axi_arvalid = upstream_axi_arvalid;
    if(!upstream_axi_arvalid) {
        value_map = generate_random_access(rand_client_num());
    }
    armleosoc_axi_router->upstream_axi_araddr = value_map["addr"];
    armleosoc_axi_router->upstream_axi_arlen = value_map["len"];
    armleosoc_axi_router->upstream_axi_arsize = value_map["size"];
    armleosoc_axi_router->upstream_axi_arburst = value_map["burst"];
    armleosoc_axi_router->upstream_axi_arid = value_map["id"];
    armleosoc_axi_router->upstream_axi_arlock = value_map["lock"];
    armleosoc_axi_router->upstream_axi_arprot = value_map["prot"];
    armleosoc_axi_router->eval();
}

void expect_upstream_ar(uint32_t upstream_axi_arready) {
    check(armleosoc_axi_router->upstream_axi_arready == upstream_axi_arready, "upstream ARREADY does not match");
}

//-------------------------------------
//
// Upstream R
//
//-------------------------------------

void poke_upstream_r(uint32_t upstream_axi_rready) {
    armleosoc_axi_router->upstream_axi_rready = upstream_axi_rready;
    armleosoc_axi_router->eval();
}

void expect_upstream_r(uint32_t upstream_axi_rvalid, std::map<std::string, uint32_t> value_map) {
    check(armleosoc_axi_router->upstream_axi_rvalid == upstream_axi_rvalid, "upstream RVALID does not match");
    if(upstream_axi_rvalid) {
        if(value_map["resp"] == 0b00 || value_map["resp"] == 0b01) {
            check(armleosoc_axi_router->upstream_axi_rdata == value_map["data"], "upstream DATA does not match");
        }
        check(armleosoc_axi_router->upstream_axi_rresp == value_map["resp"], "upstream RESP does not match");
        check(armleosoc_axi_router->upstream_axi_rlast == value_map["last"], "upstream LAST does not match");
        check(armleosoc_axi_router->upstream_axi_rid == value_map["id"], "upstream RID does not match");
    }
}


//-------------------------------------
//
// Downstream AR
//
//-------------------------------------

void poke_downstream_ar(uint32_t client_num, uint32_t arready) {
    // ARREADY can be asserted only for client_num, others are zero
    armleosoc_axi_router->downstream_axi_arready = arready << client_num;
    armleosoc_axi_router->eval();
}

void expect_downstream_ar(uint32_t client_num, uint32_t arvalid, std::map<std::string, uint32_t> value_map) {
    check((!!(armleosoc_axi_router->downstream_axi_arvalid & (arvalid << client_num)) == arvalid), "ARVALID does not match the expected value");
    if(arvalid) {
        check(armleosoc_axi_router->downstream_axi_araddr == value_map["addr"], "ARADDR does not match");
        check(armleosoc_axi_router->downstream_axi_arlen == value_map["len"], "ARLEN does not match");
        check(armleosoc_axi_router->downstream_axi_arsize == value_map["size"], "ARSIZE does not match");
        check(armleosoc_axi_router->downstream_axi_arburst == value_map["burst"], "ARBURST does not match");
        check(armleosoc_axi_router->downstream_axi_arlock == value_map["lock"], "ARLOCK does not match");
        check(armleosoc_axi_router->downstream_axi_arprot == value_map["prot"], "ARPROT does not march");
        check(armleosoc_axi_router->downstream_axi_arid == value_map["id"], "ARID does not match");
    }
}

//-------------------------------------
//
// Downstream R
//
//-------------------------------------

void poke_downstream_r(uint32_t cn, uint32_t rvalid, std::map<std::string, uint32_t> r_map) {
    armleosoc_axi_router->downstream_axi_rvalid = rvalid;

    if(!rvalid) {
        r_map = generate_random_r();
    }

    armleosoc_axi_router->downstream_axi_rresp = (r_map["resp"]) << (cn * 2);
    armleosoc_axi_router->downstream_axi_rid = r_map["id"] << (cn * 2);
    armleosoc_axi_router->downstream_axi_rlast = r_map["last"] << cn;
    armleosoc_axi_router->downstream_axi_rdata[cn] = r_map["data"];
    armleosoc_axi_router->eval();
}

void expect_downstream_r(uint32_t cn, uint32_t rready) {
    check((!!(armleosoc_axi_router->downstream_axi_rready & cn)) == rready, "RREADY does not match");
}



std::map<std::string, uint32_t> empty_a;
std::map<std::string, uint32_t> empty_r;

//-------------------------------------
//
// Write tests
//
//-------------------------------------

void write_test(uint32_t client_num, std::bitset<256> write_stall) {
    
}



//-------------------------------------
//
// Read tests
//
//-------------------------------------

void test_read_out_of_range(uint32_t client_num, bool stall_r, std::bitset<256> read_stall) {
    std::map<std::string, uint32_t> ar = generate_random_access(client_num);
    std::map<std::string, uint32_t> decerr;

    //-------------------------------------
    //
    // AR request sent, maybe accepted
    //
    //-------------------------------------
    poke_upstream_ar(/*arvalid=*/1, /*values=*/ar);
    poke_upstream_r(/*ready=*/0);
    poke_downstream_ar(/*client_num=*/0,/*arready=*/0);
    poke_downstream_r(/*client_num=*/0,/*valid=*/0, empty_r);
    armleosoc_axi_router->eval();
    cout << "Sent read command on AR, expecting dec err" << endl;
    expect_upstream_ar(/*ready=*/1);
    expect_upstream_r(/*rvalid=*/0, empty_r);
    expect_downstream_ar(/*client_num=*/0,/*arvalid=*/0, empty_a);
    expect_downstream_r(/*client_num=*/0,/*rready=*/0);
    next_cycle();

    cout << "Accepting DECERR responses" << endl;
    for(int i = 0; i <= ar["len"]; i++) {
        decerr["resp"] = 0b11;
        decerr["id"] = ar["id"];
        decerr["last"] = i == ar["len"];

        if(read_stall[i]) {cout << "Stalled one response i: " << i << endl;}
        else {cout << "Accepted response without stall i: " << i << endl;}

        expect_upstream_ar(/*ready=*/0);
        expect_upstream_r(/*rvalid=*/1, /*values=*/decerr);
        poke_upstream_r(!read_stall[i]);
        
        if(read_stall[i]) {
            read_stall[i] = 0;
            i--;
        }
        expect_downstream_ar(/*client_num=*/0,/*arvalid=*/0, empty_a);
        expect_downstream_r(/*client_num=*/0,/*rready=*/0);
        next_cycle();
    }
    
}


void read_test(uint32_t client_num, bool stall_ar, std::bitset<256> delayed_resp, std::bitset<256> read_stall) {
    // std::map<std::string, uint32_t> ar = generate_random_access(client_num);
    // std::map<std::string, uint32_t> downstream_ar = ar;
        
    // std::map<std::string, uint32_t> decerr;
    // std::map<std::string, uint32_t> expected_r = generate_random_r();


    // decerr["resp"] = 0b11;
    // expected_r["id"] = decerr["id"] = ar["id"];
    // expected_r["last"] = decerr["last"] = 0;

    // cout << "Starting read test cn: " << client_num << endl;
    // bool expect_decerr = !is_mapped_client_num(client_num);

    // downstream_ar["addr"] = downstream_ar["addr"] - map_client_num_to_addr(client_num);
    // cout << "Converted upstream AR: " << hex << ar["addr"] << " to downstream AR: " << downstream_ar["addr"]  << endl << dec;
    

    // //-------------------------------------
    // //
    // // AR request sent, maybe accepted
    // //
    // //-------------------------------------
    // poke_upstream_ar(/*arvalid=*/1, /*values=*/ar);
    // poke_upstream_r(/*ready=*/0);
    // poke_downstream_ar(/*client_num=*/0,/*arready=*/0);
    // poke_downstream_r(/*client_num=*/0,/*valid=*/0, empty_r);
    // armleosoc_axi_router->eval();
    // cout << "Sent read command on AR, expecting dec err: " << expect_decerr << endl;
    // if(expect_decerr) {
    //     // Expect upstream to be accepted, but then DECERR returned
    //     expect_upstream_ar(/*ready=*/1);
    // } else {
    //     expect_upstream_ar(/*ready=*/0);
    // }
    // expect_upstream_r(/*rvalid=*/0, empty_r);
    // expect_downstream_ar(/*client_num=*/0,/*arvalid=*/0, empty_a);
    // expect_downstream_r(/*client_num=*/0,/*rready=*/0);
    // next_cycle();



    // //-------------------------------------
    // //
    // // AR request has been sent,
    // // now if decerr is expected then do nothing,
    // // because AR has been accepted
    // // Otherwise if decerr is not expected, then accept the downstream AR (with stall)
    // // 
    // //
    // //-------------------------------------


    // if(!expect_decerr) {
    //     cout << "Accepting AR request on downstream" << endl;
    //     // Only for non decerr, stall if required then accept the AR request
    //     if(!stall_ar) {
    //         poke_downstream_ar(/*client_num=*/client_num,/*arready=*/1);
    //         expect_upstream_ar(/*ready=*/1);
    //     } else {
    //         poke_downstream_ar(/*client_num=*/client_num,/*arready=*/0);
    //         expect_upstream_ar(/*ready=*/0);
    //     }
        
    //     expect_upstream_r(/*rvalid=*/0, empty_r);
    //     expect_downstream_ar(/*client_num=*/client_num,/*arvalid=*/1, downstream_ar);
    //     expect_downstream_r(/*client_num=*/0,/*rready=*/0);
    //     next_cycle();
    // }


    // // TODO: Poke_downstream_r;
    // for(int i = 0; i <= ar["len"]; i++) {
    //     poke_upstream_ar(/*arvalid=*/0, /*values=*/empty_a);
    //     poke_downstream_r();

    //     if(read_stall[i]) {
    //         poke_upstream_r(/*ready=*/0);
    //         read_stall[i] = 0;
    //         i--;
    //     } else {
    //         poke_upstream_r(/*ready=*/1);
    //     };
    //     expect_upstream_ar(/*ready=*/0);

    //     expected_r["last"] = decerr["last"] = i == ar["len"];
    //     if(expect_decerr) {
    //         expect_upstream_r(/*rvalid=*/1, decerr);
    //         expect_downstream_ar(/*client_num=*/0,/*arvalid=*/0, empty_a);
    //         expect_downstream_r(/*client_num=*/0,/*rready=*/0);
    //     } else {
    //         expect_upstream_r(/*rvalid=*/1, );
    //         expect_downstream_ar(/*client_num=*/0,/*arvalid=*/0, empty_a);
    //         expect_downstream_r(/*client_num=*/0,/*rready=*/0);
    //     }
    //     next_cycle();
        

    //     // 
    // }


    
    // //assert_downstream_not_valid();
    // /*
    // poke_upstream_ar(1, ar);
    
    // for(uint8_t cn = 0; is_mapped_client_num(cn), ++cn) {
    //     poke_downstream_ar(cn, 0);
    //     poke_downstream_r(cn, 0, empty_r);
    // }
    // armleosoc_axi_router->eval();
    // expect_upstream_ar(0, empty_ar);
    // expect_upstream_r(0, empty_r);
    // for(uint8_t cn = 0; is_mapped_client_num(cn), ++cn) {
    //     expect_downstream_ar(cn, 0, 0);
    //     expect_downstream_r(cn, 0);
    // }*/

    
    // /*

    // assert_downstream_read(0, 1, 1, 1);
    // if(is_mapped_client_num(client_num)) {
    //     armleosoc_axi_router->downstream_axi_arready = 1 << (client_num * 2);
    //     armleosoc_axi_router->eval();

    //     check(armleosoc_axi_router->upstream_axi_arready == 0, "Expected AR to not be accepted in first cycle because not DECERR happened");
    //     check(armleosoc_axi_router->downstream_axi_arvalid == 1, "Expected ARVALID to be set");
        
        
    //     next_cycle();
    // } else {
    //     check(armleosoc_axi_router->upstream_axi_arready == 1, "Expected AR to be accepted, because DECERR has to be returned");
    // }*/
    // //poke_upstream_ar();
    // //next_cycle();

    
    
    // /*
    // for(uint32_t cnt = 0; cnt != arlen; ++cnt) {
        
    //     if(!is_mapped_client_num(client_num)) {
    //         // If this is not mapped then expect a read that has DECERR as response
    //     }

    //     next_cycle();
    // }*/
}


#include "verilator_template_main_start.cpp"
    empty_a = generate_random_access(0);
    empty_r = generate_random_r();
    utils_init();
    
    TOP->rst_n = 0;
    poke_upstream_ar(/*arvalid=*/0, /*values=*/empty_a);
    poke_upstream_r(/*ready=*/0);
    next_cycle();
    TOP->rst_n = 1;
    
    test_read_out_of_range(4, 0, 0b1010);
    test_read_out_of_range(4, 1, 0b1010);
    test_read_out_of_range(4, 0, 0b0101);
    test_read_out_of_range(4, 1, 0b0101);

    //read_test(0, 0, 0, 0);
    /*read_test(0, 0, 0b1010, 0b1010);
    read_test(4, 0, 0b1010, 0b1010);
    read_test(0, 1, 0b1010, 0b1010);
    read_test(4, 1, 0b1010, 0b1010);*/
    // TODO: Test simple read to region 0/1/2/3
    // TODO: Test simple read outside of regions
    // TODO: Test simple write to region 0/1/2/3
    // TODO: Test simple write outside of regions

#include <verilator_template_footer.cpp>
