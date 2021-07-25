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

#include <Varmleocpu_csr.h>
#define TRACE
#define TOP_MODULE_DECLARATION Varmleocpu_csr * armleocpu_csr;
#define TOP_ALLOCATION armleocpu_csr = new Varmleocpu_csr;
#include "verilator_template_header.cpp"


const int ARMLEOCPU_CSR_CMD_NONE = (0);
const int ARMLEOCPU_CSR_CMD_READ = (1);
const int ARMLEOCPU_CSR_CMD_WRITE = (2);
const int ARMLEOCPU_CSR_CMD_READ_WRITE = (3);
const int ARMLEOCPU_CSR_CMD_READ_SET = (4);
const int ARMLEOCPU_CSR_CMD_READ_CLEAR = (5);
const int ARMLEOCPU_CSR_CMD_MRET = (6);
const int ARMLEOCPU_CSR_CMD_SRET = (7);
const int ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN = (8);

const int MACHINE = 3;
const int SUPERVISOR = 1;
const int USER = 0;

void check_not_invalid() {
    check(armleocpu_csr->csr_invalid == 0, "Unexpected: Invalid access");
}


void csr_read(uint32_t address) {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_READ;
    armleocpu_csr->csr_address = address;
    armleocpu_csr->eval();
    check_not_invalid();
}

void csr_write_nocheck(uint32_t address, uint32_t data) {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_WRITE;
    armleocpu_csr->csr_address = address;
    armleocpu_csr->csr_from_rs = data;
    armleocpu_csr->eval();
}


void csr_write(uint32_t address, uint32_t data) {
    csr_write_nocheck(address, data);
    check_not_invalid();
}

void csr_read_check(uint32_t val) {
    armleocpu_csr->eval();
    if(armleocpu_csr->csr_to_rd != val)
        cout << "Unexpected csr_to_rd for address: 0x" << hex << armleocpu_csr->csr_address
        << ", value is 0x" << armleocpu_csr->csr_to_rd
        << ", expected: 0x" << val << endl << dec;
    check(armleocpu_csr->csr_to_rd == val, "Unexpected readdata value");
}

void test_mro(uint32_t address, uint32_t expected_value) {
    csr_read(address);
    csr_read_check(expected_value);
    next_cycle();

    csr_write_nocheck(address, 0xDEADBEEF);
    check(armleocpu_csr->csr_invalid == 1, "MRO: Failed check invalid == 1");
    //check();
    next_cycle();


    csr_read(address);
    csr_read_check(expected_value);
    next_cycle();
}

void csr_none() {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_NONE;
    armleocpu_csr->eval();
    //check_not_invalid();
}

void test_scratch(uint32_t address) {
    cout << "Testbench: test_scratch: Writing all ones to scratch" << endl;
    csr_write(address, 0xFFFFFFFF);
    next_cycle();

    cout << "Testbench: test_scratch: reading all ones" << endl;
    csr_read(address);
    
    csr_read_check(0xFFFFFFFF);
    next_cycle();

    cout << "Testbench: test_scratch: writing zero" << endl;
    csr_write(address, 0);
    next_cycle();

    cout << "Testbench: test_scratch: Reading all zero" << endl;
    csr_read(address);
    csr_read_check(0);
    next_cycle();

    csr_none();
    next_cycle();

    cout << "Testbench: test_scratch: Reading after dummy cycle" << endl;
    csr_read(address);
    csr_read_check(0);
    next_cycle();


    // TODO: Add writing with dummy cycle
    csr_none();
}


void force_to_machine() {
    armleocpu_csr->irq_meip_i = 1;
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN;
    next_cycle();

    csr_none();

    check(armleocpu_csr->csr_mcurrent_privilege == MACHINE, "GOTOPRIVILEGE: Unexpected target privilege");
}

void from_machine_go_to_privilege(uint32_t target_privilege) {
    csr_write(0xFC0, target_privilege);
    next_cycle();

    csr_none();
    check(armleocpu_csr->csr_mcurrent_privilege == target_privilege, "GOTOPRIVILEGE: Unexpected target privilege");
}
/*
// Does not do dummy cycle
void interrupt_test(uint32_t from_privilege, uint32_t mstatus, uint32_t mideleg, uint32_t mie,
        bool irq_exti_i, bool irq_swi_i, bool irq_timer_i,
        uint32_t int_cause, uint32_t expected_privilege) {
    armleocpu_csr->irq_exti_i = 0;
    armleocpu_csr->irq_timer_i = 0;
    armleocpu_csr->irq_swi_i = 0;

    force_to_machine();

    csr_write(0x300, mstatus);
    next_cycle();

    csr_write(0x303, mideleg);
    next_cycle();

    csr_write(0x304, mie);
    next_cycle();

    from_machine_go_to_privilege(from_privilege);

    if(from_privilege == MACHINE) {
        csr_write(0x300, mstatus);
        next_cycle();
    }

    armleocpu_csr->irq_exti_i = irq_exti_i;
    armleocpu_csr->irq_timer_i = irq_timer_i;
    armleocpu_csr->irq_swi_i = irq_swi_i;
    next_cycle();



    csr_none();
    check(armleocpu_csr->interrupt_pending_csr == 1, "interrupt_pending_csr wrong");
    if(armleocpu_csr->interrupt_cause != int_cause)
        cout << armleocpu_csr->interrupt_cause << " != " << int_cause << endl;
    check(armleocpu_csr->interrupt_cause == int_cause, "wrong int cause");
    check(armleocpu_csr->interrupt_target_privilege == expected_privilege, "target privilege is not expected");
    if(expected_privilege == MACHINE)
        check(armleocpu_csr->interrupt_target_pc == armleocpu_csr->csr_mtvec, "Unexpected: interrupt_target_pc");
    else if(expected_privilege == SUPERVISOR)
        check(armleocpu_csr->interrupt_target_pc == armleocpu_csr->csr_stvec, "Unexpected: interrupt_target_pc");
    else
        throw "Unexpected 'expected_privilege' value";
    
}*/

