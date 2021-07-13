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

tbfiles=axi2simple_converter_tb.v
files=../../../peripheral_src/armleocpu_axi2simple_converter.v
top_tb=axi2simple_converter_testbench
top=armleocpu_axi2simple_converter
defines=-DFORMAL_RULES

default: clean test
test: simulate-iverilog synth-yosys lint-verilator
clean: clean-iverilog clean-yosys clean-verilator

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
include ../../VerilatorSimulate.mk # For linting
