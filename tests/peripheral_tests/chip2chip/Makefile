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

tbfiles=chip2chip_host_tb.v
files+=../../../peripheral_src/armleocpu_chip2chip_host.v
files+=../../../peripheral_src/armleocpu_axi_bram.v
files+=../../../peripheral_src/armleocpu_axi_exclusive_monitor.v
files+=../../../core_src/armleocpu_mem_1rw.v
files+=../../../core_src/armleocpu_mem_1rwm.v
top_tb=chip2chip_host_testbench
top=armleocpu_chip2chip_host

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include ../../SimulateTemplate.mk
include ../../YosysTemplate.mk
