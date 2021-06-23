tbfiles=axi_router_tb.v
files=../../../peripheral_src/armleocpu_axi_router.v
files+=../../../peripheral_src/armleocpu_axi_read_router.v
files+=../../../peripheral_src/armleocpu_axi_write_router.v
files+=../../../peripheral_src/armleocpu_axi_bram.v
files+=../../../peripheral_src/armleocpu_axi_exclusive_monitor.v
files+=../../../core_src/armleocpu_mem_1rw.v
files+=../../../core_src/armleocpu_mem_1rwm.v

default: clean test
test: simulate-iverilog yosys-synth
clean: clean-iverilog clean-yosys


yosys-synth:
	yosys -c simple.tcl 2>&1 | tee yosys.log
	! grep "ERROR:" yosys.log
clean-yosys:
	rm -rf synth.v abc.history


include ../../SimulateTemplate.mk
