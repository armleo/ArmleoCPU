all: check test

test: subtests sbt_test_only

compile_top:
	sbt "runMain ArmleoCPUDriver --target-dir generated_vlog"

sbt_test_only:
	sbt "testOnly armleocpu.ALUTester" "testOnly armleocpu.RegfileTester"


sbt_mount:
	sbt

subtests: compile_verilog
	cd testbenches && $(MAKE)

clean:
	rm -rf generated_vlog
	rm -rf target
	rm -rf test_run_dir
	#cd testbenches && $(MAKE) clean
	#rm -rf check.log
