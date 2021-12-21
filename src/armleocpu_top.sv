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
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

// TODO: Remove below
// verilator lint_off UNUSED
// verilator lint_off UNDRIVEN

module armleocpu_top(
    input wire clk,
    input wire rst_n,

    `CACHE_AXI_IO(d_axi_),
    `CACHE_AXI_IO(i_axi_)
);



// Instance the CSR
// Instance the ICache
// Instance the DCache
// Instance the Register File
// Instance execute
// Write state machine that does fetching/decoding/execute/writeback
// 



endmodule