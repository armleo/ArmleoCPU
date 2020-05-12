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
    return (funct7 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) |(rd << 7) | opcode;
}
uint32_t getRd_addr(uint32_t instruction) {
    return (instruction >> 7) & 0b11111;
}
uint32_t getRs1_addr(uint32_t instruction) {
    return (instruction >> 15) & 0b11111;
}
uint32_t getRs2_addr(uint32_t instruction) {
    return (instruction >> 20) & 0b11111;
}

string getOpcodeName(uint32_t instr) {
    switch (instr & 0b1111111) {
        case 0b0110011:
            return "ALU";
            break;
    }
    return "UNKNOWN";
}

void check(bool match, string msg) {
    
    if(!match) {
        cout << "testnum: " << dec << testnum << endl;
        cout << msg << endl;
        throw runtime_error(msg);
    }
}

void check_cache_none() {
    check(corevx_execute->c_cmd == CACHE_CMD_NONE, "Expected cmd none");
}

void test_alu(uint32_t test, uint32_t instruction, uint32_t rs1_value, uint32_t rs2_value, uint32_t rd_expected_value) {
    testnum = test;
    corevx_execute->f2e_instr = instruction;
    corevx_execute->rs1_data = rs1_value;
    corevx_execute->rs2_data = rs2_value;

    corevx_execute->eval();
    check_cache_none();
    uint32_t rd = getRd_addr(instruction);
    uint32_t rs1 = getRs1_addr(instruction);
    uint32_t rs2 = getRs2_addr(instruction);
    /*cout << "Testing: " << getOpcodeName(instruction) << ", "
        << hex << rd << ", "
        << hex << rs1 << ", " 
        << hex << rs2 << endl;*/
    cout << "["<< dec << testnum << "]    rs1_value: " << hex << rs1_value << ", ";
    cout << "rs2_value: " << hex << rs2_value << ", ";
    cout << "expected result: " << hex << rd_expected_value << ", ";
    cout << "actual result: " << hex << corevx_execute->rd_wdata << endl;
    
    check(corevx_execute->e2f_ready == 1, "Error e2f_ready should be 1");
    check(corevx_execute->e2f_exc_start == 0, "Error e2f_exc_start should be 0");
    check(corevx_execute->e2f_exc_return == 0, "Error e2f_exc_return should be 0");
    check(corevx_execute->e2f_flush == 0, "Error e2f_flush should be 0");
    check(corevx_execute->e2f_branchtaken == 0, "Error e2f_branchtaken should be 0");
    check(corevx_execute->e2debug_machine_ebreak == 0, "Error e2f_branchtaken should be 0");
    

    check(corevx_execute->csr_cmd == 0, "Error csr cmd should be zero");
    check(corevx_execute->csr_exc_cmd == 0, "Error csr exc_start should be zero");
    
    check(corevx_execute->rd_addr == rd, "Error: rd_addr");
    check(corevx_execute->rd_write == (rd != 0), "Error: rd_write");
    check(corevx_execute->rd_wdata == rd_expected_value, "Error: rd_wdata");
    check(corevx_execute->rs1_addr == rs1, "Error: r1_addr");
    check(corevx_execute->rs2_addr == rs2, "Error: r2_addr");
    
    dummy_cycle();
}

void test_auipc(uint32_t test, uint32_t pc, uint32_t upimm20, uint32_t rd) {
    testnum = test;
    corevx_execute->f2e_instr = 0b0010111 | (rd << 7) | (upimm20 << 12);
    corevx_execute->f2e_pc = pc;
    corevx_execute->eval();
    check_cache_none();
    uint32_t rd_expected_value = pc + (upimm20 << 12);
    cout << "Testing: " << "AUIPC" << ", "
        << hex << rd << ", "
        << hex << upimm20 << endl;
    cout << hex <<"["<< dec << testnum << "]    pc = " << pc << " upimm20 = " << upimm20;
    cout << "expected result: " << hex << rd_expected_value << ", ";
    cout << "actual result: " << hex << corevx_execute->rd_wdata << endl;
    
    check(corevx_execute->e2f_ready == 1, "Error e2f_ready should be 1");
    check(corevx_execute->e2f_exc_start == 0, "Error e2f_exc_start should be 0");
    check(corevx_execute->e2f_exc_return == 0, "Error e2f_exc_return should be 0");
    check(corevx_execute->e2f_flush == 0, "Error e2f_flush should be 0");
    check(corevx_execute->e2f_branchtaken == 0, "Error e2f_branchtaken should be 0");
    check(corevx_execute->e2debug_machine_ebreak == 0, "Error e2f_branchtaken should be 0");
    

    check(corevx_execute->csr_cmd == 0, "Error csr cmd should be zero");
    check(corevx_execute->csr_exc_cmd == 0, "Error csr exc_start should be zero");
    
    check(corevx_execute->rd_addr == rd, "Error: rd_addr");
    check(corevx_execute->rd_write == (rd != 0), "Error: rd_write");
    check(corevx_execute->rd_wdata == rd_expected_value, "Error: rd_wdata");
    
    dummy_cycle();
}

void test_lui(uint32_t test, uint32_t upimm20, uint32_t rd) {
    testnum = test;
    corevx_execute->f2e_instr = 0b0110111 | (rd << 7) | (upimm20 << 12);
    corevx_execute->f2e_pc = 0;
    corevx_execute->eval();
    check_cache_none();
    uint32_t rd_expected_value = (upimm20 << 12);
    cout << "Testing: " << "LUI" << ", "
        << hex << rd << ", "
        << hex << upimm20 << endl;
    cout << hex <<"["<< dec << testnum << "]   "<< "upimm20 = " << upimm20;
    cout << "expected result: " << hex << rd_expected_value << ", ";
    cout << "actual result: " << hex << corevx_execute->rd_wdata << endl;
    
    check(corevx_execute->e2f_ready == 1, "Error e2f_ready should be 1");
    check(corevx_execute->e2f_exc_start == 0, "Error e2f_exc_start should be 0");
    check(corevx_execute->e2f_exc_return == 0, "Error e2f_exc_return should be 0");
    check(corevx_execute->e2f_flush == 0, "Error e2f_flush should be 0");
    check(corevx_execute->e2f_branchtaken == 0, "Error e2f_branchtaken should be 0");
    check(corevx_execute->e2debug_machine_ebreak == 0, "Error e2f_branchtaken should be 0");
    

    check(corevx_execute->csr_cmd == 0, "Error csr cmd should be zero");
    check(corevx_execute->csr_exc_cmd == 0, "Error csr exc_start should be zero");
    
    check(corevx_execute->rd_addr == rd, "Error: rd_addr");
    check(corevx_execute->rd_write == (rd != 0), "Error: rd_write");
    check(corevx_execute->rd_wdata == rd_expected_value, "Error: rd_wdata");
    
    dummy_cycle();
}


void test_branch(uint32_t test, uint32_t funct3, uint32_t rs1_val, uint32_t rs2_val, uint32_t branchtaken) {
    testnum = test;
    uint32_t rs1_a = 5;
    uint32_t rs2_a = 6;
    corevx_execute->f2e_instr = 0b1100011 | (funct3 << 12) | (rs1_a << 15) | (rs2_a << 20) | (0b01000 << 7);
    corevx_execute->f2e_pc = 0xFF0;
    corevx_execute->rs1_data = rs1_val;
    corevx_execute->rs2_data = rs2_val;
    
    uint32_t branchtarget = corevx_execute->f2e_pc + 8;
    corevx_execute->eval();
    check_cache_none();
    cout << "Testing: " << "BRANCH" << ", "
        << hex << "funct3 = " << funct3 << ", "
        << hex << "rs1_value = "<< rs1_val << ", "
        << hex << "rs2_value = "<< rs2_val << ", "
        << hex << "should branchtaken = " << branchtaken << endl;
    cout << "expected result: " << hex << branchtaken << ", ";
    cout << "actual result: " << hex << (uint32_t)corevx_execute->e2f_branchtaken << endl;
    
    check(corevx_execute->rs1_addr == rs1_a, "Error: r1_addr");
    check(corevx_execute->rs2_addr == rs2_a, "Error: r2_addr");

    check(corevx_execute->e2f_ready == 1, "Error e2f_ready should be 1");
    check(corevx_execute->e2f_exc_start == 0, "Error e2f_exc_start should be 0");
    check(corevx_execute->e2f_exc_return == 0, "Error e2f_exc_return should be 0");
    check(corevx_execute->e2f_flush == 0, "Error e2f_flush should be 0");
    check(corevx_execute->e2f_branchtaken == branchtaken, "Error e2f_branchtaken should be 1");
    if(branchtaken)
        check(corevx_execute->e2f_branchtarget == branchtarget, "Error e2f_branchtarget should be pc + 8");
    
    check(corevx_execute->e2debug_machine_ebreak == 0, "Error e2f_branchtaken should be 0");
    
    check(corevx_execute->csr_cmd == 0, "Error csr cmd should be zero");
    check(corevx_execute->csr_exc_cmd == 0, "Error csr exc_start should be zero");
    
    check(corevx_execute->rd_write == 0, "Error: rd_write");
    
    dummy_cycle();
}


void test_jalr(uint32_t test, uint32_t jump_offset, uint32_t rs1_val, uint32_t rd) {
    testnum = test;
    uint32_t rs1_a = 30;
    corevx_execute->f2e_instr = 0b1100111 | (rd << 7) | (rs1_a << 15) | (jump_offset << 20);
    corevx_execute->f2e_pc = 0xFFFFFFF0;
    corevx_execute->rs1_data = rs1_val;
    corevx_execute->eval();
    check_cache_none();
    uint32_t branchtarget = jump_offset + rs1_val;
    uint32_t rd_expected_value = corevx_execute->f2e_pc + 4;
    cout << "Testing: " << "JALR" << ", "
        << hex << "rs1_value = "<< rs1_val << ", "
        << hex << "branchtarget = "<< branchtarget << ", "
        << hex << "jump_offset = "<< jump_offset << ";";

    check(corevx_execute->rs1_addr == rs1_a, "Error: r1_addr");

    check(corevx_execute->csr_exc_cmd == 0, "Error csr exc_start should be zero");
    

    check(corevx_execute->e2f_ready == 1, "Error e2f_ready should be 1");
    check(corevx_execute->e2f_exc_start == 0, "Error e2f_exc_start should be 0");
    check(corevx_execute->e2f_exc_return == 0, "Error e2f_exc_return should be 0");
    check(corevx_execute->e2f_flush == 0, "Error e2f_flush should be 0");
    check(corevx_execute->e2f_branchtaken == 1, "Error e2f_branchtaken should be 1");
    check(corevx_execute->e2f_branchtarget == branchtarget, "Error e2f_branchtarget unexpected");
    
    check(corevx_execute->e2debug_machine_ebreak == 0, "Error e2f_branchtaken should be 0");
    
    check(corevx_execute->csr_cmd == 0, "Error csr cmd should be zero");
    
    check(corevx_execute->rd_write == (rd != 0), "Error: rd_write");
    check(corevx_execute->rd_addr == rd, "Error: rd_addr");
    check(corevx_execute->rd_wdata == rd_expected_value, "Error: rd_wdata");
    
    dummy_cycle();
}

void test_jal(uint32_t test, uint32_t jump_offset, uint32_t rd) {
    testnum = test;
    corevx_execute->f2e_instr = 0b1101111 | (rd << 7) | ((jump_offset >> 1) << 21);
    corevx_execute->f2e_pc = 0xFFFFFFF0;
    corevx_execute->eval();
    check_cache_none();
    uint32_t branchtarget = jump_offset + corevx_execute->f2e_pc;
    uint32_t rd_expected_value = corevx_execute->f2e_pc + 4;
    cout << "Testing: " << "JAL" << ", "
        << hex << "branchtarget = "<< branchtarget << ", "
        << hex << "jump_offset = "<< jump_offset << ";" << endl;

    check(corevx_execute->csr_exc_cmd == 0, "Error csr exc_start should be zero");
    

    check(corevx_execute->e2f_ready == 1, "Error e2f_ready should be 1");
    check(corevx_execute->e2f_exc_start == 0, "Error e2f_exc_start should be 0");
    check(corevx_execute->e2f_exc_return == 0, "Error e2f_exc_return should be 0");
    check(corevx_execute->e2f_flush == 0, "Error e2f_flush should be 0");
    check(corevx_execute->e2f_branchtaken == 1, "Error e2f_branchtaken should be 1");
    check(corevx_execute->e2f_branchtarget == branchtarget, "Error e2f_branchtarget unexpected");
    
    check(corevx_execute->e2debug_machine_ebreak == 0, "Error e2f_branchtaken should be 0");
    
    check(corevx_execute->csr_cmd == 0, "Error csr cmd should be zero");
    
    check(corevx_execute->rd_write == (rd != 0), "Error: rd_write");
    check(corevx_execute->rd_addr == rd, "Error: rd_addr");
    check(corevx_execute->rd_wdata == rd_expected_value, "Error: rd_wdata");
    
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

    cout << "Starting execute tests" << endl;
    testnum = 0;
    corevx_execute->rst_n = 1;
    corevx_execute->c_reset_done = 1;
    corevx_execute->csr_mstatus_tvm = 0;
    corevx_execute->csr_mstatus_tw = 0;
    corevx_execute->c_response = CACHE_RESPONSE_IDLE;
    corevx_execute->c_load_data = 0;
    corevx_execute->f2e_exc_start = 0;
    corevx_execute->f2e_cause = 0;
    corevx_execute->csr_invalid = 0;
    corevx_execute->csr_readdata = 0xFFFFFFFF;
    corevx_execute->rs1_data = 0;
    corevx_execute->rs2_data = 0;

    cout << "Starting ALU Tests" << endl;

    testnum = 0;
    corevx_execute->f2e_instr = 0;
    corevx_execute->eval();
    check(corevx_execute->e2f_ready == 1, "Error e2f_ready should be 1");
    check(corevx_execute->e2f_exc_start == 1, "Error e2f_exc_start should be 1");
    check(corevx_execute->e2f_exc_return == 0, "Error e2f_exc_return should be 0");
    check(corevx_execute->e2f_flush == 0, "Error e2f_flush should be 0");
    check(corevx_execute->e2f_branchtaken == 0, "Error e2f_branchtaken should be 0");
    check(corevx_execute->e2debug_machine_ebreak == 0, "Error e2f_branchtaken should be 0");
    
    
    check(corevx_execute->csr_cmd == 0, "Error csr cmd should be zero");
    check(corevx_execute->csr_exc_cmd == 1, "Error csr exc_start should be start");
    check(corevx_execute->csr_exc_cause == 2, "Error: Expected cause should be illegal_instr");
    
    check(corevx_execute->rd_write == 0, "Error: rd_write");

    dummy_cycle();

    cout << "Testing ALU ADD" << endl;
    // ALU, ADD
    test_alu(1, make_r_type(0b0110011, 31, 0b000, 30, 29, 0b0000000),          1,          1,        2);
    test_alu(2, make_r_type(0b0110011, 31, 0b000, 30, 29, 0b0000000),         -1,         -1,       -2);
    test_alu(3, make_r_type(0b0110011, 31, 0b000, 30, 29, 0b0000000), 0xFF      ,          1, 0xFF + 1);
    
    // ALU, SUB
    cout << "Testing ALU SUB" << endl;
    test_alu(4, make_r_type(0b0110011, 31, 0b000, 30, 29, 0b0100000),  1, 1, 0);
    test_alu(5, make_r_type(0b0110011, 31, 0b000, 30, 29, 0b0100000), -1, 1, -2);
    
    cout << "Testing ALU AND" << endl;
    // ALU, AND
    test_alu(6, make_r_type(0b0110011, 31, 0b111, 30, 29, 0b0000000),     1,      1,    1);
    test_alu(7, make_r_type(0b0110011, 31, 0b111, 30, 29, 0b0000000),  0xFF, 0xFF00,    0);
    test_alu(8, make_r_type(0b0110011, 31, 0b111, 30, 29, 0b0000000),  0xFF,   0xFF, 0xFF);

    cout << "Testing ALU OR" << endl;
    // ALU, OR
    test_alu(9 , make_r_type(0b0110011, 31, 0b110, 30, 29, 0b0000000),     1,      1,    1     );
    test_alu(10, make_r_type(0b0110011, 31, 0b110, 30, 29, 0b0000000),  0xFF, 0xFF00,    0xFFFF);
    test_alu(11, make_r_type(0b0110011, 31, 0b110, 30, 29, 0b0000000),  0xFF,   0xFF,    0xFF  );
    // TODO:
    // SLL, SLT, SLTU, XOR, SRL, SRA

    // TODO: SLLI, SLTI, SLTUI, SRLI, SRAI, XORI, ANDI, ADDI, ORI
    
    
    
    testnum = 101;
    test_auipc(101, 0xFF0, 0xFFFFF, 31);
    test_auipc(102, 0xFFF0, 0xFFFFF, 31);
    test_auipc(102, 0xFFF0, 0xFFFFF, 0);

    test_lui(103, 0xFFFFF, 31);
    test_lui(104, 0xFFFFF, 0);

    // branches

    // BEQ, equal
    test_branch(201, 0b000, 0xFF, 0xFF, 1);
    // BEQ, not equal
    test_branch(202, 0b000, 0xFF, 0x1, 0);


    // BNE
        //  equal
        test_branch(203, 0b001, 0xFF, 0xFF, 0);
        //  not equal
        test_branch(204, 0b001, 0xFF, 0xF1, 1);

    // BLT
        // taken
        test_branch(205, 0b100, -1, 0, 1);
        // not taken
        test_branch(206, 0b100, 0, -1, 0);

    // BGE
        // taken
        test_branch(207, 0b101, 0x7FFFFFFF, 0, 1);
        // taken, equal
        test_branch(208, 0b101, 0x7FFFFFFF, 0x7FFFFFFF, 1);
        // not taken
        test_branch(209, 0b101, -1, 0x7FFFFFFF, 0);

    // BLTU
        // taken
        test_branch(210, 0b110, 0, 0xFFFFFFFF, 1);
        // not taken
        test_branch(211, 0b110, 0xFFFFFFFF, 0, 0);
    // BGEU
        // taken
        test_branch(212, 0b111, 0xFFFFFFFF, 0, 1);
        // taken, equal
        test_branch(213, 0b111, 0xFFFFFFFF, 0xFFFFFFFF, 1);
        // not taken
        test_branch(214, 0b111, 0, 0xFFFFFFFF, 0);
    // JALR
        test_jalr(301, 8, 0xFF0, 31);
        test_jalr(302, 8, 0xFF0, 0);
    // JAL
        test_jal(401, 4, 31);
    // LOAD
        // BYTE 0
        // BYTE 1
        // BYTE 2
        // BYTE 3

        // HALF WORD 0
        // HALF WORD 2

        // WORD
    // LOAD, missaligned
        // WORD 1
        // WORD 2
        // WORD 3

        // HALF WORD 1
        // HALF WORD 3
    // LOAD, error
        // PAGEFAULT
        // ACCESSFAULT
    // STORE
        // BYTE 0
        // BYTE 1
        // BYTE 2
        // BYTE 3

        // HALF WORD 0
        // HALF WORD 2

        // WORD
    // STORE, missaligned
        // WORD 1
        // WORD 2
        // WORD 3

        // HALF WORD 1
        // HALF WORD 3
    // STORE, error
        // PAGEFAULT
        // ACCESSFAULT

    // When implemented:
    // Missaligned instruction
    // Instruction pagefault
    // Instruction accessfault
    // ECALL
        // from M, S, U
    // EBREAK
        // from M, S, U

    // FENCE
    // FENCE.I
    // SFENCE.VMA

    // CSR READ-WRITE
    // WFI (tw = 1, tw = 0)
    // MRET, SRET
    

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