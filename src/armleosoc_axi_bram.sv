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
//  
////////////////////////////////////////////////////////////////////////////////


`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

// TODO: Remove below

// verilator lint_off UNUSED
// verilator lint_off WIDTH
// verilator lint_off CASEINCOMPLETE

module armleosoc_axi_bram(
    clk, rst_n,

    axi_awvalid, axi_awready, axi_awaddr, axi_awlen, axi_awburst, axi_awsize, axi_awid,
    axi_wvalid, axi_wready, axi_wdata, axi_wstrb, axi_wlast,
    axi_bvalid, axi_bready, axi_bresp, axi_bid,

    axi_arvalid, axi_arready, axi_araddr, axi_arlen, axi_arsize, axi_arburst, axi_arid,
    axi_rvalid, axi_rready, axi_rresp, axi_rlast, axi_rdata, axi_rid
    

);
parameter DEPTH = 1024; // 1024 x 32 default
parameter ADDR_WIDTH = 32; // Determines the size of addr bus. If memory outside this peripheral is accessed BRESP/RRESP is set to DECERR
parameter ID_WIDTH = 4;
localparam SIZE_WIDTH = 3;
parameter DATA_WIDTH = 32; // 32 or 64
localparam DATA_STROBES = DATA_WIDTH / 8;
localparam DEPTH_CLOG2 = $clog2(DEPTH);


    input wire          clk;
    input wire          rst_n;

    input wire          axi_awvalid;
    output reg          axi_awready;
    input wire  [ADDR_WIDTH-1:0]
                        axi_awaddr;
    input wire  [7:0]   axi_awlen;
    input wire  [SIZE_WIDTH-1:0]
                        axi_awsize;
    input wire  [1:0]   axi_awburst;
    input wire  [ID_WIDTH-1:0]
                        axi_awid;

    // AXI W Bus
    input wire          axi_wvalid;
    output reg          axi_wready;
    input wire  [DATA_WIDTH-1:0]
                        axi_wdata;
    input wire  [DATA_STROBES-1:0]
                        axi_wstrb;
    input wire          axi_wlast;
    
    // AXI B Bus
    output reg          axi_bvalid;
    input wire          axi_bready;
    output reg [1:0]    axi_bresp;
    output reg [ID_WIDTH-1:0]
                        axi_bid;
    
    
    input wire          axi_arvalid;
    output reg          axi_arready;
    input wire  [ADDR_WIDTH-1:0]
                        axi_araddr;
    input wire  [7:0]   axi_arlen;
    input wire  [SIZE_WIDTH-1:0]
                        axi_arsize;
    input wire  [1:0]   axi_arburst;
    input wire  [ID_WIDTH-1:0]
                        axi_arid;
    

    output reg          axi_rvalid;
    input wire          axi_rready;
    output reg  [1:0]   axi_rresp;
    output reg          axi_rlast;
    output reg  [DATA_WIDTH-1:0]
                        axi_rdata;
    output reg [ID_WIDTH-1:0]
                        axi_rid;
    

// NOTE: This file is not requirement
`ifdef BRAM_DEBUG
`include "assert.vh"
`endif


reg [DEPTH_CLOG2-1+2:0] address;
reg read, write;

armleocpu_mem_1rwm #($clog2(DEPTH), DATA_WIDTH) backstorage  (
    .clk(clk),
    
    .address(address[DEPTH_CLOG2-1+2:2]),

    .read(read),
    .readdata(axi_rdata),

    .write(write),
    .writeenable(axi_wstrb),
    .writedata(axi_wdata)
);

localparam STATE_IDLE = 4'd0; // Accepts read/write address
localparam STATE_WRITE = 4'd1; // Accepts write
localparam STATE_WRITE_RESPONSE = 4'd2; // Sends write response
localparam STATE_READ = 4'd3; // Read cycle

`DEFINE_REG_REG_NXT(4, state, state_nxt, clk)

`DEFINE_REG_REG_NXT(ADDR_WIDTH, addr, addr_nxt, clk)

`DEFINE_REG_REG_NXT(ID_WIDTH, id, id_nxt, clk)
`DEFINE_REG_REG_NXT(2, resp, resp_nxt, clk)

`DEFINE_REG_REG_NXT(9, burst_remaining, burst_remaining_nxt, clk)
`DEFINE_REG_REG_NXT(8, len, len_nxt, clk)
`DEFINE_REG_REG_NXT(2, burst_type, burst_type_nxt, clk)
// Burst remaining contains amount of cycles of rvalid && rready

// Signals
reg [ADDR_WIDTH-1:0] wrap_mask;
reg [ADDR_WIDTH-1:0] increment;
reg axi_bvalid_nxt, axi_rvalid_nxt;


always @(posedge clk) begin
    axi_bvalid <= axi_bvalid_nxt;
    axi_rvalid <= axi_rvalid_nxt;
end

always @* begin
    `ifdef SIMULATION
    #1
    // This is required because of infinite loop
    // When simulations is running
    // This is caused by changing of values between multiple combinational alwayses
    `endif
    axi_awready = 0;
    axi_wready = 0;

    axi_arready = 0;

    axi_rlast = 0;
    
    state_nxt = state;

    addr_nxt = addr;
    id_nxt = id;
    resp_nxt = resp;
    len_nxt = len;


    // TODO: ORdering is wrong
    
    address = addr_nxt;
    write = 0;
    read = 0;


    burst_remaining_nxt = burst_remaining;
    burst_type_nxt = burst_type;

    axi_rid = id;
    axi_rresp = resp;
    

    axi_bvalid_nxt = axi_bvalid;
    axi_rvalid_nxt = axi_rvalid;

    axi_bresp = resp;
    axi_bid = id;
    

    //increment = (1 << (size + 1));
    increment = 4;
    // Size = 0, 1 byte, 1 increment
    // Size = 1, 2 byte, 2 increment
    // Size = 2, 4 byte, 4 increment
    // etc

    // Wrap mask. Bit mask showing 
    wrap_mask = len << 2 | 2'b11;
    // Examples:
    // len = 8'b0, 1 cycle,   wrap mask = 11
    // len = 8'b1, 2 cycles,  wrap mask = 111
    // len = 8'b11, 4 cycles,  wrap mask = 1111
    // len = 8'b111, 8 cycles,  wrap mask = 11111

    
    if(!rst_n) begin
        axi_bvalid_nxt = 0;
        axi_rvalid_nxt = 0;
        state_nxt = STATE_IDLE;

    end else begin
        case(state)
            STATE_IDLE: begin
                if(axi_awvalid) begin
                    axi_awready = 1;
                    state_nxt = STATE_WRITE;
                    addr_nxt = axi_awaddr;
                    id_nxt = axi_awid;
                    len_nxt = axi_awlen;
                    resp_nxt = addr_nxt < (DEPTH << 2) ? `AXI_RESP_OKAY : `AXI_RESP_DECERR;
                    // Note: Sync logic contains debug statements and assertions
                end else if(axi_arvalid) begin
                    axi_arready = 1;
                    state_nxt = STATE_READ;
                    addr_nxt = axi_araddr;
                    id_nxt = axi_arid;
                    burst_remaining_nxt = axi_arlen;
                    burst_type_nxt = axi_arburst;
                    len_nxt = axi_arlen;
                    resp_nxt = addr_nxt < (DEPTH << 2) ? `AXI_RESP_OKAY : `AXI_RESP_DECERR;
                    
                    axi_rvalid_nxt = 1;
                    // Note: Sync logic contains debug statements and assertions
                    
                    address = addr_nxt;
                    read = 1;
                    
                end
            end
            STATE_READ: begin
                // Example for 3 cycles burst read
                // First cycle: Contains data for addr and burst remaining = 2
                // Second cycle data for addr + 4 and burst remaining = 1
                // Last cycle data for addr + 8 and axi_rlast = 1 (burst remaining = 0)

                // Example for 1 cycle burst read
                // First cycle: Contains data for addr and burst remaining = 0, axi_rlast = 1
                axi_rlast = burst_remaining == 0;
                if(axi_rready) begin
                    if(burst_type == `AXI_BURST_INCR) begin
                        addr_nxt = (addr + increment);
                    end else if(burst_type == `AXI_BURST_WRAP) begin
                        addr_nxt = (addr & ~wrap_mask)
                                    | ((addr + increment) & wrap_mask);
                    end else begin
                        `ifdef BRAM_DEBUG
                            $display("Unsupported burst type");
                        `endif
                    end
                    if(!axi_rlast) begin
                        burst_remaining_nxt = burst_remaining - 1;
                    end
                    if(axi_rlast) begin
                        axi_rvalid_nxt = 0;
                        state_nxt = STATE_IDLE;
                    end
                    resp_nxt = addr_nxt < (DEPTH << 2) ? `AXI_RESP_OKAY : `AXI_RESP_DECERR;
                    address = addr_nxt;
                    read = 1;
                end
            end
            STATE_WRITE: begin
                axi_wready = 1;
            
                address = addr_nxt;
                if(axi_wvalid) begin
                    // Note: Sync logic contains debug statements and assertions
                    
                    axi_bvalid_nxt = 1;
                    resp_nxt = addr_nxt < (DEPTH << 2) ? `AXI_RESP_OKAY : `AXI_RESP_DECERR;
                    
                    if(resp == `AXI_RESP_OKAY) begin
                        write = 1;
                    end
                    state_nxt = STATE_WRITE_RESPONSE;
                end
            end
            STATE_WRITE_RESPONSE: begin
                if(axi_bready) begin
                    state_nxt = STATE_IDLE;
                    axi_bvalid_nxt = 0;
                end
            end
        endcase
    end
end

`ifdef BRAM_DEBUG

always @(posedge clk) begin
    if(rst_n) begin
        case(state)
            STATE_IDLE: begin
                if(axi_awvalid) begin
                    `assert_equal(axi_awaddr[1:0], 2'b00)
                    `assert_equal(axi_awlen, 0)
                    `assert_equal(axi_awsize, $clog2(DATA_STROBES))
                    `assert_equal(axi_awburst, `AXI_BURST_INCR)
                    $display("Starting write addr = 0x%x", addr_nxt);
                end else if(axi_arvalid) begin
                    `assert_equal(axi_araddr[1:0], 2'b00)
                    `assert_equal(axi_arsize, $clog2(DATA_STROBES))
                    `assert((axi_arburst == `AXI_BURST_INCR) || (axi_arburst == `AXI_BURST_WRAP))
                    $display("Starting read addr = 0x%x, len = %d, burst_type = %d, id = %d", addr_nxt, len_nxt, burst_type_nxt, id_nxt);
                
                end
            end
            STATE_READ: begin
                if(axi_rready) begin
                    if(burst_type == `AXI_BURST_INCR) begin
                        
                    end else if(burst_type == `AXI_BURST_WRAP) begin
                        
                    end else begin
                        $display("Unsupported burst type");
                        `assert(0)
                    end
                    if(axi_rlast) begin
                        $display("Read done");
                    end else begin
                        $display("Reading data addr = 0x%x, resp = 0x%x, burst_remaining = 0x%x, burst_type = 0x%x",
                                                addr_nxt, resp_nxt, burst_remaining, burst_type);
                    end
                end
            end
            STATE_WRITE: begin
                if(axi_wvalid) begin
                    `assert_equal(axi_wlast, 1);
                    if(resp == `AXI_RESP_OKAY) begin
                        $display("Written addr = 0x%x, data = 0x%x, wstrb=0b%b", address, axi_wdata, axi_wstrb);
                    end else begin
                        $display("NOT Written addr = 0x%x", addr_nxt);
                    end
                end
            end
        endcase
    end
end

`endif
endmodule

// verilator lint_on UNUSED
// verilator lint_on WIDTH
// verilator lint_on CASEINCOMPLETE


`include "armleocpu_undef.vh"