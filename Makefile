SBT=sbt


all: check test

test: subtests

subtests:
	cd testbenches && $(MAKE) subtests
pipelinetests:
	

build:
	

check:
	gcc --version > check.log
	verilator --version >> check.log
	iverilog --version >> check.log
