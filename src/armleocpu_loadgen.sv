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
// Filename:    armleocpu_loadgen.v
// Project:	ArmleoCPU
//
// Purpose:	Realigns bus data from address aligned to right aligned,
//              with optional sign extension
//          
//          
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE


module armleocpu_loadgen(
    input [1:0] inword_offset,
    input [2:0] loadgen_type,

    input [31:0] loadgen_datain,

    output reg [31:0] loadgen_dataout,
    output reg loadgen_missaligned,
    output reg loadgen_unknowntype
);

wire [4:0] roffset = {inword_offset, 3'b000};
wire [31:0] rshift  = loadgen_datain >> roffset;



always @* begin
    case(loadgen_type)
        // Word
        `LOAD_WORD:          loadgen_dataout = rshift;
        `LOAD_HALF_UNSIGNED: loadgen_dataout = {16'h0, rshift[15:0]};
        `LOAD_HALF:          loadgen_dataout = {{16{rshift[15]}}, $signed(rshift[15:0])};
        `LOAD_BYTE_UNSIGNED: loadgen_dataout = {{24{1'b0}}, rshift[7:0]};
        `LOAD_BYTE:          loadgen_dataout = {{24{rshift[7]}}, rshift[7:0]};
        default:             loadgen_dataout = rshift;
    endcase
end

always @* begin
    loadgen_unknowntype = 0;
    loadgen_missaligned = 0;
    case(loadgen_type)
        `LOAD_WORD:                      loadgen_missaligned = (|inword_offset);
        `LOAD_HALF_UNSIGNED, `LOAD_HALF: loadgen_missaligned = inword_offset[0];
        `LOAD_BYTE_UNSIGNED, `LOAD_BYTE: loadgen_missaligned = 0;
        default:
            loadgen_unknowntype = 1;
    endcase
end
endmodule


`include "armleocpu_undef.vh"
