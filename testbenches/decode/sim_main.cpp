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

const uint32_t INSTR_NOP = 0b0010011;

const uint32_t E2D_CMD_NONE = 0;
const uint32_t E2D_CMD_KILL = 1;
const uint32_t E2D_CMD_FLUSH = 2;
const uint32_t E2D_CMD_BRANCH = 3;

void f2d_instr(uint8_t valid, uint32_t instr, uint32_t pc) {
    armleocpu_decode->f2d_instr_valid = valid;
    armleocpu_decode->f2d_instr = instr;
    armleocpu_decode->f2d_pc = pc;
    armleocpu_decode->eval();
}

void e2d_respond(uint8_t ready, uint8_t cmd, uint32_t jump_target, uint8_t write, uint8_t waddr) {
    armleocpu_decode->e2d_ready = ready;
    armleocpu_decode->e2d_cmd = cmd;
    armleocpu_decode->e2d_jump_target = jump_target;
    armleocpu_decode->e2d_rd_write = write;
    armleocpu_decode->e2d_rd_waddr = waddr;
    armleocpu_decode->eval();
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

    till_user_update();    
    next_cycle();
    
    
    try {
        uint32_t pc;

        armleocpu_decode->rst_n = 1;

        f2d_instr(0, INSTR_NOP, pc += 4);
        e2d_respond(0, E2D_CMD_NONE, 0x1FF0, 0, 31);
        
        next_cycle();

        d2f_ready == 0
        d2f_cmd == E2D_CMD_NONE


        d2e_instr_check();
        d2e_instr_illegal == 0
        d2e_instr_decode_alu_output_sel == ALU_OUTPUT_ADD
        d2e_instr_decode_muldiv_sel == MULDIV_OUTPUT_DEFAULT
        d2e_instr_decode_type == ALU
        d2e_instr_decode_alu_in0_mux_sel == IN0_RS1
        d2e_instr_decode_alu_in1_mux_sel == IN1_RS2
        d2e_shamt_sel == SHAMT_DEFAULT
        d2e_rd_sel == RD_SEL_ALU

        f2d_instr(1, INSTR_ADD, pc += 4);
        e2d_respond(0, E2D_CMD_NONE, 0x1FF0, 0, 31);
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
