
top?=Core

main: generated_vlog/Core.v

synth-yosys: synth.yosys.temp.tcl
	yosys -c synth.yosys.temp.tcl 2>&1 | tee yosys.log
	! grep "ERROR:" yosys.log
	! grep "\$$_DLATCH_" yosys.log

synth.yosys.temp.tcl: Makefile
	rm -rf synth.yosys.temp.tcl
	echo "yosys -import" >> synth.yosys.temp.tcl
	echo "read_verilog -sv generated_vlog/Core.v" >> synth.yosys.temp.tcl
	echo "synth_intel -family cycloneiv -top $(top) -vqm synth_quartus.yosys.temp.v" >> synth.yosys.temp.tcl
	echo "clean" >> synth.yosys.temp.tcl
	echo "write_verilog generated_vlog/synth.yosys.temp.v" >> synth.yosys.temp.tcl

test:
	sbt test

generated_vlog/Core.v:
	sbt "runMain armleocpu.CoreGenerator --target verilog --preserve-aggregate none"

clean-synth-yosys:
	rm -rf abc.history synth.yosys.temp.tcl yosys.log synth.yosys.temp.v synth_quartus.yosys.temp.v
