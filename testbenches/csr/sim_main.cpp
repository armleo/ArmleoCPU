#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Varmleocpu_csr.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Varmleocpu_csr* armleocpu_csr;

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

uint32_t testnum;

using namespace std;

double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}
void dump_step() {
    simulation_time++;
    if(trace) m_trace->dump(simulation_time);
}
void update() {
    armleocpu_csr->eval();
    dump_step();
}

void posedge() {
    armleocpu_csr->clk = 1;
    update();
    update();
}

void till_user_update() {
    armleocpu_csr->clk = 0;
    update();
}
void after_user_update() {
    update();
}

void dummy_cycle() {
    after_user_update();

    posedge();
    till_user_update();
}

void check(bool match, string msg) {
    if(!match) {
        cout << "testnum: " << testnum << endl;
        cout << msg << endl;
        throw runtime_error(msg);
    }
}

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
    armleocpu_csr->csr_writedata = data;
    armleocpu_csr->eval();
}


void csr_write(uint32_t address, uint32_t data) {
    csr_write_nocheck(address, data);
    check_not_invalid();
}

void csr_read_check(uint32_t val) {
    armleocpu_csr->eval();
    if(armleocpu_csr->csr_readdata != val)
        cout << "Unexpected csr_readdata for address: 0x" << hex << armleocpu_csr->csr_address
        << ", value is 0x" << armleocpu_csr->csr_readdata
        << ", expected: 0x" << val << endl << dec;
    check(armleocpu_csr->csr_readdata == val, "Unexpected readdata value");
}

void test_mro(uint32_t address, uint32_t expected_value) {
    csr_read(address);
    csr_read_check(expected_value);
    dummy_cycle();

    csr_write_nocheck(address, 0xDEADBEEF);
    check(armleocpu_csr->csr_invalid == 1, "MRO: Failed check invalid == 1");
    //check();
    dummy_cycle();


    csr_read(address);
    csr_read_check(expected_value);
    dummy_cycle();
}

void csr_none() {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_NONE;
    armleocpu_csr->eval();
    check_not_invalid();
}

void test_scratch(uint32_t address) {
    csr_write(address, 0xFFFFFFFF);
    dummy_cycle();

    csr_read(address);
    
    csr_read_check(0xFFFFFFFF);
    dummy_cycle();

    csr_write(address, 0);
    dummy_cycle();

    csr_read(address);
    csr_read_check(0);
    dummy_cycle();

    csr_none();
    dummy_cycle();

    csr_read(address);
    csr_read_check(0);
    dummy_cycle();

    csr_none();
}
/*
void go_to_privilege(uint32_t target_privilege) {
    armleocpu_csr->csr_privilege = target_privilege;
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN;
    dummy_cycle();

    check(armleocpu_csr->mcurrent_privilege == target_privilege);


}
void interrupt_test(uint32_t from_privilege, uint32_t mstatus, uint32_t mideleg, uint32_t int_cause, uint32_t expected_privilege) {
    go_to_privilege(from_privilege);
    

    csr_write(, mstatus);
    
    dummy_cycle();
}
*/

