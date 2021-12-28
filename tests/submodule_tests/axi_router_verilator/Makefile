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
defines=

verilator_options=-GADDR_WIDTH=32

verilator_options+=-GOPT_NUMBER_OF_CLIENTS=4
verilator_options+=-GREGION_COUNT=4

# REGION_CLIENT_NUM = {2'b01, 2'b00, 2'b10, 2'b11}
verilator_options+=-GREGION_CLIENT_NUM=8\'b01_00_10_11

# REGION_BASE_ADDRS = {32'h0000_0000, 32'h0000_1000, 32'h1000_0000, 32'h1000_1000}
verilator_options+=-GREGION_BASE_ADDRS=128\'h00000000_00001000_10000000_10001000

# REGION_END_ADDRS= {32'h0000_1000, 32'h0000_2000, 32'h1000_2000, 32'h1000_3000}
verilator_options+=-GREGION_END_ADDRS=128\'h00001000_00002000_10002000_10003000

# REGION_CLIENT_BASE_ADDRS = {32'h0000_0000, 32'h0000_1000, 32'h1000_0000, 32'h1000_1000}
verilator_options+=-GREGION_CLIENT_BASE_ADDRS=128\'h00000000_00001000_10000000_10001000

top=armleosoc_axi_router
files=$(ROUTER_FILES)

include $(PROJECT_DIR)/tests/VerilatorYosysTemplate.mk
