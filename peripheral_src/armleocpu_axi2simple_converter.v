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


module armleocpu_axi2simple_converter #(
    localparam DATA_WIDTH = 32, // Fixed 32
    localparam DATA_STROBES = DATA_WIDTH / 8, // fixed
    parameter ADDR_WIDTH = 34, // can be anything reasonable
    parameter ID_WIDTH = 4, // can be anything reasonable
    localparam SIZE_WIDTH = 3 // fixed
) (
    input               clk,
    input               rst_n,

    input wire          axi_awvalid,
    output reg          axi_awready,
    input wire  [ID_WIDTH-1:0]
                        axi_awid,
    input wire  [ADDR_WIDTH-1:0]
                        axi_awaddr,
    input wire  [7:0]   axi_awlen,
    input wire  [SIZE_WIDTH-1:0]
                        axi_awsize,
    input wire  [1:0]   axi_awburst,
    

    // AXI W Bus
    input wire          axi_wvalid,
    output reg          axi_wready,
    input wire  [DATA_WIDTH-1:0]
                        axi_wdata,
    input wire  [DATA_STROBES-1:0]
                        axi_wstrb,
    input wire          axi_wlast,
    
    // AXI B Bus
    output reg          axi_bvalid,
    input wire          axi_bready,
    output reg [1:0]    axi_bresp,
    output reg [ID_WIDTH-1:0]
                        axi_bid,
    
    
    input wire          axi_arvalid,
    output reg          axi_arready,
    input wire  [ID_WIDTH-1:0]
                        axi_arid,
    input wire  [ADDR_WIDTH-1:0]
                        axi_araddr,
    input wire  [7:0]   axi_arlen,
    input wire  [SIZE_WIDTH-1:0]
                        axi_arsize,
    input wire  [1:0]   axi_arburst,
    
    

    output reg          axi_rvalid,
    input wire          axi_rready,
    output reg  [1:0]   axi_rresp,
    output reg          axi_rlast,
    output reg  [DATA_WIDTH-1:0]
                        axi_rdata,
    output reg [ID_WIDTH-1:0]
                        axi_rid,

    input               address_error, // AXI4 Response = 11
    input               write_error, // AXI4 Response = 10

    // Simple interface
    output reg [ADDR_WIDTH-1:0]
                        address, // address
    output reg		    write,
    output [31:0]	    write_data,
    output [3:0]        write_byteenable,
    output reg		    read, // used to retire read from register
    input  [31:0]	    read_data // should not care about read request, always contains data accrding to read_address or address_error is asserted
);

reg [63:0] saved_readdata; // Used to ensure that data does not change
reg [63:0] saved_readdata_nxt; // COMB
reg [1:0] state;
reg [1:0] state_nxt;  // COMB

localparam STATE_ACTIVE = 2'd0,
    STATE_READ_RESPOND = 2'd1,
    STATE_WRITE_RESPOND = 2'd2;
assign write_data = AXI_WDATA;
assign write_byteenable = AXI_WSTRB;

always @(posedge clk) begin : main_always_ff
    if(!rst_n) begin
        AXI_RRESP <= 0;
        AXI_BRESP <= 0;
        state <= STATE_ACTIVE;
    end else begin
        state <= state_nxt;
        AXI_RRESP <= AXI_RRESP_nxt;
        AXI_BRESP <= AXI_BRESP_nxt;
        saved_readdata <= saved_readdata_nxt;
        `ifdef DEBUG_AXI2SIMPLE_CONVERTER
            `assert_equal((state == STATE_ACTIVE || state == STATE_READ_RESPOND || state == STATE_WRITE_RESPOND), 1)
        `endif
    end
end

always @* begin : main_always_comb
    AXI_AWREADY = 0;
    AXI_ARREADY = 0;
    state_nxt = state;
    saved_readdata_nxt = saved_readdata;
    AXI_WREADY = 0;
    AXI_BVALID = 0;
    AXI_BRESP_nxt = AXI_BRESP;
    AXI_RRESP_nxt = AXI_RRESP;
    AXI_RVALID = 0;
    AXI_RDATA = saved_readdata;
    write = 0;
    read = 0;
    address = AXI_ARADDR;
    case(state)
        STATE_ACTIVE: begin
            if(AXI_AWVALID && AXI_WVALID) begin
                address = AXI_AWADDR;
                write = 1;

                AXI_AWREADY = 1; // Address write request accepted
                AXI_WREADY = 1;
                AXI_BRESP_nxt = 2'b10;
                if(address_error)
                    AXI_BRESP_nxt = 2'b10;
                else if(write_error)
                    AXI_BRESP_nxt = 2'b11;
                else if(AXI_AWADDR[1:0] != 2'b00)
                    AXI_BRESP_nxt = 2'b10;
                else
                    AXI_BRESP_nxt = 2'b00;
                
                state_nxt = STATE_WRITE_RESPOND;
            end else if(!AXI_AWVALID && AXI_ARVALID) begin
                address = AXI_ARADDR;
                read = 1;

                AXI_ARREADY = 1;
                if(AXI_ARADDR[1:0] == 2'b00 && !address_error)
                    AXI_RRESP_nxt = 2'b00;
                else
                    AXI_RRESP_nxt = 2'b10;
                saved_readdata_nxt = read_data;
                state_nxt = STATE_READ_RESPOND;
            end
        end
        STATE_WRITE_RESPOND: begin
            AXI_BVALID = 1;
            // BRESP is already set in previous stage
            if(AXI_BREADY) begin
                state_nxt = STATE_ACTIVE;
            end
            
        end
        STATE_READ_RESPOND: begin
            AXI_RVALID = 1;
            if(AXI_RREADY) begin
                state_nxt = STATE_ACTIVE;
            end
            AXI_RDATA = saved_readdata;
        end
        default: begin
            
        end
    endcase
end


endmodule

`include "armleocpu_undef.vh"