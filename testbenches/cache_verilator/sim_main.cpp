#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Varmleocpu_cache.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Varmleocpu_cache* armleocpu_cache;


const int LOAD_BYTE = 0b000;
const int LOAD_BYTE_UNSIGNED = 0b100;

const int LOAD_HALF = 0b001;
const int LOAD_HALF_UNSIGNED = 0b101;

const int LOAD_WORD = 0b010;


const int STORE_WORD = 0b010;
const int STORE_HALF = 0b001;
const int STORE_BYTE = 0b000;

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


const int ARMLEOBUS_CMD_READ = 1;
const int ARMLEOBUS_CMD_WRITE = 2;

using namespace std;

const uint64_t MEMORY_WORDS = 16*1024*1024;

uint32_t k[MEMORY_WORDS*4];


double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}
void dump_step() {
    simulation_time++;
    if(trace) m_trace->dump(simulation_time);
}
void update() {
    armleocpu_cache->eval();
    dump_step();
}

void posedge() {
    armleocpu_cache->clk = 1;
    update();
    update();
}

void till_user_update() {
    armleocpu_cache->clk = 0;
    update();
}
void after_user_update() {
    update();
}

uint32_t make_address(uint32_t vtag, uint32_t lane, uint32_t offset, uint32_t inwordOffset) {
    // inword offset = 2
    // offset = 4
    // lane = 6
    // vtag = 32-6-4-2 = 20
    uint32_t address = vtag << 12;
    address |= lane << 6;
    address |= offset << 2;
    address |= inwordOffset;
    return address;
}

uint32_t mem[MEMORY_WORDS];

void memory_update() {
    static int counter = 0;
    static int currently_read = 0;
    armleocpu_cache->m_transaction_done = 0;
    armleocpu_cache->m_transaction_response = 1;
    armleocpu_cache->m_rdata = 0;
    uint64_t masked_address = armleocpu_cache->m_address & ~(1UL << 31);
    uint64_t shifted_address = masked_address >> 2;
    
    if(armleocpu_cache->m_transaction) {
        if((armleocpu_cache->m_cmd == ARMLEOBUS_CMD_READ) || (armleocpu_cache->m_cmd == ARMLEOBUS_CMD_WRITE)) {
            counter += 1;
            if(counter >= 2) {
                //
                if((armleocpu_cache->m_address & 1 << 20) && ((armleocpu_cache->m_address & 0b11) || (armleocpu_cache->m_wbyte_enable != 0xF))) {
                    cout << "[BUG] missaligned access??";
                    throw "[BUG] missaligned access??";
                } else if(shifted_address >= MEMORY_WORDS) {
                    armleocpu_cache->m_transaction_done = 1;
                    counter = 0;
                    std::cout << "access outside memory " << shifted_address << std::endl;
                } else if(armleocpu_cache->m_cmd == ARMLEOBUS_CMD_READ) {
                    armleocpu_cache->m_rdata = mem[shifted_address];
                    armleocpu_cache->m_transaction_done = 1;
                    armleocpu_cache->m_transaction_response = 0;
                    counter = 0;
                } else if(armleocpu_cache->m_cmd == ARMLEOBUS_CMD_WRITE) {
                    mem[shifted_address] = armleocpu_cache->m_wdata;
                    armleocpu_cache->m_transaction_done = 1;
                    armleocpu_cache->m_transaction_response = 0;
                    // TODO: m_wbyte_enable;
                    counter = 0;
                } else {
                    std::cout << "memory_update: !BUG!";
                    counter = 0;
                }
            }
        } else {
            counter = 0;
        }
    } else counter = 0;
    
}

void dummy_cycle() {
    after_user_update();

    posedge();
    till_user_update();
    memory_update();
    armleocpu_cache->eval();
}


void store(uint32_t address, uint32_t data, uint8_t type) {
    armleocpu_cache->c_cmd = CACHE_CMD_STORE;
    armleocpu_cache->c_address = address;
    armleocpu_cache->c_store_type = type;
    armleocpu_cache->c_store_data = data;
    int timeout = 0;
    do {
        dummy_cycle();
        timeout++;
    } while((armleocpu_cache->c_response == CACHE_RESPONSE_WAIT || armleocpu_cache->c_response == CACHE_RESPONSE_IDLE) && timeout != 1000);
    armleocpu_cache->c_cmd = CACHE_CMD_NONE;
    if(timeout == 1000)
        std::cout << "store timeout" << std::endl;
}

