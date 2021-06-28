tbfiles=cache_tb.v
tbfiles+=../../../peripheral_src/armleocpu_axi_bram.v
tbfiles+=../../../peripheral_src/armleocpu_axi_exclusive_monitor.v
tbfiles+=../../../peripheral_src/armleocpu_axi_router.v
tbfiles+=../../../peripheral_src/armleocpu_axi_read_router.v
tbfiles+=../../../peripheral_src/armleocpu_axi_write_router.v

files=../../../core_src/armleocpu_mem_1rw.v
files+=../../../core_src/armleocpu_mem_1rwm.v
files+=../../../core_src/armleocpu_cache_pagefault.v
files+=../../../core_src/armleocpu_ptw.v
files+=../../../core_src/armleocpu_tlb.v
files+=../../../core_src/armleocpu_loadgen.v
files+=../../../core_src/armleocpu_storegen.v
files+=../../../core_src/armleocpu_cache.v
defines=-DDEBUG_CACHE

iverilog_options="-pfileline=1"

top=armleocpu_cache

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk