###############################################################################
# 
# This file is part of ArmleoCPU.
# ArmleoCPU is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# ArmleoCPU is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with ArmleoCPU.  If not, see <https:#www.gnu.org/licenses/>.
# 
# Copyright (C) 2016-2021, Arman Avetisyan, see COPYING file or LICENSE file
# SPDX-License-Identifier: GPL-3.0-or-later
# 

# inputs $(top), $(defines) $(files), $(cpp_files), $(includepaths)
includepaths+=../.. ../../../core_src

includepathsI=$(addprefix -I,$(includepaths))

VERILATOR = verilator
VERILATOR_COVERAGE = verilator_coverage


VERILATOR_FLAGS = $(verilator_options)
# VERILATOR_FLAGS += -Wall
VERILATOR_FLAGS += -cc --exe -Os -x-assign 0 $(defines) --trace --coverage $(includepathsI) --top-module $(top)

VERILATOR_INPUT = $(files) $(cpp_files)


test-verilator: docker_check
	@echo
	@echo "Running verilator"
	$(VERILATOR) $(VERILATOR_FLAGS) $(VERILATOR_INPUT) 2>&1 | tee verilator.log
	
	! grep "%Error" verilator.log
	@echo
	@echo "Running verilated makefiles"
	cd obj_dir && $(MAKE) -j 4 -f V$(top).mk 2>&1 | tee make.log
	@echo

	@echo "Running verilated executable"
	@rm -rf logs
	@mkdir -p logs
	obj_dir/V$(top) +trace 2>&1 | tee run.log
	! grep "%Error" run.log

	@echo
	@echo "Running coverage"
	@rm -rf logs/annotated
	$(VERILATOR_COVERAGE) --annotate logs/annotated logs/coverage.dat

	@echo
	@echo "Complete"
	

lint-verilator: docker_check
	$(VERILATOR) --lint-only -Wall $(verilator_options) $(includepathsI) --top-module $(top) $(files) 2>&1 | tee verilator.lint.log

clean-verilator: docker_check
	rm -rf *.log logs *.vcd obj_dir

include ../../../dockercheck.mk
