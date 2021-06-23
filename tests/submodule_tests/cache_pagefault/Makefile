tbfiles=cache_pagefault_tb.v
files=../../../core_src/armleocpu_cache_pagefault.v
defines=-DDEBUG_PAGEFAULT
top_tb=pagefault_testbench
top=armleocpu_cache_pagefault

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
