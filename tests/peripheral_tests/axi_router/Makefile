tbfiles=axi_router_tb.v
files=../../../peripheral_src/armleocpu_axi_router.v
files+=../../../peripheral_src/armleocpu_axi_read_router.v
files+=../../../peripheral_src/armleocpu_axi_write_router.v
files+=../../../peripheral_src/armleocpu_axi_bram.v
files+=../../../peripheral_src/armleocpu_axi_exclusive_monitor.v
files+=../../../core_src/armleocpu_mem_1rw.v
files+=../../../core_src/armleocpu_mem_1rwm.v
top_tb=axi_router_testbench
top=armleocpu_axi_router

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
