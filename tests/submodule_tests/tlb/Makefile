tbfiles=tlb_tb.v
files=../../../core_src/armleocpu_tlb.v ../../../core_src/armleocpu_mem_1rw.v
defines=-DDEBUG_TLB

default: clean test
test: simulate-iverilog
clean: clean-iverilog

include ../../SimulateTemplate.mk