#include "verilator_template_main_start.cpp"
    
    cout << "Fetch Test started" << endl;
    TOP->clk = 0;
    TOP->rst_n = 0;
    TOP->csr_cmd = ARMLEOCPU_CSR_CMD_NONE;
    TOP->instret_incr = 0;


    TOP->irq_mtip_i = 0;
    TOP->irq_meip_i = 0;
    TOP->irq_seip_i = 0;
    TOP->irq_msip_i = 0;
    TOP->irq_ssip_i = 0;

    check(0, "Test error");

    csr_none();
    next_cycle();

    TOP->rst_n = 1;
    //force_to_machine();
    next_cycle();

    start_test("MSCRATCH");
    test_scratch(0x340);

    start_test("MVENDORID");
    test_mro(0xF11, 0x0A1AA1E0);

    start_test("MARCHID");
    test_mro(0xF12, 1);

    start_test("MIMPID");
    test_mro(0xF13, 1);

    start_test("MHARTID");
    test_mro(0xF14, 0);

    start_test("MTVEC");

    csr_write(0x305, 0xFFFFFFFC);
    next_cycle();
    
    csr_read(0x305);
    csr_read_check(0xFFFFFFFC);
    next_cycle();

    csr_write(0x305, 0xFFFFFFFF);
    next_cycle();
    
    csr_read(0x305);
    csr_read_check(0xFFFFFFFC);
    next_cycle();

    csr_write(0x305, 0x0);
    next_cycle();

    csr_read(0x305);
    csr_read_check(0x0);
    next_cycle();


    start_test("MSTATUS");
    csr_read(0x300);
    csr_read_check(0x0);
    next_cycle();


    uint32_t val = 
        (1 << 22) |
        (1 << 21) |
        (1 << 20) |
        (1 << 19) |
        (1 << 18) |
        (1 << 17);
    csr_write(0x300, val);
    next_cycle();
    csr_read(0x300);
    csr_read_check(val);
    
    check(TOP->csr_mstatus_tsr == 1, "Unexpected tsr");
    check(TOP->csr_mstatus_tw == 1, "Unexpected tw");
    check(TOP->csr_mstatus_tvm == 1, "Unexpected tvm");
    
    check(TOP->csr_mstatus_mprv == 1, "Unexpected mprv");
    check(TOP->csr_mstatus_mxr == 1, "Unexpected mprv");
    check(TOP->csr_mstatus_sum == 1, "Unexpected mprv");
    next_cycle();

    start_test("MISA");
    csr_read(0x301);
    csr_read_check(0b01000000000101000001000100000001);
    next_cycle();
    
    start_test("MISA: all one write, should not change MISA's value");
    csr_write(0x301, 0xFFFFFFFF);
    
    next_cycle();
    csr_read(0x301);
    csr_read_check(0b01000000000101000001000100000001);
    next_cycle();

    start_test("MISA: all zero write, should not change MISA's value");
    csr_write(0x301, 0);
    next_cycle();

    csr_read(0x301);
    csr_read_check(0b01000000000101000001000100000001);
    next_cycle();


    start_test("SSCRATCH");
    test_scratch(0x140);

    start_test("SEPC");
    
    csr_write(0x141, 0b11);
    next_cycle();

    csr_read(0x141);
    csr_read_check(0);
    next_cycle();

    csr_write(0x141, 0b100);
    next_cycle();

    csr_read(0x141);
    csr_read_check(0b100);
    next_cycle();

    start_test("MEPC");
    
    csr_write(0x341, 0b11);
    next_cycle();

    csr_read(0x341);
    csr_read_check(0);
    next_cycle();

    csr_write(0x341, 0b100);
    next_cycle();

    csr_read(0x341);
    csr_read_check(0b100);
    next_cycle();


    start_test("STVEC");

    csr_write(0x105, 0xFFFFFFFC);
    next_cycle();
    
    csr_read(0x105);
    csr_read_check(0xFFFFFFFC);
    next_cycle();

    csr_write(0x105, 0xFFFFFFFF);
    next_cycle();
    
    csr_read(0x105);
    csr_read_check(0xFFFFFFFC);
    next_cycle();


    start_test("SCAUSE");
    test_scratch(0x142);

    start_test("MCAUSE");
    test_scratch(0x342);

    start_test("MTVAL");
    test_scratch(0x343);

    start_test("STVAL");
    test_scratch(0x143);

    
    csr_read(0xB00);
    uint32_t begin_value = TOP->csr_to_rd;
    start_test("MCYCLE: Start time = " + begin_value);
    next_cycle();
    
    csr_read(0xB00);
    csr_read_check(begin_value + 1);
    next_cycle();

    csr_write(0xB80, 1);
    next_cycle();


    csr_write(0xB00, -1);
    next_cycle();
    
    csr_none();
    next_cycle();

    csr_read(0xB00);
    csr_read_check(0);
    next_cycle();

    csr_read(0xB80);
    csr_read_check(2);
    next_cycle();

    start_test("INSTRET");
    
    TOP->instret_incr = 1;
    csr_read(0xB02);
    csr_read_check(0);
    next_cycle();


    csr_read(0xB02);
    csr_read_check(1);
    next_cycle();

    csr_write(0xB82, 1);
    next_cycle();

    csr_write(0xB02, -1);
    next_cycle();

    csr_none();
    next_cycle();

    csr_read(0xB82);
    csr_read_check(2);
    next_cycle();

    csr_read(0xB02);
    csr_read_check(1);
    next_cycle();

    TOP->instret_incr = 0;
    csr_none();
    next_cycle();

    start_test("SATP");
    csr_write(0x180, 0x803FFFFF);
    check(TOP->csr_satp_mode == 0, "unexpected satp mode");
    check(TOP->csr_satp_ppn == 0, "unexpected satp ppn");
    next_cycle();

    csr_read(0x180);
    csr_read_check(0x803FFFFF);
    check(TOP->csr_satp_mode == 1, "unexpected satp mode");
    check(TOP->csr_satp_ppn == 0x3FFFFF, "unexpected satp ppn");
    next_cycle();

    // TODO: Fix this. This should be zero
    start_test("MEDELEG");
    csr_write(0x302, 0xFFFFFFFF);
    next_cycle();

    csr_read(0x302);
    csr_read_check(0);
    next_cycle();

    start_test("MIDELEG");
    csr_write(0x303, 0xFFFFFFFF);
    next_cycle();

    csr_read(0x303);
    csr_read_check(0);
    next_cycle();


    start_test("MIE");
    csr_write(0x304, 0xFFFF);
    next_cycle();

    csr_read(0x304);
    csr_read_check(0xAAA);
    next_cycle();

    csr_write(0x304, 0x0);
    next_cycle();

    csr_read(0x304);
    csr_read_check(0x0);
    next_cycle();


    start_test("SIE");
    csr_write(0x104, 0xFFFF);
    next_cycle();

    csr_read(0x104);
    csr_read_check(0x222);
    next_cycle();

    csr_write(0x104, 0x0);
    next_cycle();

    csr_read(0x104);
    csr_read_check(0x0);
    next_cycle();
    
    start_test("SSTATUS");
    csr_write(0x100, 0xFFFFFFFF);
    next_cycle();


    csr_read(0x100);
    csr_read_check(0x000C0122);
    next_cycle();
    

    csr_write(0x100, 0x0);
    next_cycle();

    csr_read(0x100);
    csr_read_check(0x0);
    next_cycle();
    

    start_test("MIP");




    csr_write(0x300, 0b1000); // mstatus.mie
    next_cycle();

    

