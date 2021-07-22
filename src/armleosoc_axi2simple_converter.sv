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
// Filename:    armleocpu_axi2simple.v
// Project:	ArmleoCPU
//
// Purpose:	Converts AXI4 to simple interface.
// Deps: armleocpu_defines.vh/undef.vh for AXI4 declarations
// Notes:
//      This implementation does not fully implement AXI4, but fully implements AXI4Lite.
//      It is assumed that this controller is behind a router that converts address to zero base address
// Parameters:
//          
//          
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE


module armleosoc_axi2simple_converter (
    clk, rst_n,
    address_error,
    write_error,
    address, write, write_data, write_byteenable, read, read_data,
    `AXI_FULL_MODULE_IO_NAMELIST(axi_)
);
    localparam DATA_WIDTH = 32;
    localparam DATA_STROBES = 4;
    parameter ADDR_WIDTH = 34; // can be anything reasonable
    parameter ID_WIDTH = 4; // can be anything reasonable

    // verilator lint_off UNUSED
    `AXI_FULL_IO_CLIENT (axi_, ADDR_WIDTH, 32, ID_WIDTH)
    // verilator lint_on UNUSED

    input               clk;
    input               rst_n;

    

    input               address_error; // AXI4 Response = 11
    input               write_error; // AXI4 Response = 10

    // Simple interface
    output reg [ADDR_WIDTH-1:0]
                        address; // address
    output reg		    write;
    output [31:0]	    write_data;
    output [3:0]        write_byteenable;
    output reg		    read; // used to retire read from register
    input  [31:0]	    read_data; // should not care about read request, always contains data accrding to read_address or address_error is asserted

`ifdef DEBUG_AXI2SIMPLE_CONVERTER
`include "assert.vh"
`endif

reg [1:0] axi_bresp_nxt;
reg [1:0] axi_rresp_nxt;

reg [31:0] saved_readdata; // Used to ensure that data does not change
reg [31:0] saved_readdata_nxt; // COMB
reg [1:0] state;
reg [1:0] state_nxt;  // COMB
reg [ID_WIDTH-1:0] id;
reg [ID_WIDTH-1:0] id_nxt;  // COMB

assign axi_rid = id;
assign axi_bid = id;

localparam STATE_ACTIVE = 2'd0,
    STATE_READ_RESPOND = 2'd1,
    STATE_WRITE_RESPOND = 2'd2;
assign write_data = axi_wdata;
assign write_byteenable = axi_wstrb;

assign axi_rlast = 1;

always @(posedge clk) begin : main_always_ff
    if(!rst_n) begin
        axi_rresp <= 0;
        axi_bresp <= 0;
        state <= STATE_ACTIVE;
        saved_readdata <= 0;
        id <= 0;
    end else begin
        state <= state_nxt;
        axi_rresp <= axi_rresp_nxt;
        axi_bresp <= axi_bresp_nxt;
        saved_readdata <= saved_readdata_nxt;
        id <= id_nxt;
    end
end

always @* begin : main_always_comb
    `ifdef SIMULATION
    #1
    // Intentional delay for simulation, to make sure no infinity loop is possible
    `endif
    axi_awready = 0;
    axi_arready = 0;
    state_nxt = state;
    saved_readdata_nxt = saved_readdata;
    axi_wready = 0;
    axi_bvalid = 0;
    axi_bresp_nxt = axi_bresp;
    axi_rresp_nxt = axi_rresp;
    axi_rvalid = 0;
    //axi_rdata = saved_readdata;
    write = 0;
    read = 0;
    address = axi_araddr;
    id_nxt = id;
    
    case(state)
        STATE_ACTIVE: begin
            saved_readdata_nxt = 0;
            if(axi_awvalid && axi_wvalid) begin
                address = axi_awaddr;
                write = 1;
                id_nxt = axi_awid;
                axi_awready = 1; // Address write request accepted
                axi_wready = 1;
                if(address_error)
                    axi_bresp_nxt = 2'b10;
                else if(write_error)
                    axi_bresp_nxt = 2'b11;
                else
                    axi_bresp_nxt = 2'b00;
                state_nxt = STATE_WRITE_RESPOND;
            end else if(!axi_awvalid && axi_arvalid) begin
                address = axi_araddr;
                read = 1;
                id_nxt = axi_arid;
                axi_arready = 1;
                if(!address_error)
                    axi_rresp_nxt = 2'b00;
                else
                    axi_rresp_nxt = 2'b10;
                saved_readdata_nxt = read_data;
                state_nxt = STATE_READ_RESPOND;
            end
        end
        STATE_WRITE_RESPOND: begin
            axi_bvalid = 1;
            // BRESP is already set in previous stage
            if(axi_bready) begin
                state_nxt = STATE_ACTIVE;
            end
            
        end
        STATE_READ_RESPOND: begin
            axi_rvalid = 1;//, is set in assign below
            if(axi_rready) begin
                state_nxt = STATE_ACTIVE;
            end
        end
        default: begin
            
        end
    endcase
end

//assign axi_rvalid = state == STATE_READ_RESPOND;
assign axi_rdata = saved_readdata;

`ifdef FORMAL_RULES
reg past_valid;
initial	past_valid = 1'b0;
always @(posedge clk)
    past_valid <= 1'b1;

`define signal_valid_check(assume_assert, prefix, width, signal) \
reg axi_``prefix``valid_``prefix``ready_for_``signal``_old; \
reg [``width``-1:0] axi_``prefix````signal``_old; \
always @(posedge clk) begin \
    if ((past_valid) && (axi_``prefix``valid_``prefix``ready_for_``signal``_old)) begin \
        ``assume_assert``(axi_``prefix``valid); \
        ``assume_assert``(axi_``prefix````signal``_old == axi_``prefix````signal``); \
    end \
    axi_``prefix``valid_``prefix``ready_for_``signal``_old <= axi_``prefix``valid && !axi_``prefix``ready; \
    axi_``prefix````signal``_old <= axi_``prefix````signal``; \
end

`signal_valid_check(assume, ar, ADDR_WIDTH, addr)
`signal_valid_check(assume, ar, ID_WIDTH, id)

reg [ID_WIDTH-1:0] formal_saved_axi_arid;
always @(posedge clk) begin
    
    if(axi_arvalid && axi_arready) begin
        formal_saved_axi_arid <= axi_arid;
    end
    if(axi_rvalid) begin
        assert(axi_rid == formal_saved_axi_arid);
    end
end

`signal_valid_check(assume, aw, ADDR_WIDTH, addr)
`signal_valid_check(assume, aw, ID_WIDTH, id)


reg [ID_WIDTH-1:0] formal_saved_axi_awid;
always @(posedge clk) begin
    
    if(axi_awvalid && axi_awready) begin
        formal_saved_axi_awid <= axi_awid;
    end
    if(axi_bvalid) begin
        assert(axi_bid == formal_saved_axi_awid);
    end
end

`signal_valid_check(assume, w, DATA_WIDTH, data)
`signal_valid_check(assume, w, DATA_STROBES, strb)

`signal_valid_check(assert, b, 2, resp)


`signal_valid_check(assert, r, 2, resp)

`signal_valid_check(assert, r, DATA_WIDTH, data)

initial state = 0;

always @(posedge clk) begin
    assert(write_data === axi_wdata);
    assert(write_byteenable === axi_wstrb);

    if(rst_n) begin

    assert((state == STATE_ACTIVE) || (state == STATE_READ_RESPOND) || (state == STATE_WRITE_RESPOND));


    
    if(axi_awvalid) begin
        assume(axi_awlen == 0);
        assume(axi_awsize == 3'b010);
        assume(axi_awburst == `AXI_BURST_INCR);
        assume(!(|axi_awaddr[1:0]));
    end

    if(axi_arvalid) begin
        assume(axi_arlen == 0);
        assume(axi_arsize == 3'b010);
        assume(axi_arburst == `AXI_BURST_INCR);
        assume(!(|axi_araddr[1:0]));
    end
    if(axi_wvalid) begin
        assume(axi_wlast == 1);
    end

    if(axi_rvalid) begin
        assert(axi_rlast == 1);
    end
    end
end


`endif

endmodule

`include "armleocpu_undef.vh"