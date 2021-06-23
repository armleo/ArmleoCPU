tbfiles=axi_bram_tb.v
files=../../../peripheral_src/armleocpu_axi_bram.v ../../../core_src/armleocpu_mem_1rw.v ../../../core_src/armleocpu_mem_1rwm.v
defines=-DBRAM_DEBUG
top=armleocpu_axi_bram


default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
