tbfiles=storegen_tb.v
files=../../../core_src/armleocpu_storegen.v
top=armleocpu_storegen

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk

