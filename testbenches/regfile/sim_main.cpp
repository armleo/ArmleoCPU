#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Varmleocpu_regfile.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Varmleocpu_regfile* armleocpu_regfile;
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
    armleocpu_regfile->eval();
    dump_step();
}

void posedge() {
    armleocpu_regfile->clk = 1;
    update();
    update();
}

void till_user_update() {
    armleocpu_regfile->clk = 0;
    update();
}
void after_user_update() {
    update();
}

void next_cycle() {
    after_user_update();

    posedge();
    till_user_update();
    armleocpu_regfile->eval();
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

    // Construct the Verilated model, from Varmleocpu_regfile.h generated from Verilating "armleocpu_regfile.v"
    armleocpu_regfile = new Varmleocpu_regfile;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    armleocpu_regfile->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");

    armleocpu_regfile->rst_n = 0;
    armleocpu_regfile->rd_write = 0;
    armleocpu_regfile->rs1_read = 0;
    armleocpu_regfile->rs2_read = 0;
    till_user_update();    
    next_cycle();
    armleocpu_regfile->rst_n = 0;
    next_cycle();
    armleocpu_regfile->rst_n = 1;
    next_cycle();

    try {
        for(int i = 0; i < 32; i++) {
            testnum = i;
            uint32_t val = i | (i << 8) | (i << 16) | (i << 24);
            armleocpu_regfile->rd_write = 1;
            armleocpu_regfile->rd_addr = i;
            armleocpu_regfile->rd_wdata = val;
            armleocpu_regfile->rs1_read = 0;
            armleocpu_regfile->rs2_read = 0;

            next_cycle();

            armleocpu_regfile->rd_write = 0;

            armleocpu_regfile->rs1_read = 1;
            armleocpu_regfile->rs2_read = 1;
            
            armleocpu_regfile->rs1_addr = i;
            armleocpu_regfile->rs2_addr = i;
            next_cycle();
            check(armleocpu_regfile->rs1_rdata == val, "RS1_RDATA: Incorrect");
            check(armleocpu_regfile->rs2_rdata == val, "RS2_RDATA: Incorrect");
            next_cycle();
        
            
        }

        for(int i = 0; i < 32; i++) {
            testnum = 100 + i;
            uint32_t val = i | (i << 8) | (i << 16) | (i << 24);
            armleocpu_regfile->rd_write = 0;
            armleocpu_regfile->rs1_addr = i;
            armleocpu_regfile->rs2_addr = i;
            armleocpu_regfile->rs1_read = 1;
            armleocpu_regfile->rs1_read = 1;
            next_cycle();
            check(armleocpu_regfile->rs1_rdata == val, "RS1_RDATA: Incorrect");
            check(armleocpu_regfile->rs2_rdata == val, "RS2_RDATA: Incorrect");
            next_cycle();
            armleocpu_regfile->rs1_read = 0;
            armleocpu_regfile->rs1_read = 0;
            check(armleocpu_regfile->rs1_rdata == val, "RS1_RDATA: Incorrect");
            check(armleocpu_regfile->rs2_rdata == val, "RS2_RDATA: Incorrect");
            next_cycle();
            
        }



        cout << "Regfile tests done" << endl;
    } catch(exception e) {
        cout << e.what();
        next_cycle();
        next_cycle();
        
    }
    armleocpu_regfile->final();
    if (m_trace) {
        m_trace->close();
        m_trace = NULL;
    }
#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete armleocpu_regfile; armleocpu_regfile = NULL;

    // Fin
    exit(0);
}