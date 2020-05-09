#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Vcorevx_execute.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Vcorevx_execute* corevx_execute;

uint32_t testnum;

using namespace std;

const int CACHE_CMD_NONE = 0;
const int CACHE_CMD_EXECUTE = 1;
const int CACHE_CMD_LOAD = 2;
const int CACHE_CMD_STORE = 3;
const int CACHE_CMD_FLUSH_ALL = 4;

const int CACHE_RESPONSE_IDLE = 0;
const int CACHE_RESPONSE_WAIT = 1;
const int CACHE_RESPONSE_DONE = 2;
const int CACHE_RESPONSE_ACCESSFAULT = 3;
const int CACHE_RESPONSE_PAGEFAULT = 4;
const int CACHE_RESPONSE_MISSALIGNED = 5;
const int CACHE_RESPONSE_UNKNOWNTYPE = 6;

const uint32_t INSTR_NOP = 0b0010011;

double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}
void dump_step() {
    simulation_time++;
    if(trace) m_trace->dump(simulation_time);
}
void update() {
    corevx_execute->eval();
    dump_step();
}

void posedge() {
    corevx_execute->clk = 1;
    update();
    update();
}

void till_user_update() {
    corevx_execute->clk = 0;
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

uint32_t make_r_type(uint32_t opcode, uint32_t rd, uint32_t funct3, uint32_t rs1, uint32_t rs2, uint32_t funct7) {
    return (rd << 7) | opcode;
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

    // Construct the Verilated model, from Vcorevx_execute.h generated from Verilating "corevx_execute.v"
    corevx_execute = new Vcorevx_execute;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    corevx_execute->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");
    try {
    corevx_execute->rst_n = 0;
    

    dummy_cycle();
    corevx_execute->rst_n = 1;
    corevx_execute->c_reset_done = 0;
    dummy_cycle();
    dummy_cycle();

    cout << "pretending to fetch instructions" << endl;
    testnum = 0;
    corevx_execute->rst_n = 1;
    corevx_execute->c_reset_done = 1;
    corevx_execute->c_response = CACHE_RESPONSE_IDLE;
    corevx_execute->c_load_data = 0;
    corevx_execute->f2e_exc_start = 0;
    corevx_execute->f2e_cause = 0;
    corevx_execute->f2e_cause_interrupt = 0;
    //corevx_execute->dbg_mode = 0;
    

    cout << "Starting ALU Tests";
    //corevx_execute->f2e_instr = make_r_type();
    //corevx_execute->f2e_pc = ;
    //corevx_execute->eval();
    
    cout << bin << make_r_type(1, 1, 0, 0, 0, 0) << " " << 0b10000001 << endl;
    cout << "Execute Tests done" << endl;

    } catch(exception e) {
        cout << e.what() << endl;
        dummy_cycle();
        
    }
    corevx_execute->final();
    if (m_trace) {
        m_trace->close();
        m_trace = NULL;
    }
#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete corevx_execute; corevx_execute = NULL;

    // Fin
    exit(0);
}