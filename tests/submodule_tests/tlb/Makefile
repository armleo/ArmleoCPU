tbfiles=tlb_tb.v
files=../../../core_src/armleocpu_tlb.v ../../../core_src/armleocpu_mem_1rw.v
defines=-DDEBUG_TLB
top=armleocpu_tlb
top_tb=tlb_testbench

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk