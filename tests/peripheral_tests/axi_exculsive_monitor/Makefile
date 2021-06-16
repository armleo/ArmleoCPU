tbfiles=axi_excusive_monitor_tb.v
files=../../../peripheral_src/armleocpu_axi_bram.v ../../../peripheral_src/armleocpu_axi_exclusive_monitor.v ../../../core_src/armleocpu_mem_1rw.v ../../../core_src/armleocpu_mem_1rwm.v
defines=-DDEBUG_EXCLUSIVE_MONITOR -DBRAM_DEBUG
#iverilog_options=-pfileline=1

default: clean test
test: simulate-iverilog
clean: clean-iverilog


synth-yosys: 
	yosys -c simple.tcl 2>&1 | tee yosys.log
	! grep "ERROR:" yosys.log
clean-yosys:
	rm -rf synth.tcl abc.history


include ../../SimulateTemplate.mk
