#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Varmleocpu.h>
#include <iostream>
#include <fstream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;
Varmleocpu* armleocpu;


const int ARMLEOBUS_CMD_READ = 1;
const int ARMLEOBUS_CMD_WRITE = 2;

using namespace std;

const uint64_t MEMORY_WORDS = 16*1024*1024;


double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}
void dump_step() {
    simulation_time++;
    if(trace) m_trace->dump(simulation_time);
}
void update() {
    armleocpu->eval();
    dump_step();
}

void posedge() {
    armleocpu->clk = 1;
    update();
    update();
}

void till_user_update() {
    armleocpu->clk = 0;
    update();
}
void after_user_update() {
    update();
}

uint32_t mem[MEMORY_WORDS];

void memory_update() {
    static int d_counter = 0;
    armleocpu->d_transaction_done = 0;
    armleocpu->d_transaction_response = 1;
    armleocpu->d_rdata = 0;
    uint64_t d_masked_address = armleocpu->d_address & ~(1UL << 31);
    uint64_t d_shifted_address = d_masked_address >> 2;
    /*cout << "d: ";
    cout << (int)armleocpu->d_transaction << " ";
    cout << (int)armleocpu->d_cmd << " ";
    cout << (int)d_counter << endl;*/
    if(armleocpu->d_transaction) {
        if((armleocpu->d_cmd == ARMLEOBUS_CMD_READ) || (armleocpu->d_cmd == ARMLEOBUS_CMD_WRITE)) {
            d_counter += 1;
            if(d_counter >= 2) {
                //
                if((armleocpu->d_address & 1 << 20) && ((armleocpu->d_address & 0b11) || (armleocpu->d_wbyte_enable != 0xF))) {
                    cout << "[BUG] missaligned access??";
                    throw "[BUG] missaligned access??";
                } else if(d_shifted_address >= MEMORY_WORDS) {
                    armleocpu->d_transaction_done = 1;
                    d_counter = 0;
                    std::cout << "access outside memory " << d_shifted_address << std::endl;
                } else if(armleocpu->d_cmd == ARMLEOBUS_CMD_READ) {
                    armleocpu->d_rdata = mem[d_shifted_address];
                    armleocpu->d_transaction_done = 1;
                    armleocpu->d_transaction_response = 0;
                    d_counter = 0;
                } else if(armleocpu->d_cmd == ARMLEOBUS_CMD_WRITE) {
                    mem[d_shifted_address] = armleocpu->d_wdata;
                    armleocpu->d_transaction_done = 1;
                    armleocpu->d_transaction_response = 0;
                    // TODO: d_wbyte_enable;
                    d_counter = 0;
                } else {
                    std::cout << "memory_update: !BUG!";
                    d_counter = 0;
                }
            }
        } else {
            d_counter = 0;
        }
    } else d_counter = 0;

    static int i_counter = 0;
    armleocpu->i_transaction_done = 0;
    armleocpu->i_transaction_response = 1;
    armleocpu->i_rdata = 0;
    uint64_t i_masked_address = armleocpu->i_address & ~(1UL << 31);
    uint64_t i_shifted_address = i_masked_address >> 2;
    /*cout << "i: ";
    cout << (int)armleocpu->i_transaction << " ";
    cout << (int)armleocpu->i_cmd << " ";
    cout << (int)i_counter << endl;*/
    if(armleocpu->i_transaction) {
        if((armleocpu->i_cmd == ARMLEOBUS_CMD_READ) || (armleocpu->i_cmd == ARMLEOBUS_CMD_WRITE)) {
            cout << "counter+1" << endl;
            i_counter += 1;
            if(i_counter >= 2) {
                //
                if((armleocpu->i_address & 1 << 20) && ((armleocpu->i_address & 0b11) || (armleocpu->i_wbyte_enable != 0xF))) {
                    cout << "[BUG] missaligned access??";
                    throw "[BUG] missaligned access??";
                } else if(i_shifted_address >= MEMORY_WORDS) {
                    armleocpu->i_transaction_done = 1;
                    i_counter = 0;
                    std::cout << "access outside memory " << i_shifted_address << std::endl;
                } else if(armleocpu->i_cmd == ARMLEOBUS_CMD_READ) {
                    armleocpu->i_rdata = mem[i_shifted_address];
                    armleocpu->i_transaction_done = 1;
                    armleocpu->i_transaction_response = 0;
                    i_counter = 0;
                    
                } else if(armleocpu->i_cmd == ARMLEOBUS_CMD_WRITE) {
                    mem[i_shifted_address] = armleocpu->i_wdata;
                    armleocpu->i_transaction_done = 1;
                    armleocpu->i_transaction_response = 0;
                    // TODO: i_wbyte_enable;
                    i_counter = 0;
                } else {
                    std::cout << "memory_update: !BUG!";
                    i_counter = 0;
                }
            }
        } else {
            i_counter = 0;
        }
    } else i_counter = 0;
    
}

void dummy_cycle() {
    after_user_update();

    posedge();
    till_user_update();
    
    memory_update();
}

void load_binary(const char * file) {
    memset((void*)mem, 0, sizeof(mem));
    ifstream myData(file, ios::binary);
    myData.read((char*)mem, sizeof(mem));
}

void test(const char * tfile) {
    cout << "Test: " << tfile << endl;
    armleocpu->rst_n = 0;
    dummy_cycle();
    mem[0] = 0xFFFFFFFF;
    load_binary(tfile);
    armleocpu->rst_n = 1;
    for(int i = 0; i < 2000 && !armleocpu->armleocpu__DOT__e2debug_machine_ebreak; i++)
        dummy_cycle();
    dummy_cycle();
    if(mem[0] != 0xD01E4A55) {
        cout << "Test: " << tfile << " not passed" << endl;
        throw "Test not passed";
    } else {
        cerr << "Test: " << tfile << " passed" << endl << flush;
    }
}

int main(int argc, char** argv, char** env) {
    cout << "Test started" << endl;
    // This is a more complicated example, please also see the simpler examples/make_hello_c.

    // Prevent unused variable warnings
    if (0 && argc && argv && env) {}

    for (int i = 0; i < argc; ++i) {
        std::cout << argv[i] << std::endl;
    }

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

    // Construct the Verilated model, from Varmleocpu.h generated from Verilating "armleocpu.v"
    armleocpu = new Varmleocpu;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    armleocpu->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");

    armleocpu->rst_n = 0;
    armleocpu->clk = 0;
    till_user_update();
    armleocpu->rst_n = 0;
    armleocpu->dbg_request = 0;
    armleocpu->dbg_cmd = 0;
    armleocpu->irq_timer_i = 1;
    armleocpu->irq_exti_i = 1;
    armleocpu->irq_swi_i = 1;
    after_user_update();
    posedge();
    till_user_update();
    armleocpu->rst_n = 1;
    
    
    // 1, R1 = 0xD011E4A55
    // 2, R1 = ...
    // STORE 0, r1
    // EBREAK
    //mem[0x2000 >> 2] = ;
    //mem[(0x2000 >> 2) + 2] = 0b00000000000100000000000001110011;
    try {
        
        
        //test("../../verif_isa_tests/output/illegal.bin");
        //test("../../verif_isa_tests/output/ma_addr.bin");
        test("../../verif_isa_tests/output/ma_fetch.bin");

    } catch(exception e) {
        cout << e.what();
        dummy_cycle();
        dummy_cycle();
        
    }
    armleocpu->final();
    if (m_trace) {
        m_trace->close();
        m_trace = NULL;
    }
#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete armleocpu; armleocpu = NULL;

    // Fin
    exit(0);
}