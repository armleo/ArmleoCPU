all: clean check test

test:
	cd tests && $(MAKE) test

clean:
	rm -rf check.log
	cd tests && $(MAKE) clean
	

check:
	echo $(MAKE) > check.log
	gcc --version >> check.log
	verilator --version >> check.log
	iverilog -V >> check.log
