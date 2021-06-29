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

###################################################################
# Project Configuration: 
# 
# Specify the name of the design (project) and the Quartus II
# Settings File (.qsf)
###################################################################

PROJECT = armleocpu_testbuild
TOP_LEVEL_ENTITY = armleocpu
ASSIGNMENT_FILES = $(PROJECT).qpf $(PROJECT).qsf

###################################################################
# Part, Family, Boardfile DE1 or DE2
FAMILY = "Cyclone III"
PART = EP3C40F780C8
BOARDFILE = nexus5_pins
###################################################################

###################################################################
# Setup your sources here
SRCS = timing.sdc
SRCS += ../../src/mem_1w1r.v
SRCS += ../../src/armleocpu_multiplier.v
SRCS += ../../src/armleocpu_unsigned_divider.v 
SRCS += ../../src/armleocpu_alu.v 
SRCS += ../../src/armleocpu_brcond.v 
SRCS += ../../src/armleocpu_cache_pagefault.v 
SRCS += ../../src/armleocpu_ptw.v 
SRCS += ../../src/armleocpu_tlb.v 
SRCS += ../../src/armleocpu_loadgen.v 
SRCS += ../../src/armleocpu_regfile.v 
SRCS += ../../src/armleocpu_storegen.v 
SRCS += ../../src/armleocpu_csr.v 
SRCS += ../../src/armleocpu_execute.v 
SRCS += ../../src/armleocpu_fetch.v 
SRCS += ../../src/armleocpu_cache.v 
SRCS += ../../src/armleocpu.v


include ../QuartusBuild.mk
