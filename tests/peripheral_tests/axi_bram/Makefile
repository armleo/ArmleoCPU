tbfiles=axi_bram_tb.v
files=../../../peripheral_src/armleocpu_axi_bram.v ../../../core_src/armleocpu_mem_1rw.v ../../../core_src/armleocpu_mem_1rwm.v
defines=-DBRAM_DEBUG


default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

synth-yosys: 
	yosys -c simple.tcl 2>&1 | tee yosys.log
	! grep "ERROR:" yosys.log
clean-yosys:
	rm -rf synth.tcl abc.history


include ../../SimulateTemplate.mk
