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

void csr_write(uint32_t address, uint32_t data) {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_WRITE;
    armleocpu_csr->csr_address = address;
    armleocpu_csr->csr_writedata = data;
    armleocpu_csr->eval();
}

void csr_read(uint32_t address) {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_READ;
    armleocpu_csr->csr_address = address;
    armleocpu_csr->eval();
}

void test_mro(uint32_t address, uint32_t expected_value) {
    csr_read(address);
    check(armleocpu_csr->csr_invalid == 0, "MRO: Failed check invalid == 0");
    check(armleocpu_csr->csr_readdata == expected_value, "MRO: Failed check expected_value");
    dummy_cycle();

    csr_write(address, 0xDEADBEEF);
    check(armleocpu_csr->csr_invalid == 1, "MRO: Failed check invalid == 1");
    //check();
    dummy_cycle();


    csr_read(address);
    check(armleocpu_csr->csr_invalid == 0, "MRO: Failed check invalid == 0");
    check(armleocpu_csr->csr_readdata == expected_value, "MRO: Failed check expected_value");
    dummy_cycle();
}

void csr_none() {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_NONE;
    armleocpu_csr->eval();
}

void test_scratch(uint32_t address) {
    
    csr_write(address, 0xFFFFFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(address);
    check(armleocpu_csr->csr_readdata == 0xFFFFFFFF, "Unexpected readdata");
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_write(address, 0);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(address);
    check(armleocpu_csr->csr_readdata == 0, "Unexpected readdata");
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_none();
    dummy_cycle();

    csr_read(address);
    check(armleocpu_csr->csr_readdata == 0, "Unexpected readdata");
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_none();
}

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
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();
    
    csr_read(0x305);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0xFFFFFFFC, "Unexpected readdata");
    dummy_cycle();

    csr_write(0x305, 0xFFFFFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();
    
    csr_read(0x305);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0xFFFFFFFC, "Unexpected readdata");
    dummy_cycle();

    testnum = 6;
    cout << "Testing MSTATUS" << endl;
    csr_read(0x300);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0x0, "Unexpected readdata");
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
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();
    csr_read(0x300);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == val, "Unexpected readdata");
    
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
    check(armleocpu_csr->csr_readdata == 0b01000000000101000001000100000000, "Unexpected readdata");
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();
    
    testnum = 9;
    csr_write(0x301, 0xFFFFFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    
    dummy_cycle();
    csr_read(0x301);
    check(armleocpu_csr->csr_readdata == 0b01000000000101000001000100000001, "Unexpected readdata");
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();


    testnum = 10;
    cout << "Testing SSCRATCH" << endl;
    test_scratch(0x140);

    testnum = 11;
    cout << "Testing SEPC" << endl;
    
    csr_write(0x141, 0b11);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x141);
    check(armleocpu_csr->csr_readdata == 0, "Unexpected readdata");
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_write(0x141, 0b100);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x141);
    check(armleocpu_csr->csr_readdata == 0b100, "Unexpected readdata");
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();


    testnum = 12;
    cout << "Testing MEPC" << endl;
    
    csr_write(0x341, 0b11);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x341);
    check(armleocpu_csr->csr_readdata == 0, "Unexpected readdata");
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_write(0x341, 0b100);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x341);
    check(armleocpu_csr->csr_readdata == 0b100, "Unexpected readdata");
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    testnum = 13;
    cout << "Testing STVEC" << endl;

    csr_write(0x105, 0xFFFFFFFC);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();
    
    csr_read(0x105);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0xFFFFFFFC, "Unexpected readdata");
    dummy_cycle();

    csr_write(0x105, 0xFFFFFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();
    
    csr_read(0x105);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0xFFFFFFFC, "Unexpected readdata");
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
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    uint32_t begin_value = armleocpu_csr->csr_readdata;
    cout << "Testing MCYCLE: Start time = " << begin_value << endl;
    dummy_cycle();
    
    testnum = 19;
    csr_read(0xB00);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == begin_value + 1, "Unexpected csr_readdata");
    dummy_cycle();

    testnum = 20;
    csr_write(0xB80, 1);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    testnum = 21;
    csr_write(0xB00, -1);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();
    
    csr_none();
    dummy_cycle();

    testnum = 22;
    csr_read(0xB00);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0, "Unexpected csr_readdata");
    dummy_cycle();

    testnum = 23;
    csr_read(0xB80);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 2, "Unexpected csr_readdata");
    dummy_cycle();

    testnum = 24;
    cout << "Testing INSTRET" << endl;
    
    armleocpu_csr->instret_incr = 1;
    csr_read(0xB02);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0, "Unexpected csr_readdata");
    dummy_cycle();


    testnum = 25;
    csr_read(0xB02);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 1, "Unexpected csr_readdata");
    dummy_cycle();

    testnum = 26;
    csr_write(0xB82, 1);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    testnum = 27;
    csr_write(0xB02, -1);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_none();
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    testnum = 28;
    csr_read(0xB82);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 2, "Unexpected csr_readdata");
    dummy_cycle();

    testnum = 29;
    csr_read(0xB02);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 1, "Unexpected csr_readdata");
    dummy_cycle();

    testnum = 30;
    armleocpu_csr->instret_incr = 0;
    csr_none();
    dummy_cycle();

    cout << "Testing SATP" << endl;
    testnum = 31;
    csr_write(0x180, 0x803FFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_satp_mode == 0, "unexpected satp mode");
    check(armleocpu_csr->csr_satp_ppn == 0, "unexpected satp ppn");
    dummy_cycle();

    testnum = 32;
    csr_read(0x180);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0x803FFFFF, "Unexpected readdata");
    check(armleocpu_csr->csr_satp_mode == 1, "unexpected satp mode");
    check(armleocpu_csr->csr_satp_ppn == 0x3FFFFF, "unexpected satp ppn");
    dummy_cycle();


    testnum = 33;
    cout << "Testing MEDELEG" << endl;
    csr_write(0x302, 0xFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x302);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0xBBFF, "Unexpected readdata");
    dummy_cycle();

    testnum = 33;
    cout << "Testing MIDELEG" << endl;
    csr_write(0x303, 0xFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x303);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0x222, "Unexpected readdata");
    dummy_cycle();

    testnum = 34;
    cout << "Testing MIE" << endl;
    csr_write(0x304, 0xFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x304);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0xAAA, "Unexpected readdata");
    dummy_cycle();

    csr_write(0x304, 0x0);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x304);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0x0, "Unexpected readdata");
    dummy_cycle();




    testnum = 35;
    cout << "Testing SIE" << endl;
    csr_write(0x104, 0xFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x104);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0x222, "Unexpected readdata");
    dummy_cycle();

    csr_write(0x104, 0x0);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x104);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0x0, "Unexpected readdata");
    dummy_cycle();
    

    testnum = 36;
    cout << "Testing SSTATUS" << endl;
    csr_write(0x100, 0xFFFFFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();


    csr_read(0x100);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0x000C0122, "Unexpected readdata");
    dummy_cycle();
    

    csr_write(0x100, 0x0);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();

    csr_read(0x100);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0x0, "Unexpected readdata");
    dummy_cycle();
    

    // TODO: Test READ_SET, READ_CLEAR


    csr_none();
    dummy_cycle();
    // TODO:
        // Test mip
    // TODO: Test machine registers for access from supervisor
    // TODO: Test supervisor interrupt handling
        // Test sstatus
        // Test sie
        // Test stvec
        // Test SRET
    // Test supervisor regs
        // Test sip
    // Test user accessing supervisor

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