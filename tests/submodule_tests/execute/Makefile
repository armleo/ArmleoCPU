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
###############################################################################


cpp_files=sim_main.cpp
files=
files+=$(PROJECT_DIR)/core_src/armleocpu_brcond.v
files+=$(PROJECT_DIR)/core_src/armleocpu_alu.v
files+=$(PROJECT_DIR)/core_src/armleocpu_unsigned_divider.v
files+=$(PROJECT_DIR)/core_src/armleocpu_multiplier.v
files+=$(PROJECT_DIR)/core_src/armleocpu_execute.v
defines=-DDEBUG_EXECUTE
top=armleocpu_execute



# Currently disabled, will be enabled when fixed

default: clean test
test: # test-verilator synth-yosys
clean: clean-verilator clean-yosys

include $(PROJECT_DIR)/tests/VerilatorSimulate.mk
include $(PROJECT_DIR)/tests/YosysTemplate.mk