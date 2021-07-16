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


`define TIMEOUT 10000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"



`define TCK_HALF_PERIOD 50


localparam IR_LENGTH = 5;
localparam IDCODE_VALUE = 32'h80000001;
reg tck_i;
reg tms_i;
reg td_i;
wire td_o;
wire tdo_oe_o;


wire rst_output_n;
reg tdo_i;
wire [4:0] ir_o;
wire trst_no;
wire update_o;
wire shift_o;
wire capture_o;

armleocpu_jtag_tap #(
    .IR_LENGTH(IR_LENGTH),
    .IDCODE_VALUE(IDCODE_VALUE)
) tap (
    .*
);

task cycle;
cycle_start(); cycle_end();
endtask

task cycle_start;
    tck_i = 0;
    #`TCK_HALF_PERIOD;
endtask

task cycle_end;
    tck_i = 1;
    #`TCK_HALF_PERIOD;
endtask

task write_tms;
input tms;
begin
    tms_i = tms;
    cycle();
end
endtask

task go_to_reset;
    write_tms(1);
    write_tms(1);
    write_tms(1);
    write_tms(1);
    write_tms(1);
    write_tms(1);
endtask
    

task write_bits;
input [7:0] len;
input [255:0] val;
input tms_last;
begin
    integer i;
    for (i = 0; i < len; i++) begin
        td_i <= val[i];
        if (i == (len - 1)) tms_i <= tms_last;
        cycle();
    end
end
endtask


task readwrite_bits;
input [7:0] len;
input [255:0] inval;
output [255:0] outval;
input tms_last;
begin
    integer i;
    reg [255:0] readdata;
    for (i = 0; i < len; i++) begin
        td_i <= inval[i];
        if (i == (len - 1)) tms_i <= tms_last;
        cycle_start();
        readdata <= {readdata[254:0], td_o};
        cycle_end();
    end
    reverse_bits(len, readdata, outval);
    $display("%b", outval[31:0]);
end
endtask

task reverse_bits;
input [7:0] len;
input [255:0] inval;
output reg [255:0] outval;
begin
    integer i;
    for(i = 0; i < len; i = i + 1) begin
        outval[i] = inval[len-i-1];
    end
end
endtask

task set_ir;
input [IR_LENGTH-1:0] opcode;
begin
    // check whether IR is already set to the right value
    write_tms(1); // select DR scan
    write_tms(1); // select IR scan
    write_tms(0); // capture IR
    write_tms(0); // shift IR
    write_bits(5, opcode, 1);
    write_tms(1); // update IR
    write_tms(0); // run test idle
end
endtask

task shift_dr;
    write_tms(1); // select DR scan
    write_tms(0); // capture DR
    write_tms(0); // shift DR
endtask

task update_dr;
input exit_1_dr;
begin
    // depending on the state `exit_1_dr` is already reached when shifting data (`tms_on_last`).
    if (exit_1_dr) write_tms(1); // exi 1 DR
    write_tms(1); // update DR
    write_tms(0); // run test idle
end
endtask

task get_idcode;
output reg [32:0] idcode;
begin
    readwrite_dr(1, 32, 0, idcode);
end
endtask

task readwrite_dr;
input [IR_LENGTH-1:0] ir;
input [7:0] len;
input [255:0] data_in;
output [255:0] data_out;
begin
    reg [31:0] read_data;
    set_ir(ir);
    shift_dr();
    readwrite_bits(len, data_in, read_data, 1'b0);
    update_dr(1'b1);
    data_out = read_data;
end
endtask


reg [8:0] somereg_shift;
reg [8:0] somereg;
always @(posedge clk) begin
    if(!rst_n) begin
        somereg_shift <= 0;
        somereg <= 100;
    end else begin
        if(somereg_select && capture_o) begin
            somereg_shift <= somereg;
        end
        if(somereg_select && shift_o) begin
            somereg_shift <= {1'b0, somereg_shift[8:1]};
        end
        if(somereg_select && update_o) begin
            somereg <= somereg_shift;
        end
    end
end

wire somereg_select = ir_o == 2;

always @* begin
    if(somereg_select)
        tdo_i = somereg_shift[0];
    else
        tdo_i = 0;
end


initial begin
    reg [31:0] idcode;
    reg [31:0] read_data;
    tck_i = 0;
    tms_i = 1;
    td_i = 0;
    tdo_i = 1;

    @(posedge rst_n);
    go_to_reset();
    write_tms(0);

    get_idcode(idcode);
    $display("Idcode: 0x%x", idcode);

    `assert_equal(idcode, IDCODE_VALUE);

    readwrite_dr(2, 9, 9'h1FF, read_data);
    $display(read_data);
    
    // Currently in idle state

    @(negedge clk);
    @(negedge clk);
    @(negedge clk);
    @(negedge clk);
    @(negedge clk);
    @(negedge clk);
    @(negedge clk);
    @(negedge clk);
    $finish;
end

endmodule