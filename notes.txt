Top:
    Implement the TOP level that fetches instructions and does execution

Known issues:
TODO: PTW needs the debug 

Cache tests:
TODO: Add tests covering different cache configurations
TODO: Add tests covering AXI_PASSTHROUGH

TODO: AWPROT/ARPROT coverage

TODO: Add tests that try to break the s0/s1/resp pipeline in cache

TODO: Add tests that try to break the restart logic
TODO: 

Cache/PTW Tests:
    TODO: Add more tests covering PMA error
    TODO: Add a couple of more invalid tree leaves on both levers tests

Cache/Pagefault tests:
    TODO: Go over every case and make sure that there is at least 3 cases of tests for each

Cache/TLB Tests:
    TODO: Add tests that cover all "ways" and "lanes"

BRAM:
    DONE: Add tests with write/read without stalled b/r cycle
    BRAM is completly tested by the looks of it
    

TODO: Replace all $finish statements with `assert_finish

TODO: Let's make AXI4 protocol checker
TODO: Add separate BRAM test that uses verilator


AXI4 related: Some peripherals don't properly return IDs, test this properly because b_expect does not check id value

Long term:
    Add pipelined fetch/decode/execute stages
    64 bit ALU
    64 bit regfile
    64 bit-ify the CPU in general
    PTW sv39/48 support
    Cache 64 bit
    Debug module


