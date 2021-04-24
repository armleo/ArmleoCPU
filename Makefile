all: check test

test: subtests

compile_top:
	sbt "runMain ArmleoCPUDriver --target-dir generated_vlog"

sbt_mount:
	sbt

subtests: compile_verilog
	cd testbenches && $(MAKE)

clean:
	rm -rf generated_vlog
	cd testbenches && $(MAKE) clean
	rm -rf check.log
