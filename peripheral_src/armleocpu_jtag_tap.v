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

module armleocpu_jtag_tap (
    clk, rst_n,

    rst_output_n, tdo_i, ir_o, trst_no,
    update_o, shift_o, capture_o,
    tck_i, tms_i, td_i, td_o, tdo_oe_o,
);

    parameter IR_LENGTH = 5;

    parameter IDCODE_VALUE = 32'h00000001;
    // LSB of IDCODE_VALUE should be set to 1

    input  wire         clk;
    input  wire         rst_n;  // system wide reset

    output wire                 rst_output_n; // JTAG Generated reset
    input  wire                 tdo_i;      // Custom DR logic tdo input. Redirected to output
    output wire [IR_LENGTH-1:0] ir_o;       // Instruction register output
    output reg                  trst_no;
    output reg                  update_o;
    output reg                  shift_o;
    output reg                  capture_o;

    input  wire         tck_i;    // JTAG test clock pad
    input  wire         tms_i;    // JTAG test mode select pad
    input  wire         td_i;     // JTAG test data input pad
    output wire         td_o;     // JTAG test data output pad
    output wire         tdo_oe_o; // Data out output enable


// Implementation details:
//      TAP registers data on risign edge of tck_i, but
//          tck_i is being registered on clk
//          so clk should be at least 4 times higher frequency than tck_i
//      TAP can be reseted by rst_n
//      JTAG logic can generate rst_output_n. This reset is passed to full system

reg [1:0] tck_i_past_values;
wire tck_posedge = tck_i_past_values == 2'b01;
wire tck_negedge = tck_i_past_values == 2'b10;

always @(posedge clk) begin
    if(!rst_n) begin
        tck_i_past_values   <= 2'b00;
    end else begin
        tck_i_past_values   <= {tck_i_past_values[0], tck_i};
    end
end

// TODO: IR register logic and general logic

// tap state
localparam
    TestLogicReset  =  4'd0,
    RunTestIdle     =  4'd1,
    SelectDrScan    =  4'd2,
    CaptureDr       =  4'd3,
    ShiftDr         =  4'd4,
    Exit1Dr         =  4'd5,
    PauseDr         =  4'd6,
    Exit2Dr         =  4'd7,
    UpdateDr        =  4'd8,
    SelectIrScan    =  4'd9,
    CaptureIr       = 4'd10,
    ShiftIr         = 4'd11,
    Exit1Ir         = 4'd12,
    PauseIr         = 4'd13,
    Exit2Ir         = 4'd14,
    UpdateIr        = 4'd15;

reg [3:0] tap_state_q;
reg [3:0] tap_state_d; // Input signal of tap_state_q

// internal signals, _dr is also output of this module
reg capture_dr;
reg shift_dr;
reg update_dr;

reg capture_ir;
reg shift_ir;
reg update_ir;

always @* begin
    // Tap state logic below
    // Note: tap state only transitions on posedge of tck_i

    // TODO: Default values
    trst_no     = 1'b0;
    update_dr   = 1'b0;
    shift_dr    = 1'b0;
    capture_dr  = 1'b0;

    capture_ir  = 1'b0;
    shift_ir    = 1'b0;
    update_ir   = 1'b0;
    //pause_ir  = 1'b0;

    tap_state_d = tap_state_q;
    if(tck_posedge) begin
        case(tap_state_q)
            TestLogicReset: trst_no = 1'b1;

            // DR Path
            UpdateDr:       update_dr   = 1'b1;
            ShiftDr:        shift_dr    = 1'b1;
            CaptureDr:      capture_dr  = 1'b1;

            // IR Path
            CaptureIr:      capture_ir  = 1'b1;
            ShiftIr:        shift_ir    = 1'b1;
            UpdateIr:       update_ir   = 1'b1;
            //PauseIr:      pause_ir = 1'b1; // unused
        endcase

        case (tap_state_q)
            TestLogicReset: tap_state_d = (tms_i) ? TestLogicReset  : RunTestIdle;
            RunTestIdle:    tap_state_d = (tms_i) ? SelectDrScan    : RunTestIdle;

            // DR Path
            SelectDrScan:   tap_state_d = (tms_i) ? SelectIrScan    : CaptureDr;
            CaptureDr:      tap_state_d = (tms_i) ? Exit1Dr         : ShiftDr;
            ShiftDr:        tap_state_d = (tms_i) ? Exit1Dr         : ShiftDr;
            Exit1Dr:        tap_state_d = (tms_i) ? UpdateDr        : PauseDr;
            PauseDr:        tap_state_d = (tms_i) ? Exit2Dr         : PauseDr;
            Exit2Dr:        tap_state_d = (tms_i) ? UpdateDr        : ShiftDr;
            UpdateDr:       tap_state_d = (tms_i) ? SelectDrScan    : RunTestIdle;

            // IR Path
            SelectIrScan:   tap_state_d = (tms_i) ? TestLogicReset  : CaptureIr;

            // In this controller state, the shift register bank in the
            // Instruction Register parallel loads a pattern of fixed values on
            // the rising edge of TCK. The last two significant bits must always
            // be "01".
            CaptureIr:      tap_state_d = (tms_i) ? Exit1Ir : ShiftIr;
            
            // In this controller state, the instruction register gets connected
            // between TDI and TDO, and the captured pattern gets shifted on
            // each rising edge of TCK. The instruction available on the TDI
            // pin is also shifted in to the instruction register.
            ShiftIr:        tap_state_d = (tms_i) ? Exit1Ir : ShiftIr;
            Exit1Ir:        tap_state_d = (tms_i) ? UpdateIr : PauseIr;
            PauseIr:        tap_state_d = (tms_i) ? Exit2Ir : PauseIr;
            Exit2Ir:        tap_state_d = (tms_i) ? UpdateIr : ShiftIr;

            // In this controller state, the instruction in the instruction
            // shift register is latched to the latch bank of the Instruction
            // Register on every falling edge of TCK. This instruction becomes
            // the current instruction once it is latched.
            UpdateIr: tap_state_d = (tms_i) ? SelectDrScan : RunTestIdle;

            default:; // can't actually happen since case is full
        endcase
    end
end

// TODO: Proper implementation, because multiple clocks is not supported
// in skywater130
always @(posedge clk) begin
    if (!rst_n) begin
        tap_state_q <= RunTestIdle;
        //idcode_q    <= IdcodeValue;
        //bypass_q    <= 1'b0;
    end else if(tck_posedge) begin
        tap_state_q <= tap_state_d;
        //idcode_q    <= idcode_d;
        //bypass_q    <= bypass_d;
    end
end

assign update_o     = update_dr;
assign shift_o      = shift_dr;
assign capture_o    = capture_dr;

endmodule

`include "armleocpu_undef.vh"
