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

    dmi_req_valid, dmi_req_ready, dmi_req_wen, dmi_req_wdata, dmi_req_addr,

    dmi_resp_valid, dmi_resp_ready, dmi_resp_rdata, dmi_resp_addr_exists, dmi_resp_unknown_error
);

    parameter IDCODE_VALUE = 32'h80000001;
    localparam [5:0] ABITS = 6'd16; // Enough to fit 512 of DMs
    // Note: Fixed 6 bits localparam because it's directly required by DTM spec

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
    output reg          dmi_req_wen;
    output reg  [31:0]  dmi_req_wdata;
    output reg  [15:0]  dmi_req_addr;
    // Protocl definition:
    // when valid is high payload is valid (classic decoupled IO)
    // when ready is asserted by dmi client then on next cycle next request can
    // be started or valid can be deasserted
    // Payload: wen, wdata, addr
    // addr[7:0] is selection between DM's registers
    // addr[15:8] is used to select between multiple DMs
    // 
    // if wen is asserted then it's write request
    // Data wdata will be written to location specified by addr
    // 
    // This implementation will not send more than one outstanding request
    
    input wire          dmi_resp_valid;
    output reg          dmi_resp_ready;
    input wire  [31:0]  dmi_resp_rdata;
    input wire          dmi_resp_addr_exists;
    input wire          dmi_resp_unknown_error;

    // Same decoupled IO here
    // Instead



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

reg dmi_pending; // shows that there is pending request

localparam [1:0] OP_SUCCESS = 0;
localparam [1:0] OP_FAILED = 2;

reg [1:0] op_resp;
reg [31:0] op_rdata;

// TODO: Fix implementation below
always @(posedge clk) begin
    if(!rst_n) begin
        dtmcs_shift <= 0;
        dmi_shift <= 0;
        dmi_pending <= 0;
        dmi_req_valid <= 0;
        dmi_resp_ready <= 0;
        op_rdata <= 0;
        op_resp <= OP_FAILED;
    end else begin
        if(capture_o) begin
            if(dtmcs_select)
                dtmcs_shift <= dtmcs_read_data;
            if(dmi_select) begin
                dmi_shift <= dmi_read_data;
                if(dmi_pending && dmi_resp_valid)
                    dmi_pending <= 0;
            end
        end else if(shift_o) begin
            if(dtmcs_select)
                dtmcs_shift <= {td_i, dtmcs_shift[31:1]};
            if(dmi_select)
                dmi_shift <= {td_i, dmi_shift[DMI_LEN-1:1]};
        end else if(update_o) begin
            // Nothing to do here. dmi_write and dtmcs_write will be asserted for this case
            // Will reset op
            if(dmi_select) begin
                // This is write to DMI register
                // Check if dmi_pending or dmi_req_valid then set op to sticky error
                // TODO: DMIHARDRESET or DMIRESET logic
                if(dmi_pending || dmi_req_valid || (op_resp == OP_FAILED)) begin
                    // No pending/active requests or sticky error happened
                end else if((dmi_shift[1:0] == 2'b10) || (dmi_shift[1:0] == 2'b01)) begin
                    // No pending request and no active dmi request, new request is 01 or 10
                    dmi_pending <= 1;
                    dmi_req_valid <= 1;
                    dmi_req_wdata <= dmi_shift[33:2];
                    dmi_req_addr <= dmi_shift[33+ABITS:34];
                    dmi_req_wen <= dmi_shift[1:0] == 2'b10;
                    op_resp <= OP_FAILED; // Operation failed
                end
            end
            if(dtmcs_select) begin

            end
        end

        if(dmi_req_ready && dmi_req_valid) begin
            dmi_req_valid <= 0;
        end

        if(dmi_resp_valid) begin
            op_rdata <= dmi_resp_rdata;
            op_resp <= OP_SUCCESS;
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

    // Resp can always be accepted if any are pending

    dtmcs_read_data = {
        14'd0,
        1'b0, // dmihardreset, Reads zero
        1'b0, // dmireset, Reads zero
        1'b0, // 15th bit (starting from zero) is set to zero, does not have a name
        3'b000, // IDLE, fixed to 1, showing that entering idle is required (not really)
        2'b00, // dmistat, 0 - no error, 2 - operation failed, 3 - operation while dmi was not done
        ABITS, // abits (6 bits), set to 16
        4'd1 // Version (0.13 compatible)
    };


    dmi_read_data = {
        {ABITS{1'b0}},
        op_rdata,
        op_resp
    };

end



endmodule

`include "armleocpu_undef.vh"
