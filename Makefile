SBT=sbt


all: check test

test: subtests

subtests:
	cd testbenches && $(MAKE)

clean:
	cd testbenches && $(MAKE) clean

check:
	gcc --version > check.log
	verilator --version >> check.log
	iverilog -V >> check.log
