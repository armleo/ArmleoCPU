tbfiles=multiplier_tb.v
files=../../../core_src/armleocpu_multiplier.v
top=armleocpu_multiplier
top_tb=multiplier_testbench

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
