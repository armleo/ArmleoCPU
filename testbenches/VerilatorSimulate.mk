# inputs $(files), $(cpp_files), $(includepaths)
includepaths+=../ ../../src/includes/

includepathsI=$(addprefix -I,$(includepaths))

VERILATOR = verilator
VERILATOR_COVERAGE = verilator_coverage


VERILATOR_FLAGS = 
# VERILATOR_FLAGS += -Wall
VERILATOR_FLAGS += -cc --exe -Os -x-assign 0 --trace --coverage $(includepathsI)

VERILATOR_INPUT = $(files) $(cpp_files)

default: run

run:
	@echo
	@echo "Running verilator"
	$(VERILATOR) $(VERILATOR_FLAGS) $(VERILATOR_INPUT) &> verilator.log

	@echo
	@echo "Running verilated makefiles"
	$(MAKE) -j 4 -C obj_dir -f ../Makefile_obj 
	@echo
	@echo "Running verilated executable"
	@rm -rf logs
	@mkdir -p logs
	obj_dir/V$(topname) +trace

	@echo
	@echo "Running coverage"
	@rm -rf logs/annotated
	$(VERILATOR_COVERAGE) --annotate logs/annotated logs/coverage.dat

	@echo
	@echo "Complete"