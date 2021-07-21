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

`define TIMEOUT 10000
`define SYNC_RST
`define CLK_HALF_PERIOD 5

`include "template.vh"


localparam ADDR_WIDTH = 16;
localparam ID_WIDTH = 4;
localparam DATA_WIDTH = 32;
localparam DATA_STROBES = 4;


`AXI_FULL_SIGNALS(axi_, ADDR_WIDTH, DATA_WIDTH, ID_WIDTH)


assign axi_awlen = 0;
assign axi_awsize = 3'b010; // DATA_WIDTH == 64, will need to replace
assign axi_awburst = 2'b01;
assign axi_awid = 0; // checked by formal

assign axi_wlast = 1; // checked by formal

assign axi_arlen = 0;
assign axi_arsize = 3'b010; // DATA_WIDTH == 64, will need to replace
assign axi_arburst = 2'b01;
assign axi_arid = 0; // checked by formal
// axi_rlast, axi_rid, axi_bid checked by formal

reg                     address_error; // AXI4 Response = 11
reg                     write_error; // AXI4 Response = 10
wire [ADDR_WIDTH-1:0]   address; // address
wire                    write;
wire [31:0]	            write_data;
wire [3:0]              write_byteenable;
wire                    read; // used to retire read from register
reg [31:0]	            read_data; // should not care about read request, always contains data accrding to read_address or address_error is asserted



armleocpu_axi2simple_converter #(
    .ADDR_WIDTH(ADDR_WIDTH),
    .ID_WIDTH(ID_WIDTH)
) converter (
    .*
);

//-------------AW---------------
task aw_op;
input valid;
input [ADDR_WIDTH-1:0] addr;
begin
    axi_awvalid = valid;
    axi_awaddr = addr;
end endtask

task aw_expect;
input awready;
begin
    `assert_equal(axi_awready, awready);
end endtask

//-------------W---------------

task w_op;
input valid;
input [DATA_WIDTH-1:0] wdata;
input [DATA_STROBES-1:0] wstrb;
begin
    axi_wvalid = valid;
    axi_wdata = wdata;
    axi_wstrb = wstrb;
end endtask

task w_expect;
input wready;
begin
    `assert_equal(axi_wready, wready)
end endtask

//-------------B---------------
task b_op;
input ready;
begin
    axi_bready = ready;
end endtask

task b_expect;
input valid;
input [1:0] resp;
begin
    `assert_equal(axi_bvalid, valid)
    if(valid) begin
        `assert_equal(axi_bresp, resp)
    end
end endtask

//-------------AR---------------

task ar_op; 
input valid;
input [ADDR_WIDTH-1:0] addr;
begin
    axi_arvalid = valid;
    axi_araddr = addr;
end endtask

task ar_expect;
input ready;
begin
    `assert_equal(axi_arready, ready)
end endtask

//-------------R---------------
task r_op;
input ready;
begin
    axi_rready = ready;
end endtask

