////////////////////////////////////////////////////////////////////////////////
// 
// This file is part of ArmleoCPU.
// ArmleoCPU is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// ArmleoCPU is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with ArmleoCPU.  If not, see <https://www.gnu.org/licenses/>.
// 
// Copyright (C) 2016-2021, Arman Avetisyan, see COPYING file or LICENSE file
// SPDX-License-Identifier: GPL-3.0-or-later
// 

#include "riscv_test.h"

RVTEST_RV32U        # Define TVM used by program.

# Test code region.
RVTEST_CODE_BEGIN   # Start of test code.
        addi x0, x0, 0;
        RVTEST_PASS           # Signal success.
RVTEST_CODE_END     # End of test code.

RVTEST_DATA_BEGIN   # Start of test output data region.
result:
        .dword -1
RVTEST_DATA_END     # End of test output data region.
