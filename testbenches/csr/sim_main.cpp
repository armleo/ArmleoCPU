#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Varmleocpu_csr.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Varmleocpu_csr* armleocpu_csr;

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
    uint32_t reset_vector = 0x2000;
    uint32_t csr_mtvec = 0x4000;
    armleocpu_csr->rst_n = 0;
    armleocpu_csr->csr_cmd = CSR_CMD_NONE;
    dummy_cycle();
    armleocpu_csr->rst_n = 1;
    dummy_cycle();

    cout << "pretending to fetch instructions" << endl;
    testnum = 0;
    armleocpu_csr->rst_n = 1;
    // change inputs
    armleocpu_csr->eval();
    // check outputs
    //check(armleocpu_csr->c_cmd == CACHE_CMD_EXECUTE, "First cycle is not execute");
    
    
    cout << "Test title" << endl;
    testnum = 1;
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