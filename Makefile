all: check test

test: subtests

subtests:
	cd testbenches && $(MAKE)

clean:
	cd testbenches && $(MAKE) clean
	rm -rf check.log
check:
	echo $(MAKE) > check.log
	gcc --version >> check.log
	verilator --version >> check.log
	iverilog -V >> check.log
	xvlog --version >> check.log
