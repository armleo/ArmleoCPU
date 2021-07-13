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

# accepts defines, includepaths, tbfiles, files
netlist=netlist.netlist
simresult=./dump.vcd
vvpparams=
iverilog=iverilog
vvp=vvp
gtkwave=gtkwave

includepaths+=../../ ../../../core_src/ ../../../peripheral_src/
top?=top
top_tb?=$(top)_tb


includepathsI=$(addprefix -I,$(includepaths))

view-gtkwave: $(simresult)
	$(gtkwave) $(simresult)

build-iverilog: docker_check $(netlist)
	
simulate-iverilog: docker_check $(netlist)
	$(vvp) $(netlist) $(vvpparams) | tee execute_logfile.log
	! grep "ERROR" execute_logfile.log
synth-iverilog: docker_check
	iverilog  -Winfloop -Wall -g2012 -tvlog95 -o synth.iverilog.temp.v $(files) $(includepathsI)
$(netlist): $(files) $(tbfiles) Makefile docker_check
	$(iverilog) -Winfloop -Wall -g2012 $(includepathsI) -o $(netlist) -D__ICARUS__=1 -DSIMULATION -DSIMRESULT="\"$(simresult)\"" $(defines) -DTOP=$(top) -DTOP_TB=$(top_tb) $(files) $(tbfiles) $(iverilog_options)  2>&1 | tee compile_logfile.log
	! grep "error:" compile_logfile.log
	! grep "I give up." compile_logfile.log
lint: $(files) Makefile docker_check
	verilator --lint-only -Wall $(verilator_options) $(includepathsI) $(files) -DSIMRESULT="\"$(simresult)\"" 2>&1 | tee verilator.lint.log
clean-iverilog: docker_check
	rm -rf *.vcd *.lxt2 xvlog* xsim* verilator.lint.log compile_logfile.log execute_logfile.log *.iverilog.temp.v $(netlist)

include ../../../dockercheck.mk