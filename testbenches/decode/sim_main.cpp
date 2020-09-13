#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Varmleocpu_decode.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Varmleocpu_decode* armleocpu_decode;
uint32_t testnum = 0;

using namespace std;

double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}
void dump_step() {
    simulation_time++;
    if(trace) m_trace->dump(simulation_time);
}
void update() {
    armleocpu_decode->eval();
    dump_step();
}

void posedge() {
    armleocpu_decode->clk = 1;
    update();
    update();
}

void till_user_update() {
    armleocpu_decode->clk = 0;
    update();
}
void after_user_update() {
    update();
}

void next_cycle() {
    after_user_update();

    posedge();
    till_user_update();
    armleocpu_decode->eval();
}

void check(bool match, string msg) {
    
    if(!match) {
        cout << "testnum: " << dec << testnum << endl;
        cout << msg << endl;
        cout << flush;
        throw runtime_error(msg);
    }
}

int main(int argc, char** argv, char** env) {
    cout << "Test started" << endl;
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

    // Construct the Verilated model, from Varmleocpu_decode.h generated from Verilating "armleocpu_decode.v"
    armleocpu_decode = new Varmleocpu_decode;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    armleocpu_decode->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");

    armleocpu_decode->rst_n = 0;
    armleocpu_decode->rd_write = 0;
    armleocpu_decode->rs1_read = 0;
    armleocpu_decode->rs2_read = 0;

    till_user_update();    
    next_cycle();
    armleocpu_decode->rst_n = 1;
    next_cycle();

    armleocpu_decode->rst_n = 0;
    next_cycle();
    
    try {
        armleocpu_decode->rst_n = 1;
        next_cycle();
        cout << "Decode tests done" << endl;
    } catch(exception e) {
        cout << e.what();
        next_cycle();
        next_cycle();
        
    }
    armleocpu_decode->final();
    if (m_trace) {
        m_trace->close();
        m_trace = NULL;
    }
#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete armleocpu_decode; armleocpu_decode = NULL;

    // Fin
    exit(0);
}
