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
// Filename:    armleocpu_axi_read_router.v
// Project:	ArmleoCPU
//
// Purpose:	A basic 1-to-N AXI4 router for AR/R bus.
//      This allows multiple AXI4 clients to be connected to same bus
//      and to be mapped to different sections in memory address space
// Deps: armleocpu_defines.vh/undef.vh for AXI4 declarations
// Notes:
//      This routes only signals used by CPU and peripheral in "peripheral_src"
// Parameters:
//  See armleocpu_axi_router.v for parameter description
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleosoc_axi_read_router #(
    parameter ADDR_WIDTH = 34,
    parameter ID_WIDTH = 4,
    parameter DATA_WIDTH = 32,
    localparam DATA_STROBES = DATA_WIDTH / 8,
    localparam SIZE_WIDTH = 3,

    parameter OPT_NUMBER_OF_CLIENTS = 2,
    localparam OPT_NUMBER_OF_CLIENTS_CLOG2 = $clog2(OPT_NUMBER_OF_CLIENTS),
    
    parameter REGION_COUNT = OPT_NUMBER_OF_CLIENTS,
    parameter [REGION_COUNT * OPT_NUMBER_OF_CLIENTS_CLOG2 - 1:0] REGION_CLIENT_NUM = 0,
    parameter [REGION_COUNT * ADDR_WIDTH - 1:0] REGION_BASE_ADDRS = 0,
    parameter [REGION_COUNT * ADDR_WIDTH - 1:0] REGION_END_ADDRS = 0,
    parameter [REGION_COUNT * ADDR_WIDTH - 1:0] REGION_CLIENT_BASE_ADDRS = 0
) (
    input wire clk,
    input wire rst_n,
    


    input wire          upstream_axi_arvalid,
    output reg          upstream_axi_arready,
    input wire  [ADDR_WIDTH-1:0]
                        upstream_axi_araddr,
    input wire  [7:0]   upstream_axi_arlen,
    input wire  [SIZE_WIDTH-1:0]
                        upstream_axi_arsize,
    input wire  [1:0]   upstream_axi_arburst,
    input wire  [ID_WIDTH-1:0]
                        upstream_axi_arid,
    input wire          upstream_axi_arlock,
    input wire  [2:0]   upstream_axi_arprot,
    

    output reg          upstream_axi_rvalid,
    input  wire         upstream_axi_rready,
    output reg  [1:0]   upstream_axi_rresp,
    output reg          upstream_axi_rlast,
    output reg  [DATA_WIDTH-1:0]
                        upstream_axi_rdata,
    output reg  [ID_WIDTH-1:0]
                        upstream_axi_rid,


    output reg  [OPT_NUMBER_OF_CLIENTS-1:0]
                        downstream_axi_arvalid,
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        downstream_axi_arready,
    output reg  [ADDR_WIDTH-1:0]
                        downstream_axi_araddr,
    output wire [8-1:0]
                        downstream_axi_arlen,
    output wire [SIZE_WIDTH-1:0]
                        downstream_axi_arsize,
    output wire [2-1:0]
                        downstream_axi_arburst,
    output wire [0:0]
                        downstream_axi_arlock,
    output wire [3-1:0]
                        downstream_axi_arprot,
    output wire [ID_WIDTH-1:0]
                        downstream_axi_arid,
    

    input wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        downstream_axi_rvalid,
    output reg [OPT_NUMBER_OF_CLIENTS-1:0]
                        downstream_axi_rready,
    input wire [OPT_NUMBER_OF_CLIENTS*2-1:0]
                        downstream_axi_rresp,
    input wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        downstream_axi_rlast,
    input wire [OPT_NUMBER_OF_CLIENTS*DATA_WIDTH-1:0]
                        downstream_axi_rdata,
    input wire [OPT_NUMBER_OF_CLIENTS*ID_WIDTH-1:0]
                        downstream_axi_rid
);


localparam IDLE = 4'd0;
localparam ACTIVE = 4'd1;
localparam DECERR = 4'd2;


