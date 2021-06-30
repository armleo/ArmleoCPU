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
// Filename:    armleocpu_axi_arbiter.v
// Project:	ArmleoCPU
//
// Purpose:	A basic N-to-1 AXI4 arbiter
// Deps: armleocpu_defines.vh for AXI4 declarations
//
// Parameters:
//	OPT_NUMBER_OF_HOSTS
//      Number of hosts from 1 to any value that can be synthesized
//      and fits into parameter.
//      Some synthesizer cant synthesize too many for loops.
//
//	OPT_PASSTHROUGH
//		Dont put register at outputs. Not recommended because for high number of hosts perfomance might take a hit
//  
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_axi_arbiter #(
    parameter OPT_NUMBER_OF_HOSTS = 2,
    

    parameter [0:0] OPT_PASSTHROUGH
) (
    
);

endmodule

`include "armleocpu_undef.vh"
