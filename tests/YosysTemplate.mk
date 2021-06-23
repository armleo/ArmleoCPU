
yosys_includepaths=../../ ../../../core_src/ ../../../peripheral_src/
yosys_includepathsI=$(addprefix -I,$(includepaths))
top?=top
top_tb?=$(top)_tb

synth.yosys.temp.tcl: Makefile ../../YosysTemplate.mk
	rm -rf synth.yosys.temp.tcl
	echo "yosys -import" >> synth.yosys.temp.tcl
	echo "verilog_defaults -add $(includepathsI)" >> synth.yosys.temp.tcl
	for file in $(files); do echo "read_verilog -sv $${file}" >> synth.yosys.temp.tcl; done

	echo "synth_intel -family cycloneiv -top $(top)" >> synth.yosys.temp.tcl
	echo "clean" >> synth.yosys.temp.tcl
	echo "write_verilog synth.yosys.temp.v" >> synth.yosys.temp.tcl
synth-yosys: synth.yosys.temp.tcl
	yosys -c synth.yosys.temp.tcl 2>&1 | tee yosys.log
	! grep "ERROR:" yosys.log
clean-yosys:
	rm -rf synth.v abc.history synth.yosys.temp.tcl yosys.log


