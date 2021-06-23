tbfiles=ptw_tb.v
files=../../../core_src/armleocpu_ptw.v
defines=-DDEBUG_PTW
top=armleocpu_ptw

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
