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

`define TIMEOUT 2000000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"


localparam ADDR_WIDTH = 16;
localparam ID_WIDTH = 4;

reg axi_awvalid;
wire axi_awre
reg axi_arlock;
reg [2:0] axi_arprot;ady;
reg [ADDR_WIDTH-1:0] axi_awaddr;
reg [7:0] axi_awlen;
reg [2:0] axi_awsize;
reg [1:0] axi_awburst;
reg [ID_WIDTH-1:0] axi_awid;

reg axi_wvalid;
wire axi_wready;
reg [DATA_WIDTH-1:0] axi_wdata;
reg [DATA_STROBES-1:0] axi_wstrb;
reg axi_wlast;

wire axi_bvalid;
reg axi_bready;
wire [1:0] axi_bresp;
wire [ID_WIDTH-1:0] axi_bid;


reg axi_arvalid;
wire axi_arready;
reg [ADDR_WIDTH-1:0] axi_araddr;
reg [7:0] axi_arlen;
reg [2:0] axi_arsize;
reg [1:0] axi_arburst;
reg [ID_WIDTH-1:0] axi_arid;

wire axi_rvalid;
reg axi_rready;
wire [1:0] axi_rresp;
wire [DATA_WIDTH-1:0] axi_rdata;
wire [ID_WIDTH-1:0] axi_rid;
wire axi_rlast;

armleocpu_axi2simple_converter #(
    .ADDR_WIDTH(ADDR_WIDTH),
    .ID_WIDTH(ID_WIDTH)
) converter (
    .*
);

//-------------AW---------------
task aw_noop; begin
    axi_awvalid = 0;
end endtask

task aw_op;
input [ADDR_WIDTH-1:0] addr;
input [2:0] id;
input lock;
begin
    axi_awvalid = 1;
    axi_awaddr = addr;
    axi_awlen = 0;
    axi_awsize = 2; // 4 bytes
    axi_awburst = 2'b01; // Increment
    axi_awid = id;
end endtask

task aw_expect;
input awready;
begin
    `assert_equal(axi_awready, awready);
end endtask

//-------------W---------------
task w_noop; begin
    axi_wvalid = 0;
end endtask

task w_op;
input [DATA_WIDTH-1:0] wdata;
input [DATA_STROBES-1:0] wstrb;
begin
    axi_wvalid = 1;
    axi_wdata = wdata;
    axi_wstrb = wstrb;
    axi_wlast = 1;
end endtask

task w_expect;
input wready;
begin
    `assert_equal(axi_wready, wready)
end endtask

//-------------B---------------
task b_noop; begin
    axi_bready = 0;
end endtask

task b_expect;
input valid;
input [1:0] resp;
input [3:0] id;
begin
    `assert_equal(axi_bvalid, valid)
    if(valid) begin
        `assert_equal(axi_bresp, resp)
    end
end endtask

//-------------AR---------------
task ar_noop; begin
    axi_arvalid = 0;
end endtask

task ar_op; 
input [ADDR_WIDTH-1:0] addr;
input [3:0] id;
input [1:0] burst;
input [7:0] len;
input lock;
begin
    axi_arvalid = 1;
    axi_araddr = addr;
    axi_arlen = len;
    axi_arsize = 2; // 4 bytes
    axi_arburst = burst; // Increment
    axi_arid = id;
end endtask

task ar_expect;
input ready;
begin
    `assert_equal(axi_arready, ready)
end endtask

//-------------R---------------
task r_noop; begin
    axi_rready = 0;
end endtask

task r_expect;
input valid;
input [1:0] resp;
input [31:0] data;
input [3:0] id;
input last;
begin
    `assert_equal(axi_rvalid, valid)
    if(valid) begin
        `assert_equal(axi_rresp, resp)
        if(resp <= 2'b01)
            `assert_equal(axi_rdata, data)
        `assert_equal(axi_rid, id)
        `assert_equal(axi_rlast, last)
    end
end endtask


//-------------Others---------------
task poke_all;
input aw;
input w;
input b;

input ar;
input r; begin
    if(aw === 1)
        aw_noop();
    if(w === 1)
        w_noop();
    if(b === 1)
        b_noop();
    if(ar === 1)
        ar_noop();
    if(r === 1)
        r_noop();
end endtask

task expect_all;
input aw;
input w;
input b;

input ar;
input r; begin
    if(aw === 1)
        aw_expect(0);
    if(w === 1)
        w_expect(0);
    if(b === 1)
        b_expect(0, 2'bZZ, 4'bZZZZ);
    if(ar === 1)
        ar_expect(0);
    if(r === 1)
        r_expect(0, 2'bZZ, 32'hZZZZ_ZZZZ, 2'bZZ, 1'bZ);
end endtask


initial begin
    
    @(posedge rst_n)
    poke_all(1,1,1, 1,1);
    @(negedge clk)
    
    @(negedge clk)
    $finish;
end

endmodule