#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Varmleocpu_fetch.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Varmleocpu_fetch* armleocpu_fetch;

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

const int ARMLEOCPU_E2F_CMD_IDLE = 0;
const int ARMLEOCPU_E2F_CMD_BRANCHTAKEN = 1;
const int ARMLEOCPU_E2F_CMD_FLUSH = 2;
const int ARMLEOCPU_E2F_CMD_BUBBLE_EXC_START = 3;
const int ARMLEOCPU_E2F_CMD_BUBBLE_EXC_RETURN = 4;

const int MACHINE = 3;
const int SUPERVISOR = 1;
const int USER = 0;

uint32_t mtvec = 0x5400;
uint32_t stvec = 0x5600;

const uint32_t INSTR_NOP = 0b0010011;

double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}
void dump_step() {
    simulation_time++;
    if(trace) m_trace->dump(simulation_time);
}
void update() {
    armleocpu_fetch->eval();
    dump_step();
}

void posedge() {
    armleocpu_fetch->clk = 1;
    update();
    update();
}

void till_user_update() {
    armleocpu_fetch->clk = 0;
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

void check_instr_nop() {
    if(!armleocpu_fetch->f2e_ignore_instr)
        check(armleocpu_fetch->f2e_instr == INSTR_NOP, "unexpected instruction instead of NOP");
    
};

void e2f_nop() {
    armleocpu_fetch->eval();
    check_instr_nop();
    armleocpu_fetch->e2f_ready = 1;
}

void f2e_instr(uint32_t pc, uint32_t instr) {
    armleocpu_fetch->eval();
    check(armleocpu_fetch->f2e_pc == pc, "unexpected pc");
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    check(armleocpu_fetch->f2e_instr == instr, "unexpected instr");
}

void f2e_exc_check_nop() {
    armleocpu_fetch->eval();
    check(armleocpu_fetch->f2e_exc_start == 0, "Exception that should not happen");
}

void cache_check(int cmd, uint32_t address) {
    check(armleocpu_fetch->c_cmd == cmd, "expected cmd is incorrect");
    if(cmd != CACHE_CMD_NONE && cmd != CACHE_CMD_FLUSH_ALL)
        check(armleocpu_fetch->c_address == address, "Incorrect fetch address");
}

void check_dummy_cycle_before_bubble(bool with_nop = 0) {
    if(with_nop)
        check_instr_nop();
    cache_check(CACHE_CMD_NONE, 0);
}

void check_f2e_exc_start(uint32_t cause, uint32_t epc, uint32_t privilege) {
    armleocpu_fetch->eval();
    check(armleocpu_fetch->f2e_exc_start == 1, "Expected exception start: not happened");
    check(armleocpu_fetch->f2e_cause == cause, "Expected exception: incorrect cause");
    check(armleocpu_fetch->f2e_epc == epc, "Expected exception: Unexpected epc");
    check(armleocpu_fetch->f2e_exc_privilege == privilege, "Expected exception: Unexpected cause");
}

// TESTS

void cache_error_test(uint32_t cache_response, uint32_t cause) {
    uint32_t epc;

    armleocpu_fetch->csr_medeleg = 0;
    armleocpu_fetch->c_response = cache_response;
    armleocpu_fetch->csr_mcurrent_privilege = MACHINE;
    armleocpu_fetch->eval();
    check_instr_nop();
    epc = armleocpu_fetch->f2e_pc;
    cache_check(CACHE_CMD_NONE, 0);
    dummy_cycle();


    // Machine test BUBBLE
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->eval();
    check_f2e_exc_start(cause, epc, MACHINE);
    cache_check(CACHE_CMD_EXECUTE, armleocpu_fetch->csr_mtvec);
    dummy_cycle();


    // Supervisor test
    armleocpu_fetch->csr_medeleg = 0;
    armleocpu_fetch->c_response = cache_response;
    armleocpu_fetch->csr_mcurrent_privilege = SUPERVISOR;
    armleocpu_fetch->eval();
    check_instr_nop();
    epc = armleocpu_fetch->f2e_pc;
    cache_check(CACHE_CMD_NONE, 0);
    dummy_cycle();


    // Supervisor test BUBBLE
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->eval();
    check_f2e_exc_start(cause, epc, MACHINE);
    cache_check(CACHE_CMD_EXECUTE, armleocpu_fetch->csr_mtvec);
    dummy_cycle();


    // Supervisor, medeleg test
    armleocpu_fetch->csr_medeleg = 1 << cause;
    armleocpu_fetch->c_response = cache_response;
    armleocpu_fetch->csr_mcurrent_privilege = SUPERVISOR;
    armleocpu_fetch->eval();
    check_instr_nop();
    epc = armleocpu_fetch->f2e_pc;
    cache_check(CACHE_CMD_NONE, 0);
    dummy_cycle();


    // Supervisor, medeleg test BUBBLE
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->eval();
    check_f2e_exc_start(cause, epc, SUPERVISOR);
    cache_check(CACHE_CMD_EXECUTE, armleocpu_fetch->csr_stvec);
    dummy_cycle();


    // User test
    armleocpu_fetch->csr_medeleg = 0;
    armleocpu_fetch->c_response = cache_response;
    armleocpu_fetch->csr_mcurrent_privilege = USER;
    armleocpu_fetch->eval();
    check_instr_nop();
    epc = armleocpu_fetch->f2e_pc;
    cache_check(CACHE_CMD_NONE, 0);
    dummy_cycle();


    // User test BUBBLE
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->eval();
    check_f2e_exc_start(cause, epc, MACHINE);
    cache_check(CACHE_CMD_EXECUTE, armleocpu_fetch->csr_mtvec);
    dummy_cycle();

    

    // Supervisor, medeleg test
    armleocpu_fetch->csr_medeleg = 1 << cause;
    armleocpu_fetch->c_response = cache_response;
    armleocpu_fetch->csr_mcurrent_privilege = USER;
    armleocpu_fetch->eval();
    check_instr_nop();
    epc = armleocpu_fetch->f2e_pc;
    cache_check(CACHE_CMD_NONE, 0);
    dummy_cycle();


    // Supervisor, medeleg test BUBBLE
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->eval();
    check_f2e_exc_start(cause, epc, SUPERVISOR);
    cache_check(CACHE_CMD_EXECUTE, armleocpu_fetch->csr_stvec);
    dummy_cycle();

    armleocpu_fetch->csr_medeleg = 0;
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

    // Construct the Verilated model, from Varmleocpu_fetch.h generated from Verilating "armleocpu_fetch.v"
    armleocpu_fetch = new Varmleocpu_fetch;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    armleocpu_fetch->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");
    try {
    uint32_t epc = 0;
    uint32_t reset_vector = 0x2000;
    uint32_t csr_mtvec = 0x4000;

    armleocpu_fetch->csr_mtvec = csr_mtvec;
    armleocpu_fetch->rst_n = 0;
    armleocpu_fetch->interrupt_pending_csr = 0;

    dummy_cycle();
    armleocpu_fetch->rst_n = 1;
    armleocpu_fetch->c_reset_done = 0;
    armleocpu_fetch->e2f_ready = 0;
    dummy_cycle();
    dummy_cycle();

    cout << "pretending to fetch instructions" << endl;
    testnum = 0;
    armleocpu_fetch->rst_n = 1;

    //csr
    armleocpu_fetch->csr_mtvec = mtvec;
    armleocpu_fetch->csr_stvec = stvec;
    armleocpu_fetch->csr_mcurrent_privilege = MACHINE;
    armleocpu_fetch->csr_medeleg = 0;

    //cache if
    armleocpu_fetch->c_reset_done = 1;
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->c_load_data = 0;
    
    // interrupts
    armleocpu_fetch->interrupt_pending_csr = 0;
    armleocpu_fetch->interrupt_cause = 0;
    armleocpu_fetch->interrupt_target_pc = 0;
    armleocpu_fetch->interrupt_target_privilege = 0;
    // TODO: Test interrupts

    //e2f
    armleocpu_fetch->e2f_ready = 1;
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_IDLE;
    armleocpu_fetch->e2f_branchtarget = 0;
    armleocpu_fetch->e2f_bubble_exc_start_target = 0;
    armleocpu_fetch->e2f_bubble_exc_return_target = 0;
    armleocpu_fetch->e2f_branchtarget = 0;

    //dbg
    armleocpu_fetch->dbg_request = 0;
    armleocpu_fetch->dbg_set_pc = 0;
    armleocpu_fetch->dbg_exit_request = 0;
    
    armleocpu_fetch->eval();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_EXECUTE, "First cycle is not execute");
    check(armleocpu_fetch->c_address == reset_vector, "First fetch is not from reset vector");
    f2e_exc_check_nop();
    check_instr_nop();
    
    cout << "Cache wait" << endl;
    testnum = 1;
    dummy_cycle();
    armleocpu_fetch->c_response = CACHE_RESPONSE_WAIT;
    armleocpu_fetch->c_load_data = 0;
    armleocpu_fetch->e2f_ready = 1;
    armleocpu_fetch->eval();
    check_instr_nop();
    f2e_exc_check_nop();
    cache_check(CACHE_CMD_EXECUTE, reset_vector);
    dummy_cycle();

    cout << "Cache Response done" << endl;
    testnum = 2;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->c_load_data = 0xFF00FF00;
    armleocpu_fetch->e2f_ready = 1;
    armleocpu_fetch->eval();
    f2e_instr(reset_vector, 0xFF00FF00);
    f2e_exc_check_nop();
    cache_check(CACHE_CMD_EXECUTE, reset_vector + 0x4);
    dummy_cycle();



    cout << "Accessfault test" << endl;
    testnum = 3;
    cache_error_test(CACHE_RESPONSE_ACCESSFAULT, 1);
    

    cout << "Pagefault test" << endl;
    testnum = 4;
    cache_error_test(CACHE_RESPONSE_PAGEFAULT, 12);
    

    cout << "Missaligned test" << endl;
    testnum = 5;
    cache_error_test(CACHE_RESPONSE_MISSALIGNED, 0);

    
    cout << "Testing fetch stalling" << endl;
    testnum = 6;
    armleocpu_fetch->e2f_ready = 1;
    armleocpu_fetch->c_response = CACHE_RESPONSE_WAIT;
    armleocpu_fetch->eval();
    check_instr_nop();
    f2e_exc_check_nop();
    cache_check(CACHE_CMD_EXECUTE, stvec);
    // STVEC due to cache_error_test testing stvec last
    dummy_cycle();


    testnum = 7;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->e2f_ready = 0;
    armleocpu_fetch->eval();
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    check(armleocpu_fetch->f2e_instr == 0xFF00FF00, "unexpected instr");
    f2e_exc_check_nop();
    cache_check(CACHE_CMD_NONE, 0);
    

    testnum = 8;
    dummy_cycle();
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->e2f_ready = 0;
    armleocpu_fetch->eval();
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    check(armleocpu_fetch->f2e_instr == 0xFF00FF00, "unexpected instr");
    f2e_exc_check_nop();
    cache_check(CACHE_CMD_NONE, 0);

    testnum = 9;
    cout << "Testing branch" << endl;
    dummy_cycle();
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_BRANCHTAKEN;
    armleocpu_fetch->e2f_branchtarget = csr_mtvec + 4;
    armleocpu_fetch->e2f_ready = 1;
    armleocpu_fetch->eval();
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    check(armleocpu_fetch->f2e_instr == 0xFF00FF00, "unexpected instr");
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_EXECUTE, "expected cmd is incorrect");
    check(armleocpu_fetch->c_address == csr_mtvec + 4, "expected pc is incorrect");
    dummy_cycle();



    testnum = 10;
    cout << "Testing flush" << endl;
    
    armleocpu_fetch->e2f_ready = 0;
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_FLUSH;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->c_load_data = 0xFF00FFFF;
    armleocpu_fetch->eval();
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    check(armleocpu_fetch->f2e_instr == 0xFF00FFFF, "unexpected instr");
    check(armleocpu_fetch->f2e_pc == csr_mtvec + 4, "unexpected pc");
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_NONE, "expected cmd is incorrect should be none");
    dummy_cycle();

    testnum = 11;
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->eval();
    check(armleocpu_fetch->f2e_pc == csr_mtvec + 4, "unexpected pc");
    check(armleocpu_fetch->f2e_instr == 0xFF00FFFF, "unexpected instr");
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_NONE, "expected cmd is incorrect should be flush_all");
    dummy_cycle();

    testnum = 12;
    check(armleocpu_fetch->f2e_pc == csr_mtvec + 4, "unexpected pc");
    check(armleocpu_fetch->f2e_instr == 0xFF00FFFF, "unexpected instr");
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_NONE, "expected cmd is incorrect should be none");
    dummy_cycle();

    testnum = 13;
    armleocpu_fetch->e2f_ready = 1;
    armleocpu_fetch->eval();
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    check(armleocpu_fetch->f2e_instr == 0xFF00FFFF, "unexpected instr");
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_NONE, "expected cmd is incorrect should be none");
    dummy_cycle();

    check_instr_nop();
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_FLUSH_ALL, "expected cmd is incorrect should be flush_all");
    dummy_cycle();

    testnum = 14;
    armleocpu_fetch->c_response = CACHE_RESPONSE_WAIT;
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_IDLE;
    armleocpu_fetch->eval();
    check_instr_nop();
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_FLUSH_ALL, "expected cmd is incorrect should be flush_all");
    
    dummy_cycle();

    testnum = 15;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_IDLE;
    armleocpu_fetch->eval();
    check_instr_nop();
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_NONE, "expected cmd is incorrect should be none");
    dummy_cycle();

    cout << "Testing after flush instruction fetch" << endl;
    testnum = 16;
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_IDLE;
    armleocpu_fetch->eval();
    check_instr_nop();
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_EXECUTE, "expected cmd is incorrect should be execute");
    check(armleocpu_fetch->c_address == csr_mtvec + 8, "expected pc is incorrect");
    dummy_cycle();
    
    cout << "Flush testing done" << endl;


    cout << "Testing interrupt" << endl;
    testnum = 17;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->interrupt_pending_csr = 1;
    armleocpu_fetch->interrupt_cause = 0xF;
    armleocpu_fetch->interrupt_target_pc = 0x6000;
    armleocpu_fetch->interrupt_target_privilege = SUPERVISOR;
    armleocpu_fetch->eval();
    epc = armleocpu_fetch->f2e_pc;
    cache_check(CACHE_CMD_NONE, 0);
    check(armleocpu_fetch->f2e_exc_start == 0, "Unexpected exception");
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    
    check(armleocpu_fetch->f2e_instr == 0xFF00FFFF, "unexpected instr");
    
    dummy_cycle();


    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->eval();
    check_instr_nop();
    check_f2e_exc_start(0xF, epc, SUPERVISOR);
    cache_check(CACHE_CMD_EXECUTE, 0x6000);
    dummy_cycle();


    armleocpu_fetch->c_response = CACHE_RESPONSE_WAIT;
    armleocpu_fetch->interrupt_pending_csr = 0;
    armleocpu_fetch->eval();
    check_instr_nop();
    check(armleocpu_fetch->f2e_exc_start == 0, "Unexpected exception");
    cache_check(CACHE_CMD_EXECUTE, 0x6000);
    dummy_cycle();

    cout << "Testing e2f_cmd ARMLEOCPU_E2F_CMD_BUBBLE_EXC_START" << endl;
    testnum = 25;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_BUBBLE_EXC_START;
    armleocpu_fetch->e2f_bubble_exc_start_target = 0x8000;
    armleocpu_fetch->eval();
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    
    check(armleocpu_fetch->f2e_instr == 0xFF00FFFF, "unexpected instr");
    check(armleocpu_fetch->f2e_pc == 0x6000, "unexpected instr pc");
    f2e_exc_check_nop();
    cache_check(CACHE_CMD_NONE, 0);
    dummy_cycle();

    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_IDLE;
    armleocpu_fetch->eval();
    check_instr_nop();
    f2e_exc_check_nop();
    cache_check(CACHE_CMD_EXECUTE, 0x8000);
    dummy_cycle();

    testnum = 26;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->eval();
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    
    check(armleocpu_fetch->f2e_instr == 0xFF00FFFF, "unexpected instr");
    check(armleocpu_fetch->f2e_pc == 0x8000, "unexpected instr pc");
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_EXECUTE, "expected cmd is incorrect should be execute");
    check(armleocpu_fetch->c_address == 0x8000 + 4, "expected c_address is incorrect");
    dummy_cycle();


    cout << "Testing debug interface" << endl;

    testnum = 27;
    armleocpu_fetch->c_response = CACHE_RESPONSE_WAIT;
    armleocpu_fetch->dbg_request = 1;
    armleocpu_fetch->eval();
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_EXECUTE, "expected cmd is incorrect should be execute");
    check(armleocpu_fetch->c_address == 0x8000 + 4, "expected pc is incorrect");
    check(armleocpu_fetch->dbg_mode == 0, "Debug mode should be zero before dbg request");
    dummy_cycle();

    testnum = 28;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->dbg_request = 1;
    armleocpu_fetch->eval();
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_NONE, "expected cmd is incorrect should be NONE");
    check(armleocpu_fetch->dbg_mode == 0, "Debug mode should be zero before dbg request and when compeleted last instruction");
    dummy_cycle();


    testnum = 29;
    cout << "Testing debug pc set" << endl;
    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->dbg_request = 0;
    armleocpu_fetch->dbg_set_pc = 1;
    armleocpu_fetch->dbg_pc = 0x9000;
    armleocpu_fetch->eval();
    check_instr_nop();
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_NONE, "expected cmd is incorrect should be NONE");
    check(armleocpu_fetch->dbg_mode == 1, "Debug mode should be one");
    check(armleocpu_fetch->dbg_done == 1, "Debug done should be one");
    dummy_cycle();

    testnum = 30;
    armleocpu_fetch->dbg_exit_request = 1;
    armleocpu_fetch->dbg_set_pc = 0;
    armleocpu_fetch->eval();
    check_instr_nop();
    check(armleocpu_fetch->dbg_mode == 1, "Debug mode should be one");
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_EXECUTE, "expected cmd is incorrect should be execute");
    check(armleocpu_fetch->c_address == 0x9000, "expected pc is incorrect");
    
    dummy_cycle();
    armleocpu_fetch->dbg_exit_request = 0;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->eval();
    check(armleocpu_fetch->dbg_mode == 0, "Debug mode should be zero");
    f2e_exc_check_nop();
    check(armleocpu_fetch->c_cmd == CACHE_CMD_EXECUTE, "expected cmd is incorrect should be execute");
    check(armleocpu_fetch->c_address == 0x9004, "expected pc is incorrect");
    dummy_cycle();


    // TODO: exc_start test
    // TODO: exc_return test
    testnum = 31;
    cout << "e2f_cmd exc_start test" << endl;

    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_BUBBLE_EXC_START;
    armleocpu_fetch->e2f_bubble_exc_start_target = 0xF000;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;

    armleocpu_fetch->eval();
    epc = armleocpu_fetch->f2e_pc;
    f2e_exc_check_nop();
    cache_check(CACHE_CMD_NONE, 0);
    dummy_cycle();


    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_IDLE;
    armleocpu_fetch->eval();
    check_instr_nop();
    cache_check(CACHE_CMD_EXECUTE, 0xF000);
    f2e_exc_check_nop();
    dummy_cycle();

    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->eval();
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    
    check(armleocpu_fetch->f2e_instr == 0xFF00FFFF, "unexpected instr");
    check(armleocpu_fetch->f2e_pc == 0xF000, "unexpected instr pc");
    dummy_cycle();

    testnum = 32;
    cout << "e2f_cmd exc_return test" << endl;

    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_BUBBLE_EXC_RETURN;
    armleocpu_fetch->e2f_bubble_exc_return_target = 0xF100;
    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->eval();
    f2e_exc_check_nop();
    cache_check(CACHE_CMD_NONE, 0);
    dummy_cycle();


    armleocpu_fetch->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_fetch->e2f_cmd = ARMLEOCPU_E2F_CMD_IDLE;
    armleocpu_fetch->eval();
    check_instr_nop();
    cache_check(CACHE_CMD_EXECUTE, 0xF100);
    f2e_exc_check_nop();
    dummy_cycle();

    armleocpu_fetch->c_response = CACHE_RESPONSE_DONE;
    armleocpu_fetch->eval();
    cache_check(CACHE_CMD_EXECUTE, 0xF104);
    check(!armleocpu_fetch->f2e_ignore_instr, "Unexpected instruction ignore");
    
    check(armleocpu_fetch->f2e_instr == 0xFF00FFFF, "unexpected instr");
    check(armleocpu_fetch->f2e_pc == 0xF100, "unexpected instr pc");
    f2e_exc_check_nop();
    dummy_cycle();







    cout << "Fetch Tests done" << endl;

    } catch(exception e) {
        cout << e.what() << endl;
        dummy_cycle();
        dummy_cycle();
        
    }
    armleocpu_fetch->final();
    if (m_trace) {
        m_trace->close();
        m_trace = NULL;
    }
#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete armleocpu_fetch; armleocpu_fetch = NULL;

    // Fin
    exit(0);
}