#include <verilated.h>
#include <verilated_vcd_c.h>
#include <Vcorevx_cache.h>
#include <iostream>

vluint64_t simulation_time = 0;
VerilatedVcdC	*m_trace;
bool trace = 1;



double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
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
    Vcorevx_cache* corevx_cache = new Vcorevx_cache;  // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    m_trace = new VerilatedVcdC;
    corevx_cache->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");

    corevx_cache->rst_n = 0;

    // Set some inputs
    corevx_cache->clk = 0;
    

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
        std::cout << simulation_time << std::endl;
    }

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