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

    csr_write(address, 0xFF00FF00);
    check(armleocpu_csr->csr_invalid == 1, "MRO: Failed check invalid == 1");
    //check();
    dummy_cycle();


    csr_read(address);
    check(armleocpu_csr->csr_invalid == 0, "MRO: Failed check invalid == 0");
    check(armleocpu_csr->csr_readdata == expected_value, "MRO: Failed check expected_value");
    dummy_cycle();
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
    dummy_cycle();
    armleocpu_csr->rst_n = 1;
    dummy_cycle();

    cout << "Testing MSCRATCH with -1" << endl;
    testnum = 0;
    armleocpu_csr->rst_n = 1;

    csr_write(0x340, 0xFFFFFFFF);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();
    csr_read(0x340);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0xFFFFFFFF, "Unexpected readdata");
    dummy_cycle();


    testnum = 1;
    cout << "Testing MSCRATCH with zero" << endl;
    csr_write(0x340, 0);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    dummy_cycle();
    csr_read(0x340);
    check(armleocpu_csr->csr_invalid == 0, "Unexpected invalid");
    check(armleocpu_csr->csr_readdata == 0, "Unexpected readdata");
    dummy_cycle();

    cout << "Testing MVENDORID" << endl;
    test_mro(0xF11, 0x0A1AA1E0);
    cout << "Testing MARCHID" << endl;
    test_mro(0xF12, 1);
    cout << "Testing MIMPID" << endl;
    test_mro(0xF13, 1);
    cout << "Testing MHARTID" << endl;
    test_mro(0xF14, 0);

    dummy_cycle();
    
    // TODO:
        // Test SATP
    // TODO: Test interrupt handling
        // Test MSTATUS
        // Test MIE
        // Test mtvec
        // Test mepc
        // Test mtval
        // Test mip
    // TODO: Test machine registers for access from supervisor
    // TODO: Test supervisor interrupt handling
        // Test sstatus
        // Test sie
        // Test stvec
        // Test SRET
    // TODO: Test timers
        // Test cycle, cycleh, time, timeh
        // Test instret, instreth
    // Test supervisor regs
        // Test sscratch
        // Test sepc
        // Test scause
        // Test stval
        // Test sip
    // Test misa
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