task r_expect;
input valid;
input [1:0] resp;
input [DATA_WIDTH-1:0] data;
begin
    `assert_equal(axi_rvalid, valid)
    if(valid) begin
        `assert_equal(axi_rresp, resp)
        if(resp <= 2'b01)
            `assert_equal(axi_rdata, data)
    end
end endtask

//---------SIMPLE--------
task poke_simple;
input [31:0] data;
input addr_err;
input wdata_err;
begin
    read_data = data;
    address_error = addr_err;
    write_error = wdata_err;
end
endtask


task expect_simple_read;
input r;
begin
    `assert_equal(r, read);
    if(r) begin
        `assert_equal(address, axi_araddr);
    end
end
endtask


task expect_simple_write;
input w;
begin
    `assert_equal(w, write);
    if(w) begin
        `assert_equal(address, axi_awaddr);
        `assert_equal(axi_wdata, write_data)
        `assert_equal(axi_wstrb, write_byteenable)
    end
end
endtask


//-------------Others---------------
task poke_all;
input aw;
input w;
input b;

input ar;
input r;
input simple;
begin
    if(aw === 1)
        aw_op(0, 0);
    if(w === 1)
        w_op(0, 0, 0);
    if(b === 1)
        b_op(0);
    if(ar === 1)
        ar_op(0, 0);
    if(r === 1)
        r_op(0);
    if(simple === 1)
        poke_simple(0, 0, 0);
end endtask

task expect_simple_noop;
begin
    `assert_equal(read, 0)
    `assert_equal(write, 0)
end
endtask


task expect_all;
input aw;
input w;
input b;

input ar;
input r;
input simple; begin
    if(aw === 1)
        aw_expect(0);
    if(w === 1)
        w_expect(0);
    if(b === 1)
        b_expect(0, 2'bZZ);
    if(ar === 1)
        ar_expect(0);
    if(r === 1)
        r_expect(0, 2'bZZ, {DATA_WIDTH{1'bZ}});
    if(simple === 1)
        expect_simple_noop();
end endtask

task test_write;
input [ADDR_WIDTH-1:0] addr;
input addr_err;
input write_err;
begin
    reg [1:0] expected_resp;
    aw_op(1, addr);
    expect_all(1,1,1, 1,1, 1);
    @(negedge clk)

    
    w_op(1, addr, 4'hF);
    poke_simple(0, addr_err, write_err);
    
    if(addr_err)
        expected_resp = 2'b10;
    else if (write_err)
        expected_resp = 2'b11;
    else
        expected_resp = 2'b00;
    #2
    expect_all(0,0,1, 1,1, 0);
    aw_expect(1);
    w_expect(1);
    expect_simple_write(1);
    expect_simple_read(0);

    // Stalled B cycle

    @(negedge clk)

    aw_op(0, 0);
    w_op(0, 0, 0);

    b_op(0);

    expect_all(1,1, 0, 1,1, 1);
    b_expect(1, expected_resp);

    @(negedge clk)
    b_op(1);

    #2
    expect_all(1,1, 0, 1,1, 1);
    b_expect(1, expected_resp);
    @(negedge clk);
    poke_all(1,1,1, 1,1, 1);
end
endtask

task test_read;
input [ADDR_WIDTH-1:0] addr;
input [DATA_WIDTH-1:0] data;
input addr_err;
input write_err;
begin
    reg [1:0] expected_resp;
    if(addr_err)
        expected_resp = 2'b10;
    else
        expected_resp = 2'b00;
    
    ar_op(1, addr);
    poke_simple(data, addr_err, write_err);

    #2

    expect_simple_read(1);
    expect_all(1,1,1, 0,1, 0);
    @(negedge clk);

    ar_op(0, 0);
    r_op(0);
    #2
    r_expect(1, expected_resp, data);
    expect_all(1,1,1, 1,0, 1);
    @(negedge clk);


    ar_op(0, 0);
    r_op(1);
    #2
    r_expect(1, expected_resp, data);
    expect_all(1,1,1, 1,0, 1);
    @(negedge clk);
    poke_all(1,1,1, 1,1, 1);
end
endtask


initial begin
    
    @(posedge rst_n)
    poke_all(1,1,1, 1,1, 1);
    expect_all(1,1,1, 1,1, 1);

    @(negedge clk)
    test_write(100, 0, 0);


    test_write(104, 1, 0); // addr err
    test_write(104, 1, 1); // addr err is higher priority

    test_write(108, 0, 1); // write err

    
    test_read(100, 32'hFF00FF00, 0, 0); // no errors
    test_read(100, 32'hFF00FF01, 0, 0); // no errors
    test_read(100, 32'hFF00FF02, 0, 0); // no errors
    
    test_read(100, 32'hFF00FF03, 0, 1); // with write set, still should not be any errors
    test_read(100, 32'hFF00FF04, 1, 0); // addr err
    test_read(100, 32'hFF00FF05, 1, 1); // addr err, with write set, still same error
    
    @(negedge clk)
    @(negedge clk)
    @(negedge clk)
    $finish;
end

endmodule