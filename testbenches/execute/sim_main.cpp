#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Varmleocpu_execute.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Varmleocpu_execute* armleocpu_execute;

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

const int ARMLEOCPU_E2F_CMD_BUBBLE_EXC_RETURN = 4;
const int ARMLEOCPU_E2F_CMD_BUBBLE_EXC_START = 3;
const int ARMLEOCPU_E2F_CMD_FLUSH = 2;
const int ARMLEOCPU_E2F_CMD_BRANCHTAKEN = 1;
const int ARMLEOCPU_E2F_CMD_IDLE = 0;

const int ARMLEOCPU_CSR_CMD_NONE = 0;
const int ARMLEOCPU_CSR_CMD_READ = 1;
const int ARMLEOCPU_CSR_CMD_WRITE = 2;
const int ARMLEOCPU_CSR_CMD_READ_WRITE = 3;
const int ARMLEOCPU_CSR_CMD_READ_SET = 4;
const int ARMLEOCPU_CSR_CMD_READ_CLEAR = 5;
const int ARMLEOCPU_CSR_CMD_MRET = 6;
const int ARMLEOCPU_CSR_CMD_SRET = 7;
const int ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN = 8;

const int MACHINE = 3;
const int SUPERVISOR = 1;
const int USER = 0;

const uint32_t INSTR_NOP = 0b0010011;

double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}
void dump_step() {
    simulation_time++;
    if(trace) m_trace->dump(simulation_time);
}
void update() {
    armleocpu_execute->eval();
    dump_step();
}

void posedge() {
    armleocpu_execute->clk = 1;
    update();
    update();
}

void till_user_update() {
    armleocpu_execute->clk = 0;
    update();
}
void after_user_update() {
    update();
}

void next_cycle() {
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
        cout << flush;
        throw runtime_error(msg);
    }
}

void check_cache_none() {
    check(armleocpu_execute->c_cmd == CACHE_CMD_NONE, "Expected cmd none");
}

void check_e2debug_none() {
    check(armleocpu_execute->e2debug_machine_ebreak == 0, "Error: e2debug_machine_ebreak");
}

void csr_check_none() {
    check(armleocpu_execute->csr_cmd == ARMLEOCPU_CSR_CMD_NONE, "Error: csr cmd is not None");
}

void rd_check(uint32_t rd, uint32_t rd_expected_value) {
    check(armleocpu_execute->rd_write == (rd != 0), "Error: rd_write");
    if(rd != 0) {
        check(armleocpu_execute->rd_addr == rd, "Error: rd_addr");
        check(armleocpu_execute->rd_wdata == rd_expected_value, "Error: rd_wdata");
    }
}


void rd_check_none() {
    check(armleocpu_execute->rd_write == 0, "Error: rd_write");
}

void check_e2f_normal_op() {
    check(armleocpu_execute->e2f_ready == 1, "Error: e2f_ready is not 1 for normal OP");
    check(armleocpu_execute->e2f_cmd == ARMLEOCPU_E2F_CMD_IDLE, "Error: E2F_CMD is not IDLE for normal OP");
}

void check_e2f_not_ready() {
    check(armleocpu_execute->e2f_ready == 0, "Error: e2f_ready is not 0 for incomplete OP");
    check(armleocpu_execute->e2f_cmd == ARMLEOCPU_E2F_CMD_IDLE, "Error: E2F_CMD is not IDLE for incomplete OP");

}

void check_e2f_branch(uint32_t branchtarget) {
    check(armleocpu_execute->e2f_ready == 1, "Error: e2f_ready is not 1 for branch");
    check(armleocpu_execute->e2f_cmd == ARMLEOCPU_E2F_CMD_BRANCHTAKEN, "Error: E2F_CMD is not BRANCHTAKEN for Branch");
}

void check_e2f_interrupt(uint32_t next_pc) {
    check(armleocpu_execute->e2f_ready == 1, "Error: e2f_ready is not 1 for interrupt/exception begin");
    check(armleocpu_execute->e2f_cmd == ARMLEOCPU_E2F_CMD_BUBBLE_EXC_START, "Error: e2f_cmd is not bubble_exc_start for interrupt/exception begin");
    check(armleocpu_execute->e2f_bubble_exc_start_target == next_pc, "Error: e2f_bubble_exc_start_target has unexpected value for interrupt/exception begin");
}


void csr_check_int(uint32_t cause, uint32_t epc, uint8_t mode) {
    check(armleocpu_execute->csr_cmd == ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN, "Error: csr_cmd != ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN");
    check(armleocpu_execute->csr_exc_cause == cause, "Error: csr_exc_cause");
    check(armleocpu_execute->csr_exc_privilege == mode, "Error: csr_exc_privilege");
    check(armleocpu_execute->csr_exc_epc == epc, "Error: csr_exc_epc");
}

void test_alu(uint32_t test, uint32_t instruction, uint32_t rs1_value, uint32_t rs2_value, uint32_t rd_expected_value) {
    testnum = test;
    armleocpu_execute->f2e_instr = instruction;
    armleocpu_execute->rs1_data = rs1_value;
    armleocpu_execute->rs2_data = rs2_value;

    armleocpu_execute->eval();
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
    cout << "actual result: " << hex << armleocpu_execute->rd_wdata << endl;
    
    check_e2f_normal_op();
    csr_check_none();

    check_e2debug_none();
    

    rd_check(rd, rd_expected_value);
    check(armleocpu_execute->rs1_addr == rs1, "Error: r1_addr");
    check(armleocpu_execute->rs2_addr == rs2, "Error: r2_addr");
    
    next_cycle();
}

void test_auipc(uint32_t test, uint32_t pc, uint32_t upimm20, uint32_t rd) {
    testnum = test;
    armleocpu_execute->f2e_instr = 0b0010111 | (rd << 7) | (upimm20 << 12);
    armleocpu_execute->f2e_pc = pc;
    armleocpu_execute->eval();
    check_cache_none();
    uint32_t rd_expected_value = pc + (upimm20 << 12);
    cout << "Testing: " << "AUIPC" << ", "
        << hex << rd << ", "
        << hex << upimm20 << endl;
    cout << hex <<"["<< dec << testnum << "]    pc = " << pc << " upimm20 = " << upimm20;
    cout << "expected result: " << hex << rd_expected_value << ", ";
    cout << "actual result: " << hex << armleocpu_execute->rd_wdata << endl;
    
    check_e2f_normal_op();
    csr_check_none();
    
    check_e2debug_none();
    
    rd_check(rd, rd_expected_value);
    
    next_cycle();
}

void test_lui(uint32_t test, uint32_t upimm20, uint32_t rd) {
    testnum = test;
    armleocpu_execute->f2e_instr = 0b0110111 | (rd << 7) | (upimm20 << 12);
    armleocpu_execute->f2e_pc = 0;
    armleocpu_execute->eval();
    check_cache_none();
    uint32_t rd_expected_value = (upimm20 << 12);
    cout << "Testing: " << "LUI" << ", "
        << hex << rd << ", "
        << hex << upimm20 << endl;
    cout << hex <<"["<< dec << testnum << "]   "<< "upimm20 = " << upimm20;
    cout << "expected result: " << hex << rd_expected_value << ", ";
    cout << "actual result: " << hex << armleocpu_execute->rd_wdata << endl;
    
    check_e2f_normal_op();
    csr_check_none();
    
    check_e2debug_none();
    
    rd_check(rd, rd_expected_value);
    
    next_cycle();
}


void test_branch(uint32_t test, uint32_t funct3, uint32_t rs1_val, uint32_t rs2_val, uint32_t branchtaken) {
    testnum = test;
    uint32_t rs1_a = 5;
    uint32_t rs2_a = 6;
    armleocpu_execute->f2e_instr = 0b1100011 | (funct3 << 12) | (rs1_a << 15) | (rs2_a << 20) | (0b01000 << 7);
    armleocpu_execute->f2e_pc = 0xFF0;
    armleocpu_execute->rs1_data = rs1_val;
    armleocpu_execute->rs2_data = rs2_val;
    
    uint32_t branchtarget = armleocpu_execute->f2e_pc + 8;
    armleocpu_execute->eval();
    
    
    cout << "Testing: " << "BRANCH" << ", "
        << hex << "funct3 = " << funct3 << ", "
        << hex << "rs1_value = "<< rs1_val << ", "
        << hex << "rs2_value = "<< rs2_val << ", "
        << hex << "should branchtaken = " << branchtaken << endl;
    cout << "expected result: " << hex << branchtaken << ", ";
    cout << "actual result: " << hex << (uint32_t)(armleocpu_execute->e2f_cmd == ARMLEOCPU_E2F_CMD_BRANCHTAKEN) << endl;
    
    check_cache_none();

    check(armleocpu_execute->rs1_addr == rs1_a, "Error: r1_addr");
    check(armleocpu_execute->rs2_addr == rs2_a, "Error: r2_addr");
    
    
    csr_check_none();

    
    check_e2debug_none();

    if(!branchtaken)
        check_e2f_normal_op();
    else
        check_e2f_branch(branchtarget);

    rd_check_none();

    next_cycle();
}

uint32_t LOAD_BYTE = (0b000);
uint32_t LOAD_BYTE_UNSIGNED = (0b100);

uint32_t LOAD_HALF = (0b001);
uint32_t LOAD_HALF_UNSIGNED = (0b101);

uint32_t LOAD_WORD = (0b010);



void test_jalr(uint32_t test, uint32_t jump_offset, uint32_t rs1_val, uint32_t rd) {
    testnum = test;
    uint32_t rs1_a = 30;
    armleocpu_execute->f2e_instr = 0b1100111 | (rd << 7) | (rs1_a << 15) | (jump_offset << 20);
    armleocpu_execute->f2e_pc = 0xFFFFFFF0;
    armleocpu_execute->rs1_data = rs1_val;
    armleocpu_execute->eval();
    check_cache_none();
    uint32_t branchtarget = jump_offset + rs1_val;
    uint32_t rd_expected_value = armleocpu_execute->f2e_pc + 4;
    cout << "Testing: " << "JALR" << ", "
        << hex << "rs1_value = "<< rs1_val << ", "
        << hex << "branchtarget = "<< branchtarget << ", "
        << hex << "jump_offset = "<< jump_offset << ";";

    check(armleocpu_execute->rs1_addr == rs1_a, "Error: r1_addr");

    csr_check_none();
    check_e2f_branch(branchtarget);
    check_e2debug_none();
    rd_check(rd, rd_expected_value);
    
    next_cycle();
}

void test_jal(uint32_t test, uint32_t jump_offset, uint32_t rd) {
    testnum = test;
    armleocpu_execute->f2e_instr = 0b1101111 | (rd << 7) | ((jump_offset >> 1) << 21);
    armleocpu_execute->f2e_pc = 0xFFFFFFF0;
    armleocpu_execute->eval();
    check_cache_none();
    uint32_t branchtarget = jump_offset + armleocpu_execute->f2e_pc;
    uint32_t rd_expected_value = armleocpu_execute->f2e_pc + 4;
    cout << "Testing: " << "JAL" << ", "
        << hex << "branchtarget = "<< branchtarget << ", "
        << hex << "jump_offset = "<< jump_offset << ";" << endl;
    
    csr_check_none();
    check_e2f_branch(branchtarget);
    check_e2debug_none();
    rd_check(rd, rd_expected_value);
    
    next_cycle();
}


void test_load(uint32_t test, uint32_t rs1_val, uint32_t offset, uint32_t load_value, uint32_t load_type) {
    testnum = test;
    uint32_t rs1_a = 30;
    uint32_t rd_a = 29;
    armleocpu_execute->f2e_instr = 0b0000011 | (rd_a << 7) | (rs1_a << 15) | (load_type << 12) | (offset << 20);
    armleocpu_execute->rs1_data = rs1_val;
    armleocpu_execute->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_execute->eval();
    check(armleocpu_execute->c_cmd == CACHE_CMD_LOAD, "Error: c_cmd");
    check(armleocpu_execute->c_address == rs1_val + offset, "Error: c_address");
    check(armleocpu_execute->c_load_type == load_type, "Error: c_load_type");

    check(armleocpu_execute->rs1_addr == rs1_a, "Error: r1_addr");

    csr_check_none();
    check_e2f_not_ready();

    check_e2debug_none();
    
    rd_check_none();
    
    next_cycle();
    for(int i = 0; i < 10; ++i) {
        armleocpu_execute->c_response = CACHE_RESPONSE_WAIT;
        armleocpu_execute->eval();
        check(armleocpu_execute->c_cmd == CACHE_CMD_LOAD, "Error: c_cmd");
        check(armleocpu_execute->c_address == rs1_val + offset, "Error: c_address");
        check(armleocpu_execute->c_load_type == load_type, "Error: c_load_type");

        check(armleocpu_execute->rs1_addr == rs1_a, "Error: r1_addr");

        csr_check_none();
        check_e2f_not_ready();
        check_e2debug_none();
        rd_check_none();
        
        next_cycle();
    }

    armleocpu_execute->c_response = CACHE_RESPONSE_DONE;
    armleocpu_execute->c_load_data = load_value;
    armleocpu_execute->eval();
    check(armleocpu_execute->c_cmd == CACHE_CMD_NONE, "Error: c_cmd");
    
    check(armleocpu_execute->rs1_addr == rs1_a, "Error: r1_addr");

    csr_check_none();
    
    check_e2f_normal_op();
    check_e2debug_none();
    
    rd_check(rd_a, load_value);
    
    next_cycle();
}


void test_load_error(uint32_t test, uint32_t rs1_val, uint32_t offset, uint32_t load_type, uint32_t response, uint32_t csr_exc_cause_expected) {
    testnum = test;
    uint32_t rs1_a = 30;
    uint32_t rd_a = 29;
    armleocpu_execute->f2e_instr = 0b0000011 | (rd_a << 7) | (rs1_a << 15) | (load_type << 12) | (offset << 20);
    armleocpu_execute->rs1_data = rs1_val;
    armleocpu_execute->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_execute->eval();
    check(armleocpu_execute->c_cmd == CACHE_CMD_LOAD, "Error: c_cmd");
    check(armleocpu_execute->c_address == rs1_val + offset, "Error: c_address");
    check(armleocpu_execute->c_load_type == load_type, "Error: c_load_type");

    check(armleocpu_execute->rs1_addr == rs1_a, "Error: r1_addr");
    
    check_e2f_not_ready();
    check_e2debug_none();
    
    csr_check_none();
    
    rd_check_none();
    
    next_cycle();
    for(int i = 0; i < 10; ++i) {
        armleocpu_execute->c_response = CACHE_RESPONSE_WAIT;
        armleocpu_execute->eval();
        check(armleocpu_execute->c_cmd == CACHE_CMD_LOAD, "Error: c_cmd");
        check(armleocpu_execute->c_address == rs1_val + offset, "Error: c_address");
        check(armleocpu_execute->c_load_type == load_type, "Error: c_load_type");

        check(armleocpu_execute->rs1_addr == rs1_a, "Error: r1_addr");
        
        check_e2f_not_ready();
        check_e2debug_none();
        csr_check_none();
        
        rd_check_none();
        
        next_cycle();
    }

    uint32_t next_pc = 0x760;
    armleocpu_execute->c_response = response;
    armleocpu_execute->csr_next_pc = next_pc;
    armleocpu_execute->eval();
    check_cache_none();
    
    check(armleocpu_execute->rs1_addr == rs1_a, "Error: r1_addr");

    csr_check_int(csr_exc_cause_expected, armleocpu_execute->f2e_pc, MACHINE);

    check_e2f_interrupt(next_pc);
    check_e2debug_none();
    
    rd_check_none();
    
    next_cycle();
}


uint32_t STORE_BYTE = 0;
uint32_t STORE_HALF = 1;
uint32_t STORE_WORD = 2;
void test_store(uint32_t test, uint32_t rs1_val, uint32_t signed_offset, uint32_t rs2_val, uint32_t store_type) {
    testnum = test;
    uint32_t rs1_a = 29;
    uint32_t rs2_a = 30;
    uint32_t offset = signed_offset & 0xFFF;
    armleocpu_execute->f2e_instr = 0b0100011 | (rs1_a << 15) | (rs2_a << 20) | (store_type << 12) |
                ((offset & 0b11111) << 7) | (((offset >> 5) & 0b1111111) << 25);
    armleocpu_execute->rs1_data = rs1_val;
    armleocpu_execute->rs2_data = rs2_val;
    armleocpu_execute->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_execute->eval();
    check(armleocpu_execute->c_cmd == CACHE_CMD_STORE, "Error: c_cmd");
    check(armleocpu_execute->c_store_type == store_type, "Error: c_store_type");
    check(armleocpu_execute->c_store_data == rs2_val, "Error: c_store_data");
    check(armleocpu_execute->c_address == rs1_val + signed_offset, "Error: c_address");
    
    check(armleocpu_execute->rs1_addr == rs1_a, "Error: rs1_addr");
    check(armleocpu_execute->rs2_addr == rs2_a, "Error: rs2_addr");

    csr_check_none();
    check_e2f_not_ready();

    check_e2debug_none();
    
    rd_check_none();

    
    next_cycle();
    for(int i = 0; i < 10; ++i) {
        armleocpu_execute->c_response = CACHE_RESPONSE_WAIT;
        armleocpu_execute->eval();
        check(armleocpu_execute->c_cmd == CACHE_CMD_STORE, "Error: c_cmd");
        check(armleocpu_execute->c_address == rs1_val + signed_offset, "Error: c_address");
        check(armleocpu_execute->c_store_type == store_type, "Error: c_store_type");
        check(armleocpu_execute->c_store_data == rs2_val, "Error: c_store_data");

        check(armleocpu_execute->rs1_addr == rs1_a, "Error: rs1_addr");
        check(armleocpu_execute->rs2_addr == rs2_a, "Error: rs2_addr");

        csr_check_none();
        check_e2f_not_ready();
        check_e2debug_none();
        rd_check_none();
        
        next_cycle();
    }

    armleocpu_execute->c_response = CACHE_RESPONSE_DONE;
    armleocpu_execute->eval();
    check(armleocpu_execute->c_cmd == CACHE_CMD_NONE, "Error: c_cmd");
    
    check(armleocpu_execute->rs1_addr == rs1_a, "Error: rs1_addr");
    check(armleocpu_execute->rs2_addr == rs2_a, "Error: rs2_addr");

    csr_check_none();
    
    check_e2f_normal_op();
    check_e2debug_none();
    
    rd_check_none();
    
    next_cycle();
}


void test_store_error(uint32_t test, uint32_t rs1_val, uint32_t signed_offset, uint32_t rs2_val, uint32_t store_type, uint32_t response, uint32_t csr_exc_cause_expected) {
    testnum = test;
    uint32_t rs1_a = 29;
    uint32_t rs2_a = 30;
    uint32_t offset = signed_offset & 0xFFF;
    armleocpu_execute->f2e_instr = 0b0100011 | (rs1_a << 15) | (rs2_a << 20) | (store_type << 12) |
                ((offset & 0b11111) << 7) | (((offset >> 5) & 0b1111111) << 25);
    armleocpu_execute->rs1_data = rs1_val;
    armleocpu_execute->rs2_data = rs2_val;
    armleocpu_execute->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_execute->eval();
    check(armleocpu_execute->c_cmd == CACHE_CMD_STORE, "Error: c_cmd");
    check(armleocpu_execute->c_store_type == store_type, "Error: c_store_type");
    check(armleocpu_execute->c_store_data == rs2_val, "Error: c_store_data");
    check(armleocpu_execute->c_address == rs1_val + signed_offset, "Error: c_address");
    
    check(armleocpu_execute->rs1_addr == rs1_a, "Error: rs1_addr");
    check(armleocpu_execute->rs2_addr == rs2_a, "Error: rs2_addr");

    check_e2f_not_ready();
    check_e2debug_none();
    
    csr_check_none();
    
    rd_check_none();
    
    next_cycle();
    for(int i = 0; i < 10; ++i) {
        armleocpu_execute->c_response = CACHE_RESPONSE_WAIT;
        armleocpu_execute->eval();
        check(armleocpu_execute->c_cmd == CACHE_CMD_STORE, "Error: c_cmd");
        check(armleocpu_execute->c_address == rs1_val + signed_offset, "Error: c_address");
        check(armleocpu_execute->c_store_type == store_type, "Error: c_store_type");
        check(armleocpu_execute->c_store_data == rs2_val, "Error: c_store_data");

        check(armleocpu_execute->rs1_addr == rs1_a, "Error: rs1_addr");
        check(armleocpu_execute->rs2_addr == rs2_a, "Error: rs2_addr");

        check_e2f_not_ready();
        check_e2debug_none();
        csr_check_none();
        
        rd_check_none();
        
        next_cycle();
    }

    uint32_t next_pc = 0x864;

    armleocpu_execute->c_response = response;
    armleocpu_execute->csr_next_pc = next_pc;
    armleocpu_execute->eval();
    check(armleocpu_execute->c_cmd == CACHE_CMD_NONE, "Error: c_cmd");
    
    csr_check_int(csr_exc_cause_expected, armleocpu_execute->f2e_pc, MACHINE);

    check_e2f_interrupt(next_pc);
    check_e2debug_none();
    
    rd_check_none();
    
    next_cycle();
}


void test_fence(uint32_t test, uint32_t instr) {
    testnum = test;
    armleocpu_execute->f2e_instr = instr;
    armleocpu_execute->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_execute->eval();

    check(armleocpu_execute->c_cmd == CACHE_CMD_FLUSH_ALL, "Error: c_cmd, IDLE");
    check_e2f_not_ready();
    check_e2debug_none();
    csr_check_none();
    
    rd_check_none();
    next_cycle();

    armleocpu_execute->c_response = CACHE_RESPONSE_WAIT;
    armleocpu_execute->eval();
    check(armleocpu_execute->c_cmd == CACHE_CMD_FLUSH_ALL, "Error: c_cmd, WAIT");
    check_e2f_not_ready();
    check_e2debug_none();
    csr_check_none();
    rd_check_none();
    next_cycle();

    armleocpu_execute->c_response = CACHE_RESPONSE_DONE;
    armleocpu_execute->eval();
    check(armleocpu_execute->c_cmd == CACHE_CMD_NONE, "Error: c_cmd, DONE");
    
    check(armleocpu_execute->e2f_ready == 1, "Error: e2f_ready, DONE");
    check(armleocpu_execute->e2f_cmd == ARMLEOCPU_E2F_CMD_FLUSH, "Error: e2f_cmd is not CMD_FLUSH");
    
    check_e2debug_none();
    
    csr_check_none();
    
    rd_check_none();
    next_cycle();
}

void test_illegal(uint32_t test, uint32_t instr, uint32_t mode_from, uint32_t medeleg, uint32_t trap_to_mode) {
    testnum = test;
    armleocpu_execute->f2e_instr = instr;
    armleocpu_execute->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_execute->csr_mcurrent_privilege = mode_from;
    armleocpu_execute->csr_medeleg = medeleg;
    armleocpu_execute->csr_next_pc = 0xFA0;
    armleocpu_execute->eval();

    check_cache_none();
    check_e2debug_none();
    csr_check_int(2, armleocpu_execute->f2e_pc, trap_to_mode);
    check_e2f_interrupt(0xFA0);
    rd_check_none();
    next_cycle();
    
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

    // Construct the Verilated model, from Varmleocpu_execute.h generated from Verilating "armleocpu_execute.v"
    armleocpu_execute = new Varmleocpu_execute;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    armleocpu_execute->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");
    try {
    armleocpu_execute->rst_n = 0;
    armleocpu_execute->c_reset_done = 0;
    armleocpu_execute->f2e_exc_start = 0;
    armleocpu_execute->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_execute->f2e_instr = INSTR_NOP;
    armleocpu_execute->csr_mcurrent_privilege = MACHINE;
    next_cycle();
    armleocpu_execute->rst_n = 1;
    
    next_cycle();
    next_cycle();

    cout << "Starting execute tests" << endl;
    testnum = 0;
    armleocpu_execute->rst_n = 1;
    armleocpu_execute->c_reset_done = 1;
    armleocpu_execute->csr_mstatus_tvm = 0;
    armleocpu_execute->csr_mstatus_tw = 0;
    armleocpu_execute->c_response = CACHE_RESPONSE_IDLE;
    armleocpu_execute->c_load_data = 0;
    armleocpu_execute->f2e_exc_start = 0;
    armleocpu_execute->f2e_cause = 0;
    armleocpu_execute->csr_invalid = 0;
    armleocpu_execute->csr_readdata = 0xFFFFFFFF;
    armleocpu_execute->rs1_data = 0;
    armleocpu_execute->rs2_data = 0;

    armleocpu_execute->csr_medeleg = 0;
    
    next_cycle();


    cout << "Starting ALU Tests" << endl;

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
    // TODO: SLL, SLT, SLTU, XOR, SRL, SRA

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
        cout << "Testing load" << endl;
        // BYTE 0
        test_load(501, 100, 0x0F0, 0xFF00FF00, LOAD_BYTE);
        // BYTE 1
        test_load(502, 100, 0x0F1, 0xFF00FF00, LOAD_BYTE);
        // BYTE 2
        test_load(503, 100, 0x0F2, 0xFF00FF00, LOAD_BYTE);
        // BYTE 3
        test_load(504, 100, 0x0F3, 0xFF00FF00, LOAD_BYTE);

        // HALF WORD 0
        test_load(505, 100, 0x0F0, 0xFF00FF00, LOAD_HALF);
        test_load(506, 100, 0x0F0, 0xFF00FF00, LOAD_HALF_UNSIGNED);
        // HALF WORD 2
        test_load(505, 100, 0x0F2, 0xFF00FF00, LOAD_HALF);
        test_load(506, 100, 0x0F2, 0xFF00FF00, LOAD_HALF_UNSIGNED);
        
        // WORD
        test_load(505, 100, 0x0F0, 0xFF00FF00, LOAD_WORD);
    cout << "Testing load errors" << endl;
    // LOAD
        // MISSALIGNED
        test_load_error(510, 100, 100, LOAD_WORD, CACHE_RESPONSE_MISSALIGNED, 4);
        // PAGEFAULT
        test_load_error(511, 100, 100, LOAD_WORD, CACHE_RESPONSE_PAGEFAULT, 13);
        // ACCESSFAULT
        test_load_error(512, 100, 100, LOAD_WORD, CACHE_RESPONSE_ACCESSFAULT, 5);
    // STORE
        // BYTE 0
        test_store(601, 0xFF0, 0x0, 0xFF, STORE_BYTE);
        // BYTE 1
        test_store(602, 0xFF0, 0x1, 0xFF, STORE_BYTE);
        // BYTE 2
        test_store(603, 0xFF0, 0x2, 0xFF, STORE_BYTE);
        // BYTE 3
        test_store(604, 0xFF0, 0x3, 0xFF, STORE_BYTE);

        // HALF WORD 0
        test_store(605, 0xFF0, 0x0, 0xFFFF, STORE_HALF);
        // HALF WORD 2
        test_store(606, 0xFF0, 0x2, 0xFFFF, STORE_HALF);
        // WORD
        test_store(607, 0xFF0, 0x0, 0xFFFFFFFF, STORE_WORD);
    
    // STORE,
        // MISSALINED
        test_store_error(608, 0xFF0, 0x0, 0xFFFFFFFF, STORE_WORD, CACHE_RESPONSE_MISSALIGNED, 6);
        // PAGEFAULT
        test_store_error(609, 0xFF0, 0x0, 0xFFFFFFFF, STORE_WORD, CACHE_RESPONSE_PAGEFAULT, 15);
        // ACCESSFAULT
        test_store_error(610, 0xFF0, 0x0, 0xFFFFFFFF, STORE_WORD, CACHE_RESPONSE_ACCESSFAULT, 7);
    
    test_fence(701, 0b0001111); // FENCE
    test_fence(702, 0b0001111 | (1 << 12)); // FENCE.I
    test_fence(703, 0b1110011 | (0b0001001 << 25)); // SFENCE.VMA
    
    
    test_illegal(801, 0x0, MACHINE, /*medeleg=*/0x0, /*trap_to_mode=*/MACHINE);

    test_illegal(802, 0x0, MACHINE, /*medeleg=*/1 << 2, /*trap_to_mode=*/MACHINE);

    test_illegal(803, 0x0, SUPERVISOR, /*medeleg=*/0x0, /*trap_to_mode=*/MACHINE);

    test_illegal(804, 0x0, SUPERVISOR, /*medeleg=*/1 << 2, /*trap_to_mode=*/SUPERVISOR);
    
    test_illegal(805, 0x0, USER, /*medeleg=*/0x0, /*trap_to_mode=*/MACHINE);

    test_illegal(806, 0x0, USER, /*medeleg=*/1 << 2, /*trap_to_mode=*/SUPERVISOR);

    // TODO: Fetch exception
    // TODO: ECALL
        // from M, S, U
    // TODO: EBREAK
        // from M, S, U

    


    // TODO: CSR READ-WRITE
    // TODO: CSR SET-CLEAR
    // TODO: CSR INVALID
    // TODO: WFI (tw = 1, tw = 0)
    // TODO: MRET, SRET (tsr = 1, tsr = 0, suprvisor and machine modes)
    

    cout << "Execute Tests done" << endl;

    } catch(exception e) {
        cout << e.what() << endl;
        next_cycle();
        
    }
    armleocpu_execute->final();
    if (m_trace) {
        m_trace->close();
        m_trace = NULL;
    }
#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete armleocpu_execute; armleocpu_execute = NULL;

    // Fin
    exit(0);
}