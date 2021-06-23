tbfiles=mem_cells_tb.v
files=../../../core_src/armleocpu_mem_1rwm.v ../../../core_src/armleocpu_mem_1rw.v
top=armleocpu_mem_1rwm

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