int main(int argc, char** argv, char** env) {
    cout << "Fetch Test started" << endl;
    // This is a more complicated example, please also see the simpler examples/make_hello_c.

    // Prevent unused variable warnings
    if (0 && argc && argv && env) {}

    // Set debug level, 0 is off, 9 is highest presently used
    // May be overridden by commandArgs
    Verilated::debug(0);

    // Randomization reset policy
    // May be overridden by commandArgs
    Verilated::randReset(2);

    // Verilator must compute traced signals
    Verilated::traceEverOn(true);

    // Pass arguments so Verilated code can see them, e.g. $value$plusargs
    // This needs to be called before you create any model
    Verilated::commandArgs(argc, argv);

    // Create logs/ directory in case we have traces to put under it
    Verilated::mkdir("logs");

    // Construct the Verilated model, from Varmleocpu_csr.h generated from Verilating "armleocpu_csr.v"
    armleocpu_csr = new Varmleocpu_csr;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    armleocpu_csr->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");
    try {
    
    armleocpu_csr->rst_n = 0;
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_NONE;
    armleocpu_csr->instret_incr = 0;
    armleocpu_csr->irq_timer_i = 0;
    armleocpu_csr->irq_exti_i = 0;
    armleocpu_csr->irq_swi_i = 0;
    dummy_cycle();
    armleocpu_csr->rst_n = 1;
    dummy_cycle();

    testnum = 1;
    cout << "Testing MSCRATCH" << endl;
    test_scratch(0x340);

    testnum = 2;
    cout << "Testing MVENDORID" << endl;
    test_mro(0xF11, 0x0A1AA1E0);
    testnum = 3;
    cout << "Testing MARCHID" << endl;
    test_mro(0xF12, 1);
    testnum = 3;
    cout << "Testing MIMPID" << endl;
    test_mro(0xF13, 1);
    testnum = 4;
    cout << "Testing MHARTID" << endl;
    test_mro(0xF14, 0);

    testnum = 5;
    cout << "Testing MTVEC" << endl;

    csr_write(0x305, 0xFFFFFFFC);
    dummy_cycle();
    
    csr_read(0x305);
    csr_read_check(0xFFFFFFFC);
    dummy_cycle();

    csr_write(0x305, 0xFFFFFFFF);
    dummy_cycle();
    
    csr_read(0x305);
    csr_read_check(0xFFFFFFFC);
    dummy_cycle();

    testnum = 6;
    cout << "Testing MSTATUS" << endl;
    csr_read(0x300);
    csr_read_check(0x0);
    dummy_cycle();

    testnum = 7;
    uint32_t val = 
        (1 << 22) |
        (1 << 21) |
        (1 << 20) |
        (1 << 19) |
        (1 << 18) |
        (1 << 17);
    csr_write(0x300, val);
    dummy_cycle();
    csr_read(0x300);
    csr_read_check(val);
    
    check(armleocpu_csr->csr_mstatus_tsr == 1, "Unexpected tsr");
    check(armleocpu_csr->csr_mstatus_tw == 1, "Unexpected tw");
    check(armleocpu_csr->csr_mstatus_tvm == 1, "Unexpected tvm");
    
    check(armleocpu_csr->csr_mstatus_mprv == 1, "Unexpected mprv");
    check(armleocpu_csr->csr_mstatus_mxr == 1, "Unexpected mprv");
    check(armleocpu_csr->csr_mstatus_sum == 1, "Unexpected mprv");
    dummy_cycle();

    testnum = 8;
    cout << "Testing MISA" << endl;
    csr_read(0x301);
    csr_read_check(0b01000000000101000001000100000000);
    dummy_cycle();
    
    testnum = 9;
    csr_write(0x301, 0xFFFFFFFF);
    
    dummy_cycle();
    csr_read(0x301);
    csr_read_check(0b01000000000101000001000100000001);
    dummy_cycle();


    testnum = 10;
    cout << "Testing SSCRATCH" << endl;
    test_scratch(0x140);

    testnum = 11;
    cout << "Testing SEPC" << endl;
    
    csr_write(0x141, 0b11);
    dummy_cycle();

    csr_read(0x141);
    csr_read_check(0);
    dummy_cycle();

    csr_write(0x141, 0b100);
    dummy_cycle();

    csr_read(0x141);
    csr_read_check(0b100);
    dummy_cycle();


    testnum = 12;
    cout << "Testing MEPC" << endl;
    
    csr_write(0x341, 0b11);
    dummy_cycle();

    csr_read(0x341);
    csr_read_check(0);
    dummy_cycle();

    csr_write(0x341, 0b100);
    dummy_cycle();

    csr_read(0x341);
    csr_read_check(0b100);
    dummy_cycle();

    testnum = 13;
    cout << "Testing STVEC" << endl;

    csr_write(0x105, 0xFFFFFFFC);
    dummy_cycle();
    
    csr_read(0x105);
    csr_read_check(0xFFFFFFFC);
    dummy_cycle();

    csr_write(0x105, 0xFFFFFFFF);
    dummy_cycle();
    
    csr_read(0x105);
    csr_read_check(0xFFFFFFFC);
    dummy_cycle();


    testnum = 14;
    cout << "Testing SCAUSE" << endl;
    test_scratch(0x142);

    testnum = 15;
    cout << "Testing MCAUSE" << endl;
    test_scratch(0x342);

    testnum = 16;
    cout << "Testing MTVAL" << endl;
    test_scratch(0x343);

    testnum = 17;
    cout << "Testing STVAL" << endl;
    test_scratch(0x143);


    testnum = 18;
    csr_read(0xB00);
    uint32_t begin_value = armleocpu_csr->csr_readdata;
    cout << "Testing MCYCLE: Start time = " << begin_value << endl;
    dummy_cycle();
    
    testnum = 19;
    csr_read(0xB00);
    csr_read_check(begin_value + 1);
    dummy_cycle();

    testnum = 20;
    csr_write(0xB80, 1);
    dummy_cycle();

    testnum = 21;
    csr_write(0xB00, -1);
    dummy_cycle();
    
    csr_none();
    dummy_cycle();

    testnum = 22;
    csr_read(0xB00);
    csr_read_check(0);
    dummy_cycle();

    testnum = 23;
    csr_read(0xB80);
    csr_read_check(2);
    dummy_cycle();

    testnum = 24;
    cout << "Testing INSTRET" << endl;
    
    armleocpu_csr->instret_incr = 1;
    csr_read(0xB02);
    csr_read_check(0);
    dummy_cycle();


    testnum = 25;
    csr_read(0xB02);
    csr_read_check(1);
    dummy_cycle();

    testnum = 26;
    csr_write(0xB82, 1);
    dummy_cycle();

    testnum = 27;
    csr_write(0xB02, -1);
    dummy_cycle();

    csr_none();
    dummy_cycle();

    testnum = 28;
    csr_read(0xB82);
    csr_read_check(2);
    dummy_cycle();

    testnum = 29;
    csr_read(0xB02);
    csr_read_check(1);
    dummy_cycle();

    testnum = 30;
    armleocpu_csr->instret_incr = 0;
    csr_none();
    dummy_cycle();

    cout << "Testing SATP" << endl;
    testnum = 31;
    csr_write(0x180, 0x803FFFFF);
    check(armleocpu_csr->csr_satp_mode == 0, "unexpected satp mode");
    check(armleocpu_csr->csr_satp_ppn == 0, "unexpected satp ppn");
    dummy_cycle();

    testnum = 32;
    csr_read(0x180);
    csr_read_check(0x803FFFFF);
    check(armleocpu_csr->csr_satp_mode == 1, "unexpected satp mode");
    check(armleocpu_csr->csr_satp_ppn == 0x3FFFFF, "unexpected satp ppn");
    dummy_cycle();


    testnum = 33;
    cout << "Testing MEDELEG" << endl;
    csr_write(0x302, 0xFFFF);
    dummy_cycle();

    csr_read(0x302);
    csr_read_check(0xBBFF);
    check(armleocpu_csr->csr_medeleg == 0xBBFF, "MEDELEG csr_medeleg output is incorrect");
    dummy_cycle();

    testnum = 33;
    cout << "Testing MIDELEG" << endl;
    csr_write(0x303, 0xFFFF);
    dummy_cycle();

    csr_read(0x303);
    csr_read_check(0x222);
    dummy_cycle();

    testnum = 34;
    cout << "Testing MIE" << endl;
    csr_write(0x304, 0xFFFF);
    dummy_cycle();

    csr_read(0x304);
    csr_read_check(0xAAA);
    dummy_cycle();

    csr_write(0x304, 0x0);
    dummy_cycle();

    csr_read(0x304);
    csr_read_check(0x0);
    dummy_cycle();




    testnum = 35;
    cout << "Testing SIE" << endl;
    csr_write(0x104, 0xFFFF);
    dummy_cycle();

    csr_read(0x104);
    csr_read_check(0x222);
    dummy_cycle();

    csr_write(0x104, 0x0);
    dummy_cycle();

    csr_read(0x104);
    csr_read_check(0x0);
    dummy_cycle();
    

    testnum = 36;
    cout << "Testing SSTATUS" << endl;
    csr_write(0x100, 0xFFFFFFFF);
    dummy_cycle();


    csr_read(0x100);
    csr_read_check(0x000C0122);
    dummy_cycle();
    

    csr_write(0x100, 0x0);
    dummy_cycle();

    csr_read(0x100);
    csr_read_check(0x0);
    dummy_cycle();
    

    cout << "Testing MIP" << endl;




    csr_write(0x300, 0b1000); // mstatus.mie
    dummy_cycle();


    #define TEST_MIP(irq_input_signal, bit_shift) \
    testnum++;\
    csr_write(0x303, 0);/*mideleg*/ \
    dummy_cycle(); \
    \
    csr_write(0x304, 1 << (bit_shift + MACHINE)); /*mie*/\
    dummy_cycle(); \
    \
    irq_input_signal = 1; \
    csr_none(); \
    dummy_cycle(); \
    \
    csr_read(0x344); \
    csr_read_check(1 << (bit_shift + MACHINE)); \
    dummy_cycle(); \
    \
    csr_read(0x144); \
    csr_read_check(0); \
    dummy_cycle(); \
    testnum++;\
    irq_input_signal = 0; \
    csr_none(); \
    dummy_cycle(); \
    \
    csr_write(0x303, 1 << (bit_shift + SUPERVISOR)); /*mideleg*/\
    dummy_cycle(); \
    \
    csr_write(0x304, 1 << (bit_shift + SUPERVISOR)); /*sie*/\
    dummy_cycle(); \
    \
    irq_input_signal = 1; \
    csr_none(); \
    dummy_cycle(); \
 \
    csr_read(0x344); \
    csr_read_check((1 << (bit_shift + MACHINE)) | (1 << (bit_shift + SUPERVISOR))); \
    dummy_cycle(); \
\
    csr_read(0x144); \
    csr_read_check((1 << (bit_shift + SUPERVISOR))); \
    dummy_cycle(); \
 \
    irq_input_signal = 0; \
    csr_none(); \
    dummy_cycle(); \

    TEST_MIP(armleocpu_csr->irq_exti_i, 8)
    TEST_MIP(armleocpu_csr->irq_timer_i, 4)
    TEST_MIP(armleocpu_csr->irq_swi_i, 0)
    
    // TODO: Test MIP/MIE/MIDELEG/etc
        // with rmw sequence
    // TODO: Test with privilege change the MIP's

    
    // TODO: Test READ_SET, READ_CLEAR for each register


    // TODO: Test machine registers for access from supervisor
    // TODO: Test supervisor interrupt handling
    // TODO: Test SRET
    // TODO: Test user accessing supervisor

    

    csr_none();
    dummy_cycle();
    

    cout << "CSR Tests done" << endl;

    } catch(exception e) {
        cout << e.what() << endl;
        dummy_cycle();
        dummy_cycle();
        
    }
    armleocpu_csr->final();
    if (m_trace) {
        m_trace->close();
        m_trace = NULL;
    }
#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete armleocpu_csr; armleocpu_csr = NULL;

    // Fin
    exit(0);
}