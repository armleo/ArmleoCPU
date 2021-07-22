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
// Filename: armleocpu_storegen.v
// Project:	ArmleoCPU
//
// Purpose:	StoreGen unit, used to change alignment from left aligned to
//      to bus aligned
//
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_storegen(
    input [1:0] inword_offset,
    input [1:0] storegen_type,

    input [31:0] storegen_datain,

    output wire  [31:0] storegen_dataout,
    output wire  [3:0]  storegen_datamask,
    output wire         storegen_missaligned,
    output wire         storegen_unknowntype
);

assign storegen_datamask = 
    storegen_type == `STORE_WORD ? 4'b1111 : (
    storegen_type == `STORE_HALF ? (4'b11 << inword_offset) : (
    storegen_type == `STORE_BYTE ? (4'b1 << inword_offset) : 4'b0000
));

wire [4:0] woffset = {inword_offset, 3'b000};

assign storegen_dataout = storegen_datain << woffset;

assign storegen_missaligned = (
    ((storegen_type == `STORE_WORD) && (|inword_offset)) || 
    ((storegen_type == `STORE_HALF) && (inword_offset[0]))
);

assign storegen_unknowntype = storegen_type == 2'b11;

endmodule


`include "armleocpu_undef.vh"
