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

#define check(expr) {if(!(expr)) {cout << "Testnum: " << testnum << " Simulation time:" << simulation_time << " Check failed: " #expr "" << endl; throw runtime_error("Failed: " #expr "");}}


const uint32_t INSTR_NOP = 0b0010011;

const uint32_t E2D_CMD_NONE = 0;
const uint32_t E2D_CMD_KILL = 1;
const uint32_t E2D_CMD_FLUSH = 2;
const uint32_t E2D_CMD_BRANCH = 3;

const uint8_t MULDIV_OUTPUT_MUL = 0;
const uint8_t MULDIV_OUTPUT_DEFAULT = MULDIV_OUTPUT_MUL;

const uint8_t ALU_OUTPUT_ADD = 0;
const uint32_t INSTR_ADD = 0b0110011;

const uint8_t TYPE_ALU = 1;

const uint8_t IN0_RS1 = 0;
const uint8_t IN1_RS2 = 0;

const uint8_t SHAMT_RS2 = 0;
const uint8_t SHAMT_DEFAULT = SHAMT_RS2;

const uint8_t RD_SEL_ALU = 0;

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


void d2f_check(uint8_t ready, uint8_t cmd) {
    check(armleocpu_decode->d2f_ready == ready);
    check(armleocpu_decode->d2f_cmd == cmd);

}


void d2e_instr_invalid() {
    check(armleocpu_decode->d2e_instr_valid == 0);
}

void d2e_alu_instr_check(uint32_t instr, uint8_t alu_select) {
    check(armleocpu_decode->d2e_instr_valid == 1);
    check(armleocpu_decode->d2e_instr_illegal == 0);
    check(armleocpu_decode->d2e_instr_decode_alu_output_sel == alu_select);
    check(armleocpu_decode->d2e_instr_decode_muldiv_sel == MULDIV_OUTPUT_DEFAULT);
    check(armleocpu_decode->d2e_instr_decode_type == TYPE_ALU);
    check(armleocpu_decode->d2e_instr_decode_alu_in0_mux_sel == IN0_RS1);
    check(armleocpu_decode->d2e_instr_decode_alu_in1_mux_sel == IN1_RS2);
    check(armleocpu_decode->d2e_instr_decode_shamt_sel == SHAMT_DEFAULT);
    check(armleocpu_decode->d2e_instr_decode_rd_sel == RD_SEL_ALU);

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
        armleocpu_decode->csr_mstatus_tsr = 0;
        armleocpu_decode->csr_mstatus_tvm = 0;
        armleocpu_decode->csr_mstatus_tw = 0;
        armleocpu_decode->csr_mcurrent_privilege = 0b11;
        // No instruction available
        testnum = 0;
        e2d_respond(0, E2D_CMD_NONE, 0x1FF0, 0, 31);
        f2d_instr(0, INSTR_NOP, pc);
        d2e_instr_invalid();
        d2f_check(0, E2D_CMD_NONE);
        next_cycle();
        
        // Instruction available, execute waiting for instr
        testnum = 1;
        e2d_respond(0, E2D_CMD_NONE, 0x1FF0, 0, 31);
        f2d_instr(1, INSTR_ADD, pc);
        d2e_instr_invalid();
        d2f_check(1, E2D_CMD_NONE);
        next_cycle();
        

        testnum = 2;
        e2d_respond(0, E2D_CMD_NONE, 0x1FF0, 0, 31);
        f2d_instr(1, INSTR_ADD, pc += 4);
        d2e_alu_instr_check(INSTR_ADD, ALU_OUTPUT_ADD);
        d2f_check(0, E2D_CMD_NONE);
        next_cycle();

        testnum = 3;
        e2d_respond(1, E2D_CMD_NONE, 0x1FF0, 0, 31);
        f2d_instr(1, INSTR_ADD, pc);
        d2e_alu_instr_check(INSTR_ADD, ALU_OUTPUT_ADD);
        d2f_check(1, E2D_CMD_NONE);
        next_cycle();

        /*
        d2e_alu_instr_check(INSTR, ALU_OUTPUT_ADD);
        d2e_alui_instr_check(INSTR, ALU_OUTPUT_ADD);
        d2e_muldiv_instr_check(INSTR, MULDIV_OUTPUT_MUL);
        d2e_auipc_instr_check();
        d2e_lui_instr_check();
        

        rs1_read
        rs1_addr
        rs2_read
        rs2_addr
        */
        
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
