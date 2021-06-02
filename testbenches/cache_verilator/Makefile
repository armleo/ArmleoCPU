cpp_files=sim_main.cpp
files=../src/armleocpu_mem_1rw.v
files+=../src/armleocpu_cache_pagefault.v
files+=../src/armleocpu_ptw.v
files+=../src/armleocpu_tlb.v
files+=../src/armleocpu_loadgen.v
files+=../src/armleocpu_storegen.v
files+=../src/armleocpu_cache.v

defines=-DDEBUG_CACHE -DDEBUG_PAGEFAULT -DDEBUG_PTW
verilator_options=-Wno-UNOPTFLAT -Wno-DECLFILENAME
top=armleocpu_cache

include ../VerilatorSimulate.mk