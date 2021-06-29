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
// Filename:    armleocpu_arbiter.v
// Project:	ArmleoCPU
//
// Purpose:	Simple round-robin Arbiter
// Deps: None
//
// Parameters:
//	OPT_N
//      Number of request ports
// Ports:
//  next
//      Host registered outputs and arbiter should register
//      current grants and start generating new grant signals for next cycle.
////////////////////////////////////////////////////////////////////////////////
module armleocpu_arbiter #(
    parameter OPT_N = 2,
    localparam OPT_N_CLOG2 = $clog2(OPT_N)
) (
    input clk,
    input rst_n,

    input next,
    input [OPT_N-1:0] req,
    output [OPT_N-1:0] grant,
    output [OPT_N_CLOG2-1:0] grant_id
);
// TODO: Implement

endmodule
