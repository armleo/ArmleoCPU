tbfiles=axi_excusive_monitor_tb.v
files=../../../peripheral_src/armleocpu_axi_bram.v ../../../peripheral_src/armleocpu_axi_exclusive_monitor.v ../../../core_src/armleocpu_mem_1rw.v ../../../core_src/armleocpu_mem_1rwm.v
defines=-DDEBUG_EXCLUSIVE_MONITOR -DBRAM_DEBUG
top=armleocpu_axi_exclusive_monitor

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys


include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
