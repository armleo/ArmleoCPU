all: clean check test

test:
	timeout --foreground 1000 $(MAKE) -C tests

clean:
	rm -rf check.log
	$(MAKE) -C tests clean
	

check:
	$(MAKE) --version > check.log
	env >> check.log
	yosys -V >> check.log
	gcc --version >> check.log
	verilator --version >> check.log
	iverilog -V >> check.log
