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
// Purpose: JTAG Debug Transport Module
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_jtag_dtm (
    // Main clock and system wide reset
    clk, rst_n,

    // JTAG IF
    tck_i, tms_i, td_i, td_o,

    dmi_req_valid, dmi_req_ready, dmi_req_wdata, dmi_req_addr,

    dmi_resp_valid, dmi_resp_ready, dmi_resp_rdata, dmi_resp_resp
);

    parameter IDCODE_VALUE = 32'h80000001;
    localparam ABITS = 16; // Enough to fit 512 of DMs

    localparam IR_LENGTH = 5;

    localparam DMI_LEN = 2 + 32 + ABITS;

    input  wire         clk;
    input  wire         rst_n;  // system wide reset

    input  wire         tck_i;    // JTAG test clock pad
    input  wire         tms_i;    // JTAG test mode select pad
    input  wire         td_i;     // JTAG test data input pad
    output reg          td_o;     // JTAG test data output pad

    output reg          dmi_req_valid;
    input wire          dmi_req_ready;
    output reg  [31:0]  dmi_req_wdata;
    output reg  [31:0]  dmi_req_addr;
    
    input wire          dmi_resp_valid;
    output reg          dmi_resp_ready;
    input wire  [31:0]  dmi_resp_rdata;
    output reg   [1:0]  dmi_resp_resp;



reg tdo_i;
wire [IR_LENGTH-1:0] ir_o;
wire trst_no;

wire update_o;
wire shift_o;
wire capture_o;

armleocpu_jtag_tap #(
    .IR_LENGTH(IR_LENGTH),
    .IDCODE_VALUE(IDCODE_VALUE)
) tap (
    .clk        (clk),
    .rst_n      (rst_n),

    .tdo_i      (tdo_i),
    .ir_o       (ir_o),
    .trst_no    (trst_no),
    .capture_o  (capture_o),
    .shift_o    (shift_o),
    .update_o   (update_o),

    .tck_i      (tck_i),
    .tms_i      (tms_i),
    .td_i       (td_i),
    .td_o       (td_o)
);


// Selects
wire dtmcs_select = ir_o == 5'h10;
wire dmi_select = ir_o == 5'h11;


reg [31:0] dtmcs_shift;
reg [DMI_LEN-1:0] dmi_shift;


wire dtmcs_write = dtmcs_select && update_o;
wire [31:0] dtmcs_write_data = dtmcs_shift;
reg [31:0] dtmcs_read_data;

wire dmi_write = dmi_select && update_o;
wire [DMI_LEN-1:0] dmi_write_data = dmi_shift;
reg [DMI_LEN-1:0] dmi_read_data;

always @(posedge clk) begin
    if(!rst_n) begin
        dtmcs_shift <= 0;
        dmi_shift <= 0;
    end else begin
        if(capture_o) begin
            if(dtmcs_select)
                dtmcs_shift <= dtmcs_read_data;
            if(dmi_select)
                dmi_shift <= dmi_read_data;
        end
        if(shift_o) begin
            if(dtmcs_select)
                dtmcs_shift <= {td_i, dtmcs_shift[31:1]};
            if(dmi_select)
                dmi_shift <= {td_i, dmi_shift[DMI_LEN-1:1]};
        end
        if(update_o) begin
            // Nothing to do here. dmi_write and dtmcs_write will be asserted for this case
        end
    end
end

always @* begin
    if(dtmcs_select)
        tdo_i = dtmcs_shift[0];
    else if(dmi_select)
        tdo_i = dmi_shift[0];
    else
        tdo_i = 0;
end



endmodule

`include "armleocpu_undef.vh"