void load(uint32_t address, uint8_t type) {
    armleocpu_cache->c_cmd = CACHE_CMD_LOAD;
    armleocpu_cache->c_address = address;
    armleocpu_cache->c_load_type = type;
    int timeout = 0;
    do {
        dummy_cycle();
        timeout++;
    } while((armleocpu_cache->c_response == CACHE_RESPONSE_WAIT || armleocpu_cache->c_response == CACHE_RESPONSE_IDLE) && timeout != 1000);
    armleocpu_cache->c_cmd = CACHE_CMD_NONE;
    if(timeout == 1000)
        std::cout << "load timeout" << std::endl;
}

void execute(uint32_t address) {
    armleocpu_cache->c_cmd = CACHE_CMD_EXECUTE;
    armleocpu_cache->c_address = address;
    armleocpu_cache->c_load_type = LOAD_WORD;
    int timeout = 0;
    do {
        dummy_cycle();
        timeout++;
    } while((armleocpu_cache->c_response == CACHE_RESPONSE_WAIT || armleocpu_cache->c_response == CACHE_RESPONSE_IDLE) && timeout != 1000);
    armleocpu_cache->c_cmd = CACHE_CMD_NONE;
    if(timeout == 1000)
        std::cout << "execute timeout" << std::endl;
}

void flush() {
    armleocpu_cache->c_cmd = CACHE_CMD_FLUSH_ALL;
    int timeout = 0;
    do {
        dummy_cycle();
        timeout++;
    } while((armleocpu_cache->c_response == CACHE_RESPONSE_WAIT || armleocpu_cache->c_response == CACHE_RESPONSE_IDLE) && timeout != 16*1024);
    armleocpu_cache->c_cmd = CACHE_CMD_NONE;
    if(timeout == 16*1024)
        std::cout << "flush timeout" << std::endl;
}

void response_check(int response) {
    if(armleocpu_cache->c_response != response) {
        std::cout << "!ERROR! Cache response unexpected" << std::endl;
        throw runtime_error("!ERROR! Cache response unexpected");
    }
}
void load_data_check(uint32_t load_data) {
    if(armleocpu_cache->c_load_data != load_data) {
        std::cout << "!ERROR! Cache load data is invalid" << std::endl;
        std::cout << "Expected: 0x" << std::hex << load_data
                    << ", got: 0x" << armleocpu_cache->c_load_data << endl;
        throw runtime_error("!ERROR! Cache load data is invalid");
    }
}

void check_mem(uint32_t addr, uint32_t value) {
    if(mem[addr >> 2] != value) {
        std::cout << "!ERROR! Cache load data is invalid" << std::endl;
        std::cout << "Expected: 0x" << std::hex << value
        << ", got: 0x" << mem[addr >> 2] << endl;
        throw runtime_error("!ERROR! Memory valid invalid");
    }
}

void set_satp(uint8_t mode, uint32_t ppn) {
    armleocpu_cache->csr_satp_mode = mode;
    armleocpu_cache->csr_satp_ppn = ppn;
    flush();
    response_check(CACHE_RESPONSE_DONE);
}

void load_checked(uint32_t addr, uint8_t type, int response) {
    load(addr, type);
    response_check(response);
}

string testname;
int testnum;

void test_begin(int num, string tn) {
    testname = tn;
    testnum = num;
    cout << testnum << " - " << testname << endl;
}

void test_end() {
    dummy_cycle();
    cout << testnum << " - " << testname << " DONE" << endl;
}

uint8_t PTE_VALID      = 0b00000001;
uint8_t PTE_READ       = 0b00000010;
uint8_t PTE_WRITE      = 0b00000100;
uint8_t PTE_EXECUTE    = 0b00001000;
uint8_t PTE_USER       = 0b00010000;
uint8_t PTE_ACCESS     = 0b01000000;
uint8_t PTE_DIRTY      = 0b10000000;

uint8_t RWX = PTE_ACCESS | PTE_DIRTY | PTE_VALID | PTE_READ | PTE_WRITE | PTE_EXECUTE;

uint8_t POINTER = PTE_VALID;

/*
uint32_t construct_pte(uint32_t addr, uint8_t accessbits) {

}

uint32_t construct_megapage_leaf(uint32_t pa, uint8_t accessbits) {
    return (((pa >> 12) << 10) | accessbits);
}

uint32_t construct_missaligned_megapage_leaf(uint8_t accessbits) {
    return (1 << 10) | accessbits;
}
*/





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

    // Construct the Verilated model, from Varmleocpu_cache.h generated from Verilating "armleocpu_cache.v"
    armleocpu_cache = new Varmleocpu_cache;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    armleocpu_cache->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");

    armleocpu_cache->rst_n = 0;
    till_user_update();
    armleocpu_cache->rst_n = 0;
    armleocpu_cache->csr_satp_mode = 0;
    armleocpu_cache->csr_mcurrent_privilege = 3;
    armleocpu_cache->csr_mstatus_mprv = 0;
    
    after_user_update();
    posedge();
    till_user_update();
    armleocpu_cache->rst_n = 1;
    armleocpu_cache->c_cmd = CACHE_CMD_NONE;
    after_user_update();

    posedge();
    till_user_update();
    do {
        dummy_cycle();
    } while(!armleocpu_cache->c_reset_done);
    // Wait for reset done
    after_user_update();

