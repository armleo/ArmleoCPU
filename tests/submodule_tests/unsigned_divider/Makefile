tbfiles=divider_tb.v
files=../../../core_src/armleocpu_unsigned_divider.v
top=armleocpu_unsigned_divider
top_tb=divider_testbench

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
