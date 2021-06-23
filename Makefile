all: clean check test

test:
	cd tests && $(MAKE) test

clean:
	rm -rf check.log
	cd tests && $(MAKE) clean
	

check:
	$(MAKE) --version > check.log
	env >> check.log
	yosys -V >> check.log
	gcc --version >> check.log
	verilator --version >> check.log
	iverilog -V >> check.log