//     #define TEST_MIP(irq_input_signal, bit_shift) \
//     csr_write(0x303, 0); /*mideleg*/ \
//     next_cycle(); \
//     \
//     csr_write(0x304, 1 << (bit_shift + MACHINE)); /*mie*/\
//     next_cycle(); \
//     \
//     irq_input_signal = 1; \
//     csr_none(); \
//     next_cycle(); \
//     \
//     csr_read(0x344); \
//     csr_read_check(1 << (bit_shift + MACHINE)); \
//     next_cycle(); \
//     \
//     csr_read(0x144); \
//     csr_read_check(0); \
//     next_cycle(); \
//     irq_input_signal = 0; \
//     csr_none(); \
//     next_cycle(); \
//     \
//     csr_write(0x303, 1 << (bit_shift + SUPERVISOR)); /*mideleg*/\
//     next_cycle(); \
//     \
//     csr_write(0x304, 1 << (bit_shift + SUPERVISOR)); /*sie*/\
//     next_cycle(); \
//     \
//     irq_input_signal = 1; \
//     csr_none(); \
//     next_cycle(); \
//  \
//     csr_read(0x344); \
//     csr_read_check((1 << (bit_shift + MACHINE)) | (1 << (bit_shift + SUPERVISOR))); \
//     next_cycle(); \
// \
//     csr_read(0x144); \
//     csr_read_check((1 << (bit_shift + SUPERVISOR))); \
//     next_cycle(); \
//  \
//     irq_input_signal = 0; \
//     csr_none(); \
//     next_cycle(); \

//     TEST_MIP(TOP->irq_exti_i, 8)
//     TEST_MIP(TOP->irq_timer_i, 4)
//     TEST_MIP(TOP->irq_swi_i, 0)
    
//     {   
//         uint32_t mie = 3;
//         uint32_t sie = 1;

//         uint32_t meie = 11;
//         uint32_t seie = 9;

//         uint32_t mtie = 7;
//         uint32_t stie = 5;

//         uint32_t msie = 3;
//         uint32_t ssie = 1;




//         std::vector<int> privlist = {MACHINE, SUPERVISOR, USER};
//         std::vector<int> supervisor_privlist = {SUPERVISOR, USER};

//         for(auto & priv : privlist) {
//             interrupt_test(priv,
//                 1 << mie, // mstatus.mie = 1
//                 0, // mideleg
//                 (1 << meie) | (1 << mtie) | (1 << msie), // mie
//                 1, // exti
//                 1, // swi
//                 1, // timeri
//                 (1 << 31) | (11), // cause
//                 MACHINE // EXPECTED PRIVILEGE
//             );
//         }
        
//         for(auto & priv : supervisor_privlist) {
//             interrupt_test(priv,
//                 1 << sie, // mstatus.mie = 0, mstatus.sie = 1
//                 (1 << seie) | (1 << stie) | (1 << ssie), // mideleg
//                 (1 << seie) | (1 << stie) | (1 << ssie), // mie
//                 1, // exti
//                 1, // swi
//                 1, // timeri
//                 (1 << 31) | (11), // cause
//                 SUPERVISOR // EXPECTED PRIVILEGE
//             );
//         }

//         // TODO: Test sie not set
//         // TODO: Test s*ie not set
//         // TODO: priority tests
//         // TODO: Test other interrupts

//         // TODO: Test exception
//         // TODO: ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN
//     }
//     std::vector<int> privlist = {MACHINE, SUPERVISOR, USER};
//     for(auto & priv : privlist) {
//         force_to_machine();
//         uint32_t mpp = priv;
//         uint32_t mpie = 1;
//         uint32_t mstatus = (mpp << 11) | (mpie << 7);
//         uint32_t mepc = 0xF000C400;
//         csr_write(0x300, mstatus);
//         next_cycle();


//         csr_write(0x341, mepc);
//         next_cycle();


//         TOP->csr_cmd = ARMLEOCPU_CSR_CMD_MRET;
//         TOP->eval();
//         check(TOP->csr_next_pc == mepc, "mret: csr_next_pc is not mepc");
//         next_cycle();
        
//         check(TOP->csr_mcurrent_privilege == mpp, "mret: incorrect privilege");
//         csr_none();
//     }

    // TODO: Test with rmw sequence
    // TODO: Test with privilege change the MIP's

    
    // TODO: Test READ_SET, READ_CLEAR for each register


    // TODO: Test machine registers for access from supervisor
    // TODO: Test supervisor interrupt handling
    // TODO: Test MRET
    // TODO: Test SRET
    // TODO: Test user accessing supervisor

    

    csr_none();
    next_cycle();
    
    //throw runtime_error("CSR Tests are done but incomplete, TODO: Add tests for all CSRs");
    cout << "CSR Tests done" << endl;

#include <verilator_template_footer.cpp>