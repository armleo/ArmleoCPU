cpp_files=sim_main.cpp
files=../../../core_src/armleocpu_regfile.v ../../../core_src/armleocpu_regfile_one_lane.v
verilator_options=-Wno-UNOPTFLAT
top=armleocpu_regfile


default: clean test
test: test-verilator synth-yosys
clean: clean-verilator clean-yosys

include ../../VerilatorSimulate.mk
include ../../YosysTemplate.mk
