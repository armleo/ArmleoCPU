#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Vcorevx_cache.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Vcorevx_cache* corevx_cache;


const int CACHE_CMD_NONE = 0;
const int CACHE_CMD_EXECUTE = 2;
const int CACHE_CMD_LOAD = 2;
const int CACHE_CMD_STORE = 3;

const int CACHE_RESPONSE_IDLE = 0;
const int CACHE_RESPONSE_WAIT = 1;
const int CACHE_RESPONSE_DONE = 2;

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
            if(counter == 3) {
                if(shifted_address > 16*1024*1024) {
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
    posedge();
    till_user_update();
    memory_update();
    after_user_update();
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
    
    update();
    posedge();
    till_user_update();
    corevx_cache->rst_n = 1;
    corevx_cache->c_cmd = CACHE_CMD_NONE;
    update();
    posedge();
    till_user_update();
    after_user_update();
    do {
        dummy_cycle();
    } while(!corevx_cache->c_reset_done);
    // Wait for reset done




    posedge();
    till_user_update();
    mem[1] = 1000;
    corevx_cache->c_cmd = CACHE_CMD_LOAD;
    corevx_cache->c_load_type = 2;
    corevx_cache->c_address = 4;
    corevx_cache->c_store_type = 2;
    corevx_cache->c_store_data;

    after_user_update();
    int timeout = 0;
    while(corevx_cache->c_response != CACHE_RESPONSE_DONE && timeout != 1000) {
        dummy_cycle();
        timeout++;
    }
    
    std::cout << corevx_cache->c_load_data << std::endl;
    
/*
    // Simulate until $finish
    while ((!Verilated::gotFinish()) && simulation_time < 1000) {
        corevx_cache->clk = 0;
        if (!corevx_cache->clk) {
            // run negedge
            
            corevx_cache->eval();
            // validate values
            simulation_time++;  // Time passes...
            if(trace) m_trace->dump(simulation_time);
            // set values
            if(simulation_time > 6)
                corevx_cache->rst_n = 1;
            corevx_cache->eval();
            simulation_time++;  // Time passes...
            if(trace) m_trace->dump(simulation_time);
        }

        // Toggle a fast (time/2 period) clock
        corevx_cache->clk = 1;
        corevx_cache->eval();
        simulation_time++;  // Time passes...
        if(trace) m_trace->dump(simulation_time);
        simulation_time++;
        if(trace) m_trace->dump(simulation_time);
        std::cout << simulation_time << std::endl;
    }
    */
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