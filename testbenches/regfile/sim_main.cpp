#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Varmleocpu_regfile.h>
#include <iostream>
#include <random>
#include <limits>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Varmleocpu_regfile* armleocpu_regfile;
uint32_t testnum = 0;
bool error_happened = false;

using namespace std;

double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}

void dump_step() {
    simulation_time++;
    if(trace) m_trace->dump(simulation_time);
}
void update(bool eval = true) {
    if(eval)
        armleocpu_regfile->eval();
    dump_step();
}

void posedge() {
    armleocpu_regfile->clk = 1;
    update();
    update(0);
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

void print_state(uint32_t state[32]) {
    for(int i = 0 ; i < 32; i++) {
        cout << "reg[" << i << "] = " << state[i] << endl;
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

        cout << "Starting torture tests" << endl;
        uint32_t saved_state[32] = {0};
        uint32_t torture_max = 1000;
        uint32_t tortures_per_percent = torture_max/100;
        armleocpu_regfile->rs1_read = 0;
        armleocpu_regfile->rs1_read = 0;
    
        // Modify state
        
        std::default_random_engine generator;
        std::uniform_int_distribution<uint8_t> regnum_distribution(0,31);
        std::uniform_int_distribution<uint32_t> value_distribution(std::numeric_limits<uint32_t>::min(),std::numeric_limits<uint32_t>::max());

        // Read current state
        for(int i = 0; i < 32; i++) {
            armleocpu_regfile->rs1_addr = i;
            armleocpu_regfile->rs1_read = 1;
            next_cycle();
            saved_state[i] = armleocpu_regfile->rs1_rdata;
            print_state(saved_state);
        }
        armleocpu_regfile->rs1_read = 0;

        for(uint32_t i = 0; i < torture_max; i++) {
            testnum = 100 + i;

            uint8_t regnum = regnum_distribution(generator);
            uint32_t regvalue = value_distribution(generator);

            
            armleocpu_regfile->rd_write = 1;
            armleocpu_regfile->rd_addr = regnum;
            armleocpu_regfile->rd_wdata = regvalue;
            
            next_cycle();


            if(regnum != 0)
                saved_state[regnum] = regvalue;
            
            if((i % tortures_per_percent) == 0)
                cout << (i / tortures_per_percent) << "% complete" << endl;

        }
        armleocpu_regfile->rd_write = 0;
        // Read current state
        for(int i = 0; i < 32; i++) {
            armleocpu_regfile->rs1_addr = i;
            armleocpu_regfile->rs1_read = 1;
            armleocpu_regfile->rs2_addr = i;
            armleocpu_regfile->rs2_read = 1;
            next_cycle();
            if((saved_state[i] != armleocpu_regfile->rs1_rdata) ||
                (saved_state[i] != armleocpu_regfile->rs2_rdata)) {
                    cout << "Failed reading expected = " << saved_state[i] << ", got = " << armleocpu_regfile->rs2_rdata << " for register " << uint32_t(armleocpu_regfile->rs1_addr) << endl;
                    throw runtime_error("!ERROR! Regfile [BUG] [!BUG!]");
                }
            else {
                cout << "Success reading expected = " << saved_state[i] << ", got = " << armleocpu_regfile->rs2_rdata << " for register " << uint32_t(armleocpu_regfile->rs1_addr) << endl;
                    
            }
            
        }

        cout << "Regfile tests done" << endl;
    } catch(exception e) {
        cout << e.what();
        next_cycle();
        next_cycle();
        error_happened = true;
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
    if(error_happened)
        exit(-1);
    // Fin
    exit(0);
}