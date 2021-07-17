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
// Purpose: Generic JTAG TAP Controller
// 
// Implementation details:
// Note: IDK if MSB or LSB first sequence should be used,
//  by default all registers are set to MSB first
// Note: clk is required
// Note: clk and should be at least 8 times higher frequency than TCK
// Read more in source code
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_jtag_tap (
    clk, rst_n,

    tdo_i, ir_o, trst_no,
    update_o, shift_o, capture_o,
    tck_i, tms_i, td_i, td_o
);

    parameter IR_LENGTH = 5;

    parameter IDCODE_VALUE = 32'h80000001;
    // LSB of IDCODE_VALUE should be set to 1

    input  wire         clk;
    input  wire         rst_n;  // system wide reset

    input  wire                 tdo_i;      // Custom DR logic tdo input. Redirected to output,
    // should contain valid data after capture_o pulse. MUX is done by connected logic, depending on ir_o
    output wire [IR_LENGTH-1:0] ir_o;       // Instruction register output, used to generate selects

    output reg                  trst_no; // Singular cycle negative pulse per test reset, sync to clk
    output reg                  capture_o; // Singular cycle pulse per capture, sync to clk, first to pulse
    output reg                  shift_o; // Singular cycle pulse per shift, sync to clk, second to pulse, multiple pulses
    output reg                  update_o; // Singular cycle pulse per update, sync to clk, pulse when write is complete

    input  wire         tck_i;    // JTAG test clock pad, shared between tap and DR logic
    input  wire         tms_i;    // JTAG test mode select pad
    input  wire         td_i;     // JTAG test data input pad
    output reg          td_o;     // JTAG test data output pad
    // Note: TRST is optional by JTAG, so it's not implemented
    // 5 cycles of tms = 1 is equivalent to TRST and is refered as soft reset
    // Note: JTAG_TAP DOES NOT GENERATE any reset signals. trst_no is only used to "reset" DR logic

// Implementation details:
//      TAP registers data on risign edge of tck_i, but
//          tck_i is being registered on clk
//          so clk should be at least 4 times higher frequency than tck_i
//      TAP can be reseted by rst_n

reg tck_i_registered;
always @(posedge clk) begin
    if(!rst_n) begin
        tck_i_registered <= 0;
    end else begin
        tck_i_registered <= tck_i;
    end
end

reg [1:0] tck_i_past_values;
wire tck_posedge = tck_i_past_values == 2'b01;
wire tck_negedge = tck_i_past_values == 2'b10;

always @(posedge clk) begin
    if(!rst_n) begin
        tck_i_past_values   <= 2'b00;
    end else begin
        tck_i_past_values   <= {tck_i_past_values[0], tck_i_registered};
    end
end


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

`ifdef DEBUG_JTAG_TAP
reg [32*8-1:0] tap_state_q_ascii;
always @* begin
    case(tap_state_q)
        TestLogicReset: tap_state_q_ascii = "TestLogicReset";
        RunTestIdle:    tap_state_q_ascii = "RunTestIdle";

        // DR Path
        SelectDrScan:   tap_state_q_ascii = "SelectDrScan";
        CaptureDr:      tap_state_q_ascii = "CaptureDr";
        ShiftDr:        tap_state_q_ascii = "ShiftDr";
        Exit1Dr:        tap_state_q_ascii = "Exit1Dr";
        PauseDr:        tap_state_q_ascii = "PauseDr";
        Exit2Dr:        tap_state_q_ascii = "Exit2Dr";
        UpdateDr:       tap_state_q_ascii = "UpdateDr";

        // IR Path
        SelectIrScan:   tap_state_q_ascii = "SelectIrScan";
        CaptureIr:      tap_state_q_ascii = "CaptureIr";
        
        ShiftIr:        tap_state_q_ascii = "ShiftIr";
        Exit1Ir:        tap_state_q_ascii = "Exit1Ir";
        PauseIr:        tap_state_q_ascii = "PauseIr";
        Exit2Ir:        tap_state_q_ascii = "Exit2Ir";
        UpdateIr:       tap_state_q_ascii = "UpdateIr";
    endcase
end
`endif

