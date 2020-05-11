# inputs $(top), $(defines) $(files), $(cpp_files), $(includepaths)
includepaths+=../ ../../src/includes/

includepathsI=$(addprefix -I,$(includepaths))

VERILATOR = verilator
VERILATOR_COVERAGE = verilator_coverage


VERILATOR_FLAGS = 
# VERILATOR_FLAGS += -Wall
VERILATOR_FLAGS += -cc --exe -Os -x-assign 0 $(defines) --trace --coverage $(includepathsI) --top-module $(top)

VERILATOR_INPUT = $(files) $(cpp_files)

default: clean lint execute

lint:
	$(VERILATOR) --lint-only -Wall $(includepathsI) --top-module $(top) $(files) 2>&1 | tee verilator.lint.log

build:
	@echo
	@echo "Running verilator"
	$(VERILATOR) $(VERILATOR_FLAGS) $(VERILATOR_INPUT) 2>&1 | tee verilator.log

	@echo
	@echo "Running verilated makefiles"
	cd obj_dir && $(MAKE) -j 4 -f V$(top).mk 2>&1 | tee make.log
	@echo

execute: build
	@echo "Running verilated executable"
	@rm -rf logs
	@mkdir -p logs
	obj_dir/V$(top) +trace 2>&1 | tee run.log

	@echo
	@echo "Running coverage"
	@rm -rf logs/annotated
	$(VERILATOR_COVERAGE) --annotate logs/annotated logs/coverage.dat

	@echo
	@echo "Complete"
clean:
	rm -rf *.log logs *.vcd obj_dir