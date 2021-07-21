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

tbfiles=cache_tb.v
tbfiles+=$(PROJECT_DIR)/peripheral_src/armleocpu_axi_bram.v
tbfiles+=$(PROJECT_DIR)/peripheral_src/armleocpu_axi_exclusive_monitor.v
tbfiles+=$(PROJECT_DIR)/peripheral_src/armleocpu_axi_router.v
tbfiles+=$(PROJECT_DIR)/peripheral_src/armleocpu_axi_read_router.v
tbfiles+=$(PROJECT_DIR)/peripheral_src/armleocpu_axi_write_router.v

files=$(PROJECT_DIR)/core_src/armleocpu_mem_1rw.v
files+=$(PROJECT_DIR)/core_src/armleocpu_mem_1rwm.v
files+=$(PROJECT_DIR)/core_src/armleocpu_cache_pagefault.v
files+=$(PROJECT_DIR)/core_src/armleocpu_ptw.v
files+=$(PROJECT_DIR)/core_src/armleocpu_tlb.v
files+=$(PROJECT_DIR)/core_src/armleocpu_loadgen.v
files+=$(PROJECT_DIR)/core_src/armleocpu_storegen.v
files+=$(PROJECT_DIR)/peripheral_src/armleocpu_register_slice.v
files+=$(PROJECT_DIR)/core_src/armleocpu_cache.v
defines=-DDEBUG_CACHE -DFORMAL_RULES

iverilog_options="-pfileline=1"

top=armleocpu_cache

default: clean test
test: simulate-iverilog synth-yosys
clean: clean-iverilog clean-yosys

include $(PROJECT_DIR)/tests/SimulateTemplate.mk
include $(PROJECT_DIR)/tests/YosysTemplate.mk