`DEFINE_REG_REG_NXT(OPT_NUMBER_OF_CLIENTS_CLOG2, r_client_select, r_client_select_nxt, clk)
`DEFINE_REG_REG_NXT(4, rstate, rstate_nxt, clk)
`DEFINE_REG_REG_NXT(1, ardone, ardone_nxt, clk)
`DEFINE_REG_REG_NXT(1, rdone, rdone_nxt, clk)
`DEFINE_REG_REG_NXT(ADDR_WIDTH, raddr, raddr_nxt, clk)
`DEFINE_REG_REG_NXT(ID_WIDTH, rid, rid_nxt, clk)
`DEFINE_REG_REG_NXT(8, rlen, rlen_nxt, clk)



assign downstream_axi_araddr = raddr_nxt;
assign downstream_axi_arlen = upstream_axi_arlen;
assign downstream_axi_arsize = upstream_axi_arsize;
assign downstream_axi_arburst = upstream_axi_arburst;
assign downstream_axi_arlock = upstream_axi_arlock;
assign downstream_axi_arprot = upstream_axi_arprot;
assign downstream_axi_arid = upstream_axi_arid;


integer i;
always @* begin
    `ifdef SIMULATION
    #1
    `endif
    r_client_select_nxt = r_client_select;
    
    rstate_nxt = rstate;
    ardone_nxt = ardone;
    rdone_nxt = rdone;

    raddr_nxt = raddr;
    rid_nxt = rid;
    rlen_nxt = rlen;

    upstream_axi_arready = 0;
    upstream_axi_rvalid = 0;

    for(i = 0; i < OPT_NUMBER_OF_CLIENTS; i = i + 1) begin
        downstream_axi_arvalid[i] = 0;
        downstream_axi_rready[i] = 0;
    end


    upstream_axi_rdata = downstream_axi_rdata[`ACCESS_PACKED(r_client_select, DATA_WIDTH)];
    upstream_axi_rresp = downstream_axi_rresp[`ACCESS_PACKED(r_client_select, 2)];
    upstream_axi_rid = downstream_axi_rid[`ACCESS_PACKED(r_client_select, ID_WIDTH)];
    upstream_axi_rlast = downstream_axi_rlast[r_client_select];
    
    if(!rst_n) begin
        rstate_nxt = IDLE;
        
        ardone_nxt = 0;
        rdone_nxt = 0;
    end else begin
    if(rstate == IDLE) begin
        if(upstream_axi_arvalid) begin
            for(i = 0; i < REGION_COUNT; i = i + 1) begin
                if(upstream_axi_araddr >= REGION_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)]
                    && upstream_axi_araddr < (REGION_END_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)])) begin
                    r_client_select_nxt = 
                        REGION_CLIENT_NUM[`ACCESS_PACKED(i, OPT_NUMBER_OF_CLIENTS_CLOG2)];
                    rstate_nxt = ACTIVE;
                    raddr_nxt = upstream_axi_araddr - REGION_CLIENT_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)];
                end
            end
            if(rstate_nxt != ACTIVE) begin
                upstream_axi_arready = 1;
                rstate_nxt = DECERR;
                rlen_nxt = upstream_axi_arlen;
                rid_nxt = upstream_axi_arid;
            end
        end
    end else if(rstate == ACTIVE) begin
        // AR handshake signals
        downstream_axi_arvalid[r_client_select] = upstream_axi_arvalid & !ardone;
        upstream_axi_arready = downstream_axi_arready[r_client_select] & !ardone;

        if(upstream_axi_arready && upstream_axi_arvalid)
            ardone_nxt = 1;

        // R handshake signals
        downstream_axi_rready[r_client_select] = upstream_axi_rready  & !rdone;
        upstream_axi_rvalid = downstream_axi_rvalid[r_client_select] & !rdone;

        if(downstream_axi_rvalid[r_client_select] && downstream_axi_rlast[r_client_select] && downstream_axi_rready[r_client_select]) begin
            rdone_nxt = 1;
        end

        // State signals
        if(rdone_nxt && ardone_nxt) begin
            rstate_nxt = IDLE;
            ardone_nxt = 0;
            rdone_nxt = 0;
        end
    end else if(rstate == DECERR) begin
        upstream_axi_rvalid = 1;
        upstream_axi_rresp = `AXI_RESP_DECERR;
        upstream_axi_rlast = rlen == 0;
        upstream_axi_rid = rid;
        //upstream_axi_rdata = 0;
        if(upstream_axi_rready) begin
            rlen_nxt = rlen_nxt - 1;
            if(rlen == 0) begin
                rstate_nxt = IDLE;
            end
        end
        
    end
    end // rst_n
end

endmodule

`include "armleocpu_undef.vh"