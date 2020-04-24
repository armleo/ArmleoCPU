#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Vcorevx_cache.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Vcorevx_cache* corevx_cache;

const int LOAD_WORD = 2;
const int STORE_WORD = 2;

const int CACHE_CMD_NONE = 0;
const int CACHE_CMD_EXECUTE = 1;
const int CACHE_CMD_LOAD = 2;
const int CACHE_CMD_STORE = 3;
const int CACHE_CMD_FLUSH_ALL = 4;

const int CACHE_RESPONSE_IDLE = 0;
const int CACHE_RESPONSE_WAIT = 1;
const int CACHE_RESPONSE_DONE = 2;
const int CACHE_RESPONSE_ACCESSFAULT = 3;
const int CACHE_RESPONSE_UNKNOWNTYPE = 6;


const int ARMLEOBUS_CMD_READ = 1;
const int ARMLEOBUS_CMD_WRITE = 2;

double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}
void dump_step() {
    simulation_time++;
    if(trace) m_trace->dump(simulation_time);
}
void update() {
    corevx_cache->eval();
    dump_step();
}

void posedge() {
    corevx_cache->clk = 1;
    update();
    update();
}

void till_user_update() {
    corevx_cache->clk = 0;
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

uint32_t mem[16*1024*1024];

void memory_update() {
    static int counter = 0;
    static int currently_read = 0;
    corevx_cache->m_transaction_done = 0;
    corevx_cache->m_transaction_response = 1;
    corevx_cache->m_rdata = 0;
    uint64_t masked_address = corevx_cache->m_address & ~(1UL << 31);
    uint64_t shifted_address = masked_address >> 2;
    
    if(corevx_cache->m_transaction) {
        if(corevx_cache->m_cmd == ARMLEOBUS_CMD_READ || corevx_cache->m_cmd == ARMLEOBUS_CMD_WRITE) {
            counter += 1;
            if(counter == 2) {
                //std::cout << shifted_address << std::endl;
                if(shifted_address >= 16*1024*1024) {
                    corevx_cache->m_transaction_done = 1;
                    counter = 0;
                } else if(corevx_cache->m_cmd == ARMLEOBUS_CMD_READ) {
                    corevx_cache->m_rdata = mem[shifted_address];
                    corevx_cache->m_transaction_done = 1;
                    corevx_cache->m_transaction_response = 0;
                    counter = 0;
                } else if(corevx_cache->m_cmd == ARMLEOBUS_CMD_WRITE) {
                    mem[shifted_address] = corevx_cache->m_wdata;
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
    
}


void store(uint32_t address, uint32_t data, uint8_t type) {
    corevx_cache->c_cmd = CACHE_CMD_STORE;
    corevx_cache->c_address = address;
    corevx_cache->c_store_type = type;
    corevx_cache->c_store_data = data;
    int timeout = 0;
    do {
        dummy_cycle();
        timeout++;
    } while((corevx_cache->c_response == CACHE_RESPONSE_WAIT || corevx_cache->c_response == CACHE_RESPONSE_IDLE) && timeout != 1000);
    if(timeout == 1000)
        std::cout << "store timeout" << std::endl;
}

void load(uint32_t address, uint8_t type) {
    corevx_cache->c_cmd = CACHE_CMD_LOAD;
    corevx_cache->c_address = address;
    corevx_cache->c_load_type = type;
    int timeout = 0;
    do {
        dummy_cycle();
        timeout++;
    } while((corevx_cache->c_response == CACHE_RESPONSE_WAIT || corevx_cache->c_response == CACHE_RESPONSE_IDLE) && timeout != 1000);
    if(timeout == 1000)
        std::cout << "load timeout" << std::endl;
}

void flush() {
    corevx_cache->c_cmd = CACHE_CMD_FLUSH_ALL;
    int timeout = 0;
    do {
        dummy_cycle();
        timeout++;
    } while((corevx_cache->c_response == CACHE_RESPONSE_WAIT || corevx_cache->c_response == CACHE_RESPONSE_IDLE) && timeout != 1000);
    if(timeout == 10)
        std::cout << "flush timeout" << std::endl;
}

void response_check(int response) {
    if(corevx_cache->c_response != response) {
        std::cout << "!ERROR! Cache response unexpected" << std::endl;
        throw "!ERROR! Cache response unexpected";
    }
}
void load_data_check(uint32_t load_data) {
    if(corevx_cache->c_load_data != load_data) {
        std::cout << "!ERROR! Cache load data is invalid" << std::endl;
        throw "!ERROR! Cache load data is invalid";
    }
}


int main(int argc, char** argv, char** env) {
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

    // Construct the Verilated model, from Vcorevx_cache.h generated from Verilating "corevx_cache.v"
    corevx_cache = new Vcorevx_cache;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    corevx_cache->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");

    corevx_cache->rst_n = 0;
    till_user_update();
    corevx_cache->rst_n = 0;
    corevx_cache->csr_satp_mode = 0;
    corevx_cache->csr_mcurrent_privilege = 3;
    corevx_cache->csr_mstatus_mprv = 0;
    
    after_user_update();
    posedge();
    till_user_update();
    corevx_cache->rst_n = 1;
    corevx_cache->c_cmd = CACHE_CMD_NONE;
    after_user_update();

    posedge();
    till_user_update();
    do {
        dummy_cycle();
    } while(!corevx_cache->c_reset_done);
    // Wait for reset done
    after_user_update();


    posedge();
    till_user_update();
    mem[1] = 1000;
    mem[2] = 0xDEADBEEFUL;
    corevx_cache->c_cmd = CACHE_CMD_LOAD;
    corevx_cache->c_load_type = 2;
    corevx_cache->c_address = 4;
    corevx_cache->c_store_type = 2;
    load(4, LOAD_WORD);
    load_data_check(1000);
    response_check(CACHE_RESPONSE_DONE);
    
    
    load(8, LOAD_WORD);
    load_data_check(0xDEADBEEFUL);
    response_check(CACHE_RESPONSE_DONE);
    
    
    store(make_address(1, 1, 1, 0), 1, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    store(make_address(2, 1, 1, 0), 2, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    store(make_address(3, 1, 1, 0), 3, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    store(make_address(4, 1, 1, 0), 4, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);
    store(make_address(5, 1, 1, 0), 5, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);

    load(8, 0b11);
    response_check(CACHE_RESPONSE_UNKNOWNTYPE);
    load(8, 0b110);
    response_check(CACHE_RESPONSE_UNKNOWNTYPE);
    load(8, 0b111);
    response_check(CACHE_RESPONSE_UNKNOWNTYPE);
    
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
    
    // access fault store:
        store(16*1024*1024*4, 0, STORE_WORD);
        response_check(CACHE_RESPONSE_ACCESSFAULT);
    // access fault load:
        load(16*1024*1024*4, LOAD_WORD);
        response_check(CACHE_RESPONSE_ACCESSFAULT);
    flush();
    std::cout << "after flush" << std::endl;
    std::cout << std::hex << mem[2] << std::endl;
    std::cout << std::hex << mem[3] << std::endl;
    std::cout << std::hex << mem[4] << std::endl;

    corevx_cache->final();
    if (m_trace) {
        m_trace->close();
        m_trace = NULL;
    }
#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete corevx_cache; corevx_cache = NULL;

    // Fin
    exit(0);
}