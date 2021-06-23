tbfiles=axi_register_slice_tb.v
files=../../../peripheral_src/armleocpu_axi_register_slice.v
top=armleocpu_axi_register_slice

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
