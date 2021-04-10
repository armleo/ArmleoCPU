cpp_files=sim_main.cpp
files=../../src/mem_1w1r.v
files+=../../src/armleocpu_multiplier.v
files+=../../src/armleocpu_unsigned_divider.v
files+=../../src/armleocpu_alu.v
files+=../../src/armleocpu_brcond.v
files+=../../src/armleocpu_regfile.v
files+=../../src/armleocpu_cache_pagefault.v
files+=../../src/armleocpu_ptw.v
files+=../../src/armleocpu_tlb.v
files+=../../src/armleocpu_loadgen.v
files+=../../src/armleocpu_storegen.v
files+=../../src/armleocpu_cache.v
files+=../../src/armleocpu_csr.v
files+=../../src/armleocpu_execute.v
files+=../../src/armleocpu_fetch.v
files+=../../src/armleocpu.v
includepaths=../../src/
defines=-DDEBUG_CACHE -DDEBUG_EXECUTE -DDEBUG_FETCH
top=armleocpu
verilator_options=-Wno-UNOPTFLAT -Wno-DECLFILENAME



include ../VerilatorSimulate.mk

log_filter:
	rm -rf tests.log
	cat run.log | grep "Test:" > tests.log
	