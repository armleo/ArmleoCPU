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

module armleocpu_fetch ();

    parameter [0:0] OPT_HOST = 1;

    parameter BUFFERS = 4;
    parameter UPSTREAM_ID_WIDTH = 4;
    parameter DOWNSTREAM_ID_WIDTH = 8;

    localparam ADDR_WIDTH = 32;
    localparam DATA_WIDTH = 32;
    localparam DATA_STROBES = DATA_WIDTH / 8;
    localparam SIZE_WIDTH = 3;

    input                   clk,
    input                   rst_n,

    // All inputs/outputs are registered
    // client port, connects to CPU or other host
    input wire          io_upstream_axi_awvalid,
    output wire         io_upstream_axi_awready,
    input wire  [ADDR_WIDTH-1:0]
                        io_upstream_axi_awaddr,
    input wire  [7:0]   io_upstream_axi_awlen,
    input wire  [SIZE_WIDTH-1:0]
                        io_upstream_axi_awsize,
    input wire  [1:0]   io_upstream_axi_awburst,
    input wire          io_upstream_axi_awlock,
    input wire  [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_awid,
    input wire  [2:0]   io_upstream_axi_awprot,

    // AXI W Bus
    input wire          io_upstream_axi_wvalid,
    output wire         io_upstream_axi_wready,
    input wire  [DATA_WIDTH-1:0]
                        io_upstream_axi_wdata,
    input wire  [DATA_STROBES-1:0]
                        io_upstream_axi_wstrb,
    input wire          io_upstream_axi_wlast,
    
    // AXI B Bus
    output wire         io_upstream_axi_bvalid,
    input wire          io_upstream_axi_bready,
    output wire [1:0]   io_upstream_axi_bresp,
    output wire [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_bid,
    
    
    input wire          io_upstream_axi_arvalid,
    output reg          io_upstream_axi_arready,
    input wire  [ADDR_WIDTH-1:0]
                        io_upstream_axi_araddr,
    input wire  [7:0]   io_upstream_axi_arlen,
    input wire  [SIZE_WIDTH-1:0]
                        io_upstream_axi_arsize,
    input wire  [1:0]   io_upstream_axi_arburst,
    input wire  [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_arid,
    input wire          io_upstream_axi_arlock,
    input wire  [2:0]   io_upstream_axi_arprot,
    

    output reg          io_upstream_axi_rvalid,
    input  wire         io_upstream_axi_rready,
    output reg  [1:0]   io_upstream_axi_rresp,
    output reg          io_upstream_axi_rlast,
    output reg  [DATA_WIDTH-1:0]
                        io_upstream_axi_rdata,
    output reg  [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_rid,

    
    // Host port, connects to peripheral
    // All signals are registered


    // AXI AW Bus
    output wire  [1-1:0]
                        io_downstream_axi_awvalid,
    input  wire [1-1:0]
                        io_downstream_axi_awready,
    output wire [ADDR_WIDTH-1:0]
                        io_downstream_axi_awaddr,
    output wire [8-1:0]
                        io_downstream_axi_awlen,
    output wire [SIZE_WIDTH-1:0]
                        io_downstream_axi_awsize,
    output wire [2-1:0]
                        io_downstream_axi_awburst,
    output wire [0:0]   io_downstream_axi_awlock,
    output wire [3-1:0]
                        io_downstream_axi_awprot,
    output wire [DOWNSTREAM_ID_WIDTH-1:0]
                        io_downstream_axi_awid,

    // AXI W Bus
    output reg [1-1:0] 
                        io_downstream_axi_wvalid,
    input  wire [1-1:0] 
                        io_downstream_axi_wready,
    output wire [DATA_WIDTH-1:0]
                        io_downstream_axi_wdata,
    output wire [DATA_STROBES-1:0]
                        io_downstream_axi_wstrb,
    output wire [0:0]
                        io_downstream_axi_wlast,
    
    // AXI B Bus
    input  wire [1-1:0]
                        io_downstream_axi_bvalid,
    output reg [1-1:0]
                        io_downstream_axi_bready,
    input  wire [2-1:0]
                        io_downstream_axi_bresp,
    input wire [DOWNSTREAM_ID_WIDTH-1:0]
                        io_downstream_axi_bid,


    output reg  [1-1:0]
                        io_downstream_axi_arvalid,
    input  wire [1-1:0]
                        io_downstream_axi_arready,
    output reg  [ADDR_WIDTH-1:0]
                        io_downstream_axi_araddr,
    output wire [8-1:0]
                        io_downstream_axi_arlen,
    output wire [SIZE_WIDTH-1:0]
                        io_downstream_axi_arsize,
    output wire [2-1:0]
                        io_downstream_axi_arburst,
    output wire [0:0]
                        io_downstream_axi_arlock,
    output wire [3-1:0]
                        io_downstream_axi_arprot,
    output wire [DOWNSTREAM_ID_WIDTH-1:0]
                        io_downstream_axi_arid,
    

    input wire [1-1:0]
                        io_downstream_axi_rvalid,
    output reg [1-1:0]
                        io_downstream_axi_rready,
    input wire [2-1:0]
                        io_downstream_axi_rresp,
    input wire [1-1:0]
                        io_downstream_axi_rlast,
    input wire [DATA_WIDTH-1:0]
                        io_downstream_axi_rdata,
    input wire [DOWNSTREAM_ID_WIDTH-1:0]
                        io_downstream_axi_rid,


    // CHIP2CHIP interface
    // The input/output clk is "clk". It is assumed that on posedge clk, in_data is valid
    // The input will be registered before usage
    // and all outputs is registered to maximize perfomance
    input wire io_in_valid,
    output wire io_in_ready,
    input wire [7:0] io_in_data,
    
    output wire [7:0] io_out_data,
    output wire io_out_valid,
    input wire io_out_ready
);


parameter SYNC_RESP_TIMEOUT = 16;


// Chip 2 chip interface
// Two uni directional buses
// When ready && valid then data is accepted

// No need for initialization sequence because both devices exit from reset
// At the same time

// AW packet = {CHIP2CHIP_OPCODE_AW32, io_upstream_axi_awaddr, 8 + 32 = 40 bits
// {5'd0, io_upstream_axi_awburst, io_upstream_axi_awlock}, // 8 bits
// {io_upstream_axi_awlen}, // 8 bits
// {2'b00, io_upstream_axi_awprot, io_upstream_axi_awsize}} // 8 bits
// Total = 64 bit

// Then W packet is sent
// W packet = {CHIP2CHIP_OPCODE_W32, Multiple sections of: {3'b0, io_upstream_axi_wlast, io_upstream_axi_wstrb}, io_upstream_axi_wdata}
// Note: W packet is sent right after AW packet
// Total = 8 + 8 + 32 bits = 48 bits
// Host will 
// Then B packet is recved
// B packet = {CHIP2CHIP_OPCODE_B, 6'd0, io_upstream_axi_bresp}
// 16 bits

// Host sends SYNC_REQ to ask for state by client
// Client then sends SYNC_RESP opcode to
//      To let host know that it has buffer for next transaction
// Client might set HAS_BUFFER bits and HAS_TRANSACTION bits
//      Host is allowed to issue new transaction even if HAS_TRANSACTION bit
//      Is set by HOST
// If host decides to accept the transaction then it sends CLIENT_ACCEPT
// After that host accepts data and sends them to downstream axi
// 

// Sync req structure {SYNC_REQ, 8'd0};
// If start bit is set then it is required to respond by client in time



// Sync resp structure {SYNC_RESP, {6'd0, HAS_BUFFER, HAS_TRANSACTION}};

// CLIENT_ACCEPT structure {CLIENT_ACCEPT}

// For reading
// AR packet = {CHIP2CHIP_OPCODE_AR32, io_upstream_axi_araddr, 8 + 32 = 40 bits
// {5'd0, io_upstream_axi_arburst, io_upstream_axi_arlock}, // 8 bits
// {io_upstream_axi_arlen}, // 8 bits
// {2'b00, io_upstream_axi_arprot, io_upstream_axi_arsize}} // 8 bits

// R response packet
// {CHIP2CHIP_OPCODE_R32, {5'd0, io_upstream_axi_rresp, io_upstream_axi_rlast}, io_upstream_axi_rdata

// Client then sends HAS_BUFFER

// Host will wait for TRANSACTION_BUFFER_RELEASE if complete
// 

// OUTSTANDING_TRANSACTIONS



reg [7:0] state;

reg [15:0] timeout;

localparam STATE_IDLE = 4'd0;

always @(posedge clk) begin
    if(!rst_n) begin
        state <= STATE_SYNC_REQ0;
    end else begin
        case(state)
            STATE_SYNC_REQ0: begin
                if(OPT_HOST) begin // HOST
                    if(out_ready) begin
                        state <= STATE_SYNC_REQ1;
                    end
                end else begin // CLIENT
                    if(in_valid) begin
                        `ifdef DEBUG_CHIP2CHIP
                        assume(in_data == `CHIP2CHIP_OPCODE_SYNC_REQ);
                        `endif
                        state <= STATE_SYNC_REQ1;
                    end
                end
            end
            STATE_SYNC_REQ1: begin
                if(OPT_HOST) begin // HOST
                    if(out_ready) begin
                        state <= STATE_SYNC_RESP0;
                    end
                end else begin  // CLIENT
                    if(in_valid) begin
                        `ifdef DEBUG_CHIP2CHIP
                        assume();
                        `endif
                    end
                end
            end
        endcase
    end
end

always @* begin
    case(state)
        STATE_SYNC_REQ0: begin
            if(OPT_HOST) begin // HOST
                // Send sync request
                out_valid = 1;
                out_data = `CHIP2CHIP_OPCODE_SYNC_REQ;
                
            end else begin // CLIENT
                in_ready = 1;
            end
        end
        STATE_SYNC_REQ1: begin
            out_valid = 1;
            out_data = 0;
        end
        STATE_SYNC_RESP0: begin
            in_ready = 1;
            if(in_valid) begin
                if(in_data != `CHIP2CHIP_OPCODE_SYNC_RESP) begin

                end
            end
            synced_nxt = 0;
            if(in_data[1]) begin
                state_nxt = STATE_IDLE;
            end
            sync_counter_nxt = 16;
            in_data[0]
        end
        STATE_IDLE: begin
            if(OPT_HOST) begin
                if(upstream_axi_awvalid) begin
                    
                end else if(upstream_axi_arvalid) begin

                end else begin
                    state_nxt = STATE_SYNC;
                end
            end
        end
        
        STATE_SYNC_RESP0: begin
            in_ready = 1;
            if(in_valid && (in_data == `CHIP2CHIP_OPCODE_SYNC_RESP)) begin
                
            end else if(timeout == SYNC_RESP_TIMEOUT) begin

            end
        end
end

endmodule


`include "armleocpu_undef.vh"