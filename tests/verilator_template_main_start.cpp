
int main(int argc, char** argv, char** env) {


    // Prevent unused variable warnings
    if (0 && argc && argv && env) {}

    // Set debug level, 0 is off, 9 is highest presently used
    // May be overridden by commandArgs
    Verilated::debug(0);

    // Randomization reset policy
    // May be overridden by commandArgs
    Verilated::randReset(2);

    // Verilator must compute traced signals
    #ifdef TRACE
    Verilated::traceEverOn(true);
    #endif

    #ifndef TRACE
    Verilated::traceEverOn(false);
    #endif
    // Pass arguments so Verilated code can see them, e.g. $value$plusargs
    // This needs to be called before you create any model
    Verilated::commandArgs(argc, argv);

    // Create logs/ directory in case we have traces to put under it
    Verilated::mkdir("logs");


    // Construct the Verilated model, from Varmleocpu_csr.h generated from Verilating "armleocpu_csr.v"
    #ifndef TOP_ALLOCATION
        #error "Top allocation macro is missing"
    #endif
    TOP_ALLOCATION;
    // Or use a const unique_ptr, or the VL_UNIQUE_PTR wrapper
    #ifdef TRACE
    m_trace = new VerilatedVcdC;
    TOP->trace(m_trace, 99);
    m_trace->open("vcd_dump.vcd");
    #endif

    try {
        TOP->clk = 0;