/*
    Phys access / virt access
        Megapage / Leaf page
            Bypass / Cached
                Store Word
                Load Word
                Execute Word

                Half Store for 2 cases
                Half Load for 2 cases

                Byte Store for 4 cases
                Byte Load for 4 cases

                Store Word missaligned for 4 cases
                Load Word missaligned for 4 cases
                Execute Word missaligned for 4 cases

                Store Half missaligned for 2 cases
                Load Half missaligned for 2 cases

                !!Byte cannot be missaligned

                Unknown access for all store cases
                Unknown access for all Load cases

                if virt
                    PTW Access fault
                    Refill Access fault
                    Flush access fault
                    Flush_all Access Fault

                    PTW Megapage Pagefault
                    PTW Leaf Pagefault
                    Cache memory pagefault for each case (read, write, execute, access, dirty, user) for megaleaf and leaf


    Generate random access pattern using GLFSR, check for validity
    */

    try {
    posedge();
    till_user_update();
    mem[1] = 1000;
    mem[2] = 0xDEADBEEFUL;
    armleocpu_cache->c_cmd = CACHE_CMD_LOAD;
    armleocpu_cache->c_load_type = 2;
    armleocpu_cache->c_address = 4;
    armleocpu_cache->c_store_type = 2;
    cout << "Basic load test" << endl;
    load(4, LOAD_WORD);
    load_data_check(1000);
    response_check(CACHE_RESPONSE_DONE);
    
    
    load(8, LOAD_WORD);
    load_data_check(0xDEADBEEFUL);
    response_check(CACHE_RESPONSE_DONE);
    dummy_cycle();
    cout << "Basic load test done" << endl;


    dummy_cycle();
    cout << "Basic flush and refill test done" << endl;

    cout << "Unknown type load" << endl;

    load(8, 0b11);
    response_check(CACHE_RESPONSE_UNKNOWNTYPE);
    load(8, 0b110);
    response_check(CACHE_RESPONSE_UNKNOWNTYPE);
    load(8, 0b111);
    response_check(CACHE_RESPONSE_UNKNOWNTYPE);
    store(8, 0xFF00FF00, 0b11);
    response_check(CACHE_RESPONSE_UNKNOWNTYPE);
    dummy_cycle();
    cout << "Unknown type load done" << endl;
    

    cout << "Missaligned type load" << endl;
    load(8 + 1, LOAD_WORD);
    response_check(CACHE_RESPONSE_MISSALIGNED);
    load(8 + 2, LOAD_WORD);
    response_check(CACHE_RESPONSE_MISSALIGNED);
    load(8 + 3, LOAD_WORD);
    response_check(CACHE_RESPONSE_MISSALIGNED);

    load(8 + 1, LOAD_HALF);
    response_check(CACHE_RESPONSE_MISSALIGNED);
    load(8 + 3, LOAD_HALF);
    response_check(CACHE_RESPONSE_MISSALIGNED);

    load(8 + 1, LOAD_HALF_UNSIGNED);
    response_check(CACHE_RESPONSE_MISSALIGNED);
    load(8 + 3, LOAD_HALF_UNSIGNED);
    response_check(CACHE_RESPONSE_MISSALIGNED);
    

    dummy_cycle();
    cout << "Missaligned type load done" << endl;

    cout << "Basic store check" << endl;
    store(8, 0xFF55FF55, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    store(12, 0x66552211, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    store(16, 0x66552223, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    


    load(8, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    load_data_check(0xFF55FF55);

    load(12, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    load_data_check(0x66552211);

    load(16, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    load_data_check(0x66552223);
    dummy_cycle();
    cout << "Basic store check done" << endl;

    cout << "Access outside memory -> accessfault" << endl;
    // access fault store:
        store(MEMORY_WORDS*4, 0, STORE_WORD);
        response_check(CACHE_RESPONSE_ACCESSFAULT);
    // access fault load:
        load(MEMORY_WORDS*4, LOAD_WORD);
        response_check(CACHE_RESPONSE_ACCESSFAULT);
        dummy_cycle();
    cout << "Outside memory access test done" << endl;
    
    
    
    cout << "flush test" << endl;
    
    store(make_address(4, 4, 0b0010, 0b00), 0xFFFFFFFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    store(make_address(4, 4, 0b0011, 0b00), 0xFFFFFFFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    store(make_address(4, 4, 0b0100, 0b00), 0xFFFFFFFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    std::cout << "before flush" << std::endl;
    check_mem(8, 0xDEADBEEF);
    check_mem(12, 0);
    check_mem(16, 0);
    check_mem(make_address(4, 4, 0b0010, 0b00), 0);
    check_mem(make_address(4, 4, 0b0011, 0b00), 0);
    check_mem(make_address(4, 4, 0b0100, 0b00), 0);
    flush();
    std::cout << "after flush" << std::endl;
    check_mem(8, 0xFF55FF55);
    check_mem(12, 0x66552211);
    check_mem(16, 0x66552223);
    check_mem(make_address(4, 4, 0b0010, 0b00), 0xFFFFFFFF);
    check_mem(make_address(4, 4, 0b0011, 0b00), 0xFFFFFFFF);
    check_mem(make_address(4, 4, 0b0100, 0b00), 0xFFFFFFFF);
    
    
    cout << "Basic flush and refill test" << endl;
    
    for(int i = 1; i < 127; i++) {
        store(make_address((i >> 3) & 0b1111, (i >> 2) % 2, 1, i & 0b11), i % 256, STORE_BYTE);
        response_check(CACHE_RESPONSE_DONE);
    }
    for(int i = 1; i < 127; i++) {
        load(make_address((i >> 3) & 0b1111, (i >> 2) % 2, 1, i & 0b11), LOAD_BYTE_UNSIGNED);
        response_check(CACHE_RESPONSE_DONE);
        load_data_check(i % 256);
    }
    dummy_cycle();
    cout << "Basic flush and refill test done" << endl;
    



    cout << "Basic flush and refill test with flush" << endl;
    

    for(int i = 0; i < 256; i++) {
        uint32_t addr = make_address(i >> 4, (i % 16), 1, 0);
        uint32_t val = (addr);
        k[addr] = val;
        store(addr, val, STORE_WORD);
        response_check(CACHE_RESPONSE_DONE);
    }
    cout << "Flushing" << endl;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    for(int i = 0; i < 256; i++) {
        uint32_t addr = make_address(i >> 4, (i % 16), 1, 0);
        uint32_t val = (addr);
        if(k[addr] != val) {
            cout << "Unexpected value";
            return -1;
        }
        load(addr, LOAD_WORD);
        response_check(CACHE_RESPONSE_DONE);
        load_data_check(val);
    }
    dummy_cycle();
    cout << "Basic flush and refill test with flush done" << endl;
    

    cout << "Begin bypass tests" << endl;
    // for bypass test we need to flush memory
    flush();

    test_begin(100, "Cache bypass test");
    load((1 << 31) + 16UL, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    load_data_check(0x66552223);
    //dummy_cycle();

    store((1 << 31) + 16UL, 0xAABBCCDD, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    //dummy_cycle();

    execute((1 << 31) + 16UL);
    response_check(CACHE_RESPONSE_DONE);
    load_data_check(0xAABBCCDD);


    // out of memory
    execute((1UL << 31) + MEMORY_WORDS*4);
    response_check(CACHE_RESPONSE_ACCESSFAULT);
    load((1UL << 31) + MEMORY_WORDS*4, LOAD_WORD);
    response_check(CACHE_RESPONSE_ACCESSFAULT);
    store((1UL << 31) + MEMORY_WORDS*4, 0xAABBCCDD, STORE_WORD);
    response_check(CACHE_RESPONSE_ACCESSFAULT);

    //dummy_cycle();
    test_end();
    cout << "Done bypass tests" << endl;
    

    cout << "Begin MMU Tests" << endl;

    armleocpu_cache->csr_satp_mode = 1;
    armleocpu_cache->csr_satp_ppn = 4;
    armleocpu_cache->csr_mcurrent_privilege = 3;
    armleocpu_cache->csr_mstatus_mprv = 0;
    armleocpu_cache->csr_mstatus_mxr = 0;
    armleocpu_cache->csr_mstatus_sum = 0;
    armleocpu_cache->csr_mstatus_mpp = 0;
    // 4 << 12 to get mmu table base in bytes
    
/*

    mem[4 << 10] = construct_missaligned_megapage_leaf(RWX);
    mem[4 << 10 + 1] = construct_megapage_leaf(vpn, RWX);

    mem[4 << 10 + 2] = construct_megapage_pointer(5);

    mem[5 << 10] = construct_leaf(0, 1, );
    mem[5 << 10] = construct_leaf(0, 1, RWX);
    mem[5 << 10] = construct_leaf(0, 1, RWX);
*/
    mem[4 << 10] = RWX;
    mem[(4 << 10) + 1] = RWX | PTE_USER;
    mem[(4 << 10) + 2] = MEMORY_WORDS | (RWX); // pointer to out of memory

    mem[(4 << 10) + 3] = (5 << 10) | POINTER;
    mem[(4 << 10) + 4] = (1 << 10) | (RWX); // missaligned pointer

    mem[(5 << 10)] = (5 << 10) | POINTER;



    armleocpu_cache->csr_mcurrent_privilege = 3;
    armleocpu_cache->csr_mstatus_mprv = 0;
    set_satp(1, 4);


    test_begin(1, "Testing MMU satp should not apply to machine");
    load_checked(0, LOAD_WORD, CACHE_RESPONSE_DONE);
    test_end();
    
    

    test_begin(3, "Testing MMU satp should apply to machine with mprv (pp = supervisor, user)");
    armleocpu_cache->csr_mcurrent_privilege = 3;
    armleocpu_cache->csr_mstatus_mprv = 1;
    armleocpu_cache->csr_mstatus_mpp = 1;
    load_checked(0, LOAD_WORD, CACHE_RESPONSE_DONE);
    dummy_cycle();
    armleocpu_cache->csr_mstatus_mpp = 0;
    load_checked(0, LOAD_WORD, CACHE_RESPONSE_PAGEFAULT);
    test_end();

    test_begin(4, "Testing MMU satp should apply to supervisor");
    armleocpu_cache->csr_mcurrent_privilege = 1;
    load_checked(0, LOAD_WORD, CACHE_RESPONSE_DONE);
    test_end();

    test_begin(5, "Testing MMU satp should apply to user");
    armleocpu_cache->csr_mcurrent_privilege = 0;
    load(0, LOAD_WORD);
    load_checked(0, LOAD_WORD, CACHE_RESPONSE_PAGEFAULT);
    test_end();

    test_begin(6, "User can access user memory");
    armleocpu_cache->csr_mcurrent_privilege = 0;
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_DONE);
    test_end();
    
    test_begin(7, "Supervisor can't access user memory");
    armleocpu_cache->csr_mstatus_sum = 0;
    armleocpu_cache->csr_mcurrent_privilege = 1;
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_PAGEFAULT);
    test_end();


    test_begin(8, "Supervisor can access user memory with sum=1");
    armleocpu_cache->csr_mcurrent_privilege = 1;
    armleocpu_cache->csr_mstatus_sum = 1;
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_DONE);
    dummy_cycle();
    armleocpu_cache->csr_mcurrent_privilege = 3;
    armleocpu_cache->csr_mstatus_mprv = 1;
    armleocpu_cache->csr_mstatus_mpp = 1;
    armleocpu_cache->csr_mstatus_sum = 1;
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_DONE);
    test_end();
    
    test_begin(9, "PTW Access out of memory");
    set_satp(1, MEMORY_WORDS*4 >> 12);
    
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_ACCESSFAULT);
    test_end();
    


    test_begin(10, "PTW Access 4k leaf out of memory");
    set_satp(1, 4);
    load(2 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_ACCESSFAULT);
    dummy_cycle();
    cout << "10 - PTW Access 4k leaf out of memory done" << endl;
    

    
    test_begin(11, "PTW Access 3 level leaf pagefault");
    set_satp(1, 4);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();
    
    test_begin(12, "Test leaf readable");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_READ;
    set_satp(1, 4);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    test_end();


    test_begin(13, "Test leaf unreadable");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_EXECUTE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();

    test_begin(14, "Test leaf unreadable, execute, mxr");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_EXECUTE;
    armleocpu_cache->csr_mstatus_mxr = 1;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    test_end();

    test_begin(15, "Test leaf access bit");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_DIRTY | PTE_ACCESS | PTE_READ | PTE_EXECUTE | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);

    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_DIRTY | PTE_READ | PTE_EXECUTE | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    dummy_cycle();
    test_end();

    
    test_begin(16, "Test leaf dirty bit");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_DIRTY | PTE_ACCESS | PTE_READ | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);

    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_READ | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();
    
    
    // Test writable bit
    test_begin(17, "Test leaf write bit");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);

    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();

    // Test executable bit
    test_begin(17, "Test leaf executable bit");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ | PTE_EXECUTE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_DONE);

    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();

    test_begin(18, "Test invalid pte");
    mem[(5 << 10)] = (100 << 10) | PTE_ACCESS | PTE_DIRTY | PTE_READ | PTE_EXECUTE | PTE_WRITE;
    flush();
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();


    test_begin(18, "Test Missaligned pte");
    //mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ | PTE_EXECUTE | PTE_WRITE;
    flush();
    load(4 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    execute(4 << 22);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    store(4 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();

    /*
    cout << "17 - Test Megapage" << endl;
    mem[(4 << 10) + 1] = ;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    store(1 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    dummy_cycle();
    cout << "17 - Test Megapage" << endl;
    */

    /*
        Test cases:
            1 - Machine, try to access physical memory
            2 - Machine, satp=1, try to access physical memory
            3 - Supervisor, satp=0, try to access physical memory
            
            4 - megapage missaligned -> pagefault
            5 - megapage invalid -> pagefault
            6 - page invalid -> pagefault

            7 - User try to access supervisor -> pagefault
            8 - Supervisor try to access user sum = 0 -> pagefault
            9 - Supervisor try to access user sum = 1 -> success

            Supervisor, satp=1
            page leaf, megapage leaf:
                Execute:
                    pagefault:
                        access, no execute
                        executable, no access
                    done:
                        executable, access
                Load:
                    pagefault:
                        no read, access
                        read, no access
                        mxr, execute, no access
                        mxr, no execute, access
                    done:
                        read, access
                        mxr, execute, access
                        mxr, read, access
                Store:
                    pagefault:
                        no store, access, dirty
                        store, dirty, no access
                        store, access, no dirty
                    done:
                        store, access, dirty
    */

    cout << "MMU Tests done" << endl;

    } catch(exception e) {
        cout << e.what();
        dummy_cycle();
        dummy_cycle();
        
    }
    armleocpu_cache->final();
    if (m_trace) {
        m_trace->close();
        m_trace = NULL;
    }
#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete armleocpu_cache; armleocpu_cache = NULL;

    // Fin
    exit(0);
}