localparam [IR_LENGTH-1:0] BYPASS0 = 'h0;
localparam [IR_LENGTH-1:0] IDCODE  = 'h1;
localparam [IR_LENGTH-1:0] BYPASS1 = {IR_LENGTH{1'b1}};

// ----------------
// IR logic
// ----------------

// shift register that accepts data from JTAG
reg [IR_LENGTH-1:0] jtag_ir_shift_d, jtag_ir_shift_q;
// IR register -> register on update_ir signal
reg [IR_LENGTH-1:0] jtag_ir_d, jtag_ir_q;

always @* begin
    jtag_ir_shift_d = jtag_ir_shift_q;
    jtag_ir_d       = jtag_ir_q;

    // IR shift register
    if (shift_ir) begin
        jtag_ir_shift_d = {td_i, jtag_ir_shift_q[IR_LENGTH-1:1]};
    end

    // capture IR register
    if (capture_ir) begin
        jtag_ir_shift_d = IR_LENGTH'(4'b0101);
    end

    // update IR register
    if (update_ir) begin
        jtag_ir_d = IR_LENGTH'(jtag_ir_shift_q);
    end
end

always @(posedge clk) begin
    if (!trst_no || !rst_n) begin
        jtag_ir_shift_q <= '0;
        jtag_ir_q       <= IDCODE;
    end else begin
        jtag_ir_shift_q <= jtag_ir_shift_d;
        jtag_ir_q       <= jtag_ir_d;
    end
end


reg tdo_mux;
reg [31:0] idcode_d, idcode_q;
reg        idcode_select;
reg        bypass_select;

reg        bypass_d, bypass_q;  // this is a 1-bit register

assign ir_o = jtag_ir_q;

always @* begin
    idcode_select  = 1'b0;
    bypass_select  = 1'b0;
    case (jtag_ir_q)
        BYPASS0:   bypass_select  = 1'b1;
        IDCODE:    idcode_select  = 1'b1;
        BYPASS1:   bypass_select  = 1'b1;
        default:   bypass_select  = 1'b1;
    endcase


    idcode_d = idcode_q;
    bypass_d = bypass_q;

    if (capture_dr) begin
        if (idcode_select) idcode_d = IDCODE_VALUE;
        if (bypass_select) bypass_d = 1'b0;
    end

    if (shift_dr) begin
        if (idcode_select)  idcode_d = {td_i, 31'(idcode_q >> 1)};
        if (bypass_select)  bypass_d = td_i;
    end

    if (shift_ir) begin
        tdo_mux = jtag_ir_shift_q[0];
        // here we are shifting the DR register
    end else begin
        case (jtag_ir_q)
            IDCODE:         tdo_mux = idcode_q[0];   // Reading ID code
            BYPASS0:        tdo_mux = bypass_q;
            BYPASS1:        tdo_mux = bypass_q;
            default:        tdo_mux = tdo_i; // TDO is connected to registers outside
        endcase
    end
end


always @(posedge clk) begin : p_tdo_regs
    if (!rst_n) begin
        td_o     <= 1'b0;
    end else if(tck_negedge) begin
        td_o     <= tdo_mux;
    end
    if (!rst_n) begin
        bypass_q <= 0;
        idcode_q <= 0;
    end else if(tck_posedge) begin
        bypass_q <= bypass_d;
        idcode_q <= idcode_d;
    end
end

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

    trst_no     = 1'b1;
    update_dr   = 1'b0;
    shift_dr    = 1'b0;
    capture_dr  = 1'b0;

    capture_ir  = 1'b0;
    shift_ir    = 1'b0;
    update_ir   = 1'b0;
    //pause_ir  = 1'b0;

    tap_state_d = tap_state_q;

    // Note: Only output when posedge tck
    // Note: UpdateIR is set for one cycle in negedge tck
    case(tap_state_q)
        TestLogicReset: trst_no     = !tck_posedge;

        // DR Path
        UpdateDr:       update_dr   = tck_posedge;
        ShiftDr:        shift_dr    = tck_posedge;
        CaptureDr:      capture_dr  = tck_posedge;

        // IR Path
        CaptureIr:      capture_ir  = tck_posedge;
        ShiftIr:        shift_ir    = tck_posedge;
        UpdateIr:       update_ir   = tck_negedge;
        //PauseIr:      pause_ir    = 1'b1; // unused
    endcase


    if(tck_posedge) begin
        
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

// Multiple clocks is not supported in skywater130 flow
// Instead clk is used and all signals are synchronized to clk

always @(posedge clk) begin
    if (!rst_n) begin
        tap_state_q <= RunTestIdle;
    end else if(tck_posedge) begin
        tap_state_q <= tap_state_d;
    end
end

assign update_o     = update_dr;
assign shift_o      = shift_dr;
assign capture_o    = capture_dr;

endmodule

`include "armleocpu_undef.vh"
