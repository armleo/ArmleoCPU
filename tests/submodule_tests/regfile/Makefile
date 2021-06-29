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

cpp_files=sim_main.cpp
files=../../../core_src/armleocpu_regfile.v ../../../core_src/armleocpu_regfile_one_lane.v
verilator_options=-Wno-UNOPTFLAT
top=armleocpu_regfile


default: clean test
test: test-verilator synth-yosys
clean: clean-verilator clean-yosys

include ../../VerilatorSimulate.mk
include ../../YosysTemplate.mk
