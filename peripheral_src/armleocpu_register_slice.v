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
// Project:	ArmleoCPU
//
// Purpose:	A basic register slice
// Parameters:
//      DW - amount of bits in data inputs/outputs
//          
// 
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_register_slice  (
    clk, rst_n,
    in_valid, in_data, in_ready,
    out_valid, out_data, out_ready
);
    parameter [0:0] PASSTHROUGH = 0;
    parameter DW = 8;
    input clk;
    input rst_n;

    // Port name then signal name
    // IN port group is data input
    input wire              in_valid;
    input wire [DW-1:0]     in_data;
    output wire             in_ready;

    output reg              out_valid;
    output reg [DW-1:0]     out_data;
    input wire              out_ready;

generate if(!PASSTHROUGH) begin : NON_PASSTHROUGH_GENERATE

    reg reg_valid; // Is register below valid
    reg reg_valid_nxt; // Input of register above
    reg [DW-1:0] reg_data; // Register
    reg [DW-1:0] reg_data_nxt; // Input of register above

    reg [DW-1:0] out_data_nxt;
    reg out_valid_nxt;

    assign in_ready = !reg_valid;

    always @(posedge clk) begin
        reg_valid <= reg_valid_nxt;
        reg_data <= reg_data_nxt;
        out_valid <= out_valid_nxt;
        out_data <= out_data_nxt;
    end

    always @* begin
        reg_valid_nxt = reg_valid;
        reg_data_nxt = reg_data;
        out_data_nxt = out_data;
        out_valid_nxt = out_valid;

        if(!rst_n) begin
            reg_valid_nxt = 0;
        end else if((in_valid && in_ready) && (out_valid && !out_ready)) begin
            // Input data is valid and no data is stored
            // AND Output is valid and output is not accepted

            // This is because data was forwarded if in_read (== no data saved == !reg_valid) so no need to mark it as valid in buffer, because its already forwarded to output
            reg_valid_nxt = 1;
        end else if(out_ready) begin
            // Output is done, no data to accepted
            reg_valid_nxt = 0;
        end

        if(!reg_valid) begin
            reg_data_nxt = in_data;
            // If no data, store data.
            // On next cycle of in_valid going high
            // register reg_valid is set to one and in_ready goes down
        end
        
        // If data is stored, then use it
        if(!rst_n) begin
            out_data_nxt = 0;
            out_valid_nxt = 0;
        end else if(!out_valid || out_ready) begin
            // If data is not in output buffer OR if data is accepted
            out_valid_nxt = (in_valid || reg_valid);
                // Output will be valid on next cycle only if input is valid or data is saved
            // Then if data is stored in buffer then output it
            // Otherwise if input contains valid data, output it
            if(reg_valid) begin
                out_data_nxt = reg_data;
            end else if(in_valid) begin
                // Else if no data is stored, forward it
                out_data_nxt = in_data;
            end
        end
    end
end else begin : PASSTHROUGH_GENERATE
    assign out_valid = in_valid;
    assign out_data = in_data;
    assign in_ready = out_ready;
end
endgenerate


endmodule

`include "armleocpu_undef.vh"

