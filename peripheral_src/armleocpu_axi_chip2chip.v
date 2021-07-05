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

module armleocpu_axi_over_spi_host(
    clk, rst_n,

    io_upstream_axi_awvalid, io_upstream_axi_awready, io_upstream_axi_awaddr, io_upstream_axi_awlen, io_upstream_axi_awburst, io_upstream_axi_awsize,
    io_upstream_axi_wvalid, io_upstream_axi_wready, io_upstream_axi_wdata, io_upstream_axi_wstrb, io_upstream_axi_wlast,
    io_upstream_axi_bvalid, io_upstream_axi_bready, io_upstream_axi_bresp,

    io_upstream_axi_arvalid, io_upstream_axi_arready, io_upstream_axi_araddr, io_upstream_axi_arlen, io_upstream_axi_arsize, io_upstream_axi_arburst,
    io_upstream_axi_rvalid, io_upstream_axi_rready, io_upstream_axi_rresp, io_upstream_axi_rlast, io_upstream_axi_rdata,

    io_upstream_irq,

    // TODO: Add signals
);

parameter ID_WIDTH = 4;
localparam ADDR_WIDTH = 32;
localparam DATA_WIDTH = 32;
localparam DATA_STROBES = DATA_WIDTH/8;

input                   clk;
input                   rst_n;

output reg          io_upstream_irq;

input wire                      io_upstream_axi_awvalid;
output wire                     io_upstream_axi_awready;
input wire  [ADDR_WIDTH-1:0]    io_upstream_axi_awaddr;
input wire  [7:0]               io_upstream_axi_awlen;
input wire  [SIZE_WIDTH-1:0]    io_upstream_axi_awsize;
input wire  [1:0]               io_upstream_axi_awburst;
input wire                      io_upstream_axi_awlock;
input wire  [ID_WIDTH-1:0]      io_upstream_axi_awid;
input wire  [2:0]               io_upstream_axi_awprot;

// AXI W Bus
input wire                      io_upstream_axi_wvalid;
output wire                     io_upstream_axi_wready;
input wire  [DATA_WIDTH-1:0]    io_upstream_axi_wdata;
input wire  [DATA_STROBES-1:0]  io_upstream_axi_wstrb;
input wire                      io_upstream_axi_wlast;

// AXI B Bus
output wire                     io_upstream_axi_bvalid;
input wire                      io_upstream_axi_bready;
output wire [1:0]               io_upstream_axi_bresp;
output wire [ID_WIDTH-1:0]      io_upstream_axi_bid;

input wire                      io_upstream_axi_arvalid;
output wire                     io_upstream_axi_arready;
input wire  [ADDR_WIDTH-1:0]    io_upstream_axi_araddr;
input wire  [7:0]               io_upstream_axi_arlen;
input wire  [SIZE_WIDTH-1:0]    io_upstream_axi_arsize;
input wire  [1:0]               io_upstream_axi_arburst;
input wire  [ID_WIDTH-1:0]      io_upstream_axi_arid;
input wire                      io_upstream_axi_arlock;
input wire  [2:0]               io_upstream_axi_arprot;

output wire                     io_upstream_axi_rvalid;
input  wire                     io_upstream_axi_rready;
output wire [1:0]               io_upstream_axi_rresp;
output wire                     io_upstream_axi_rlast;
output wire [DATA_WIDTH-1:0]    io_upstream_axi_rdata;
output wire [ID_WIDTH-1:0]      io_upstream_axi_rid;

output reg                      io_csn;
input wire  [7:0]               io_datain;
output reg  [7:0]               io_dataout;
output reg                      io_oe;



armleocpu_axi_register_slice #(
    .ADDR_WIDTH(ADDR_WIDTH), // Fixed
    .ID_WIDTH(ID_WIDTH), // Configurable
    .DATA_WIDTH(DATA_WIDTH) // Fixed
) slice(
    .clk                (clk),
    .rst_n              (rst_n),

    `CONNECT_AXI_BUS(upstream_axi_, io_upstream_axi_)
    .upstream_axi_arlock           (io_upstream_arlock),
    .upstream_axi_awlock           (io_upstream_awlock),

    `CONNECT_AXI_BUS(downstream_axi_, upstream_axi_), 
    .downstream_axi_arlock         (upstream_axi_arlock),
    .downstream_axi_awlock         (upstream_axi_awlock),
);

reg address

armleocpu_mem_1rw #(
    .ELEMENTS_W(5), // at least (8 + 16) x 32 -> rounded up 32 x 32
    .WIDTH(DATA_WIDTH),
) buffer (
    .clk        (clk),

    .address    (address),

    .read       (read),
    .readdata   (readdata),

    .write      (write),
    .writedata  (writedata)
);



always @* begin

    state_nxt = state;
    address = 0;
    write = 0;
    read = 0;
    writedata = 0;

    upstream_axi_awready = 0;
    upstream_axi_wready = 0;
    upstream_axi_bvalid = 0;
    upstream_axi_arready = 0;
    upstream_axi_rvalid = 0;

    currently_writing_nxt = currently_writing;

    case(state)
        STATE_IDLE: begin
            address = 0;
            if(upstream_axi_awvalid) begin
                writedata = OPCODE_WRITE;
                write = 1;
                currently_writing_nxt = 1;
                `ifdef DEBUG_CHIP2CHIP
                assume(upstream_axi_awlen < 16); // Make sure no bigger than 64 bytes per burst
                `endif
            end else if(upstream_axi_arvalid) begin
                writedata = OPCODE_READ;
                write = 1;
                currently_writing_nxt = 0;
                `ifdef DEBUG_CHIP2CHIP
                assume(upstream_axi_arlen < 16); // Make sure no bigger than 64 bytes per burst
                `endif
            end
        end
        
        STATE_WRITE0: begin
            write = 1;
            address = 1;
            if(currently_writing) begin // AW
                writedata = {
                    upstream_axi_awaddr, // 32 bits
                };
            end else begin // AR
                writedata = {
                    upstream_axi_araddr
                };
            end

            state_nxt = STATE_WRITE1;
        end
        STATE_WRITE1: begin
            write = 1;
            
            address = 2;
            if(currently_writing) begin
                writedata = {
                    upstream_axi_awid,   // ID_WIDTH
                    upstream_axi_awlen,  // 8
                    upstream_axi_awsize, // 3
                    upstream_axi_awburst,// 2
                    upstream_axi_awlock, // 1
                    upstream_axi_awprot  // 3
                };
            end else begin
                writedata = {
                    upstream_axi_awid,   // ID_WIDTH
                    upstream_axi_awlen,  // 8
                    upstream_axi_awsize, // 3
                    upstream_axi_awburst,// 2
                    upstream_axi_awlock, // 1
                    upstream_axi_awprot  // 3
                };
            end


            state_nxt = STATE_WRITE_DATA;

            len_nxt = len;
        end
        STATE_WRITE_DATA: begin
            
            if()
            counter_nxt = 2 + len;
            state_nxt = STATE_TX;
            initial_cycle_nxt = 1;
        end
        STATE_TX: begin
            io_csn = 0;
            
            read = 1;
            initial_cycle_nxt = 0;
            if(initial_cycle) begin
                
            end else if(counter != 0) begin

            else begin

            end
            
            state_nxt = STATE_RX;
        end
        STATE_RX: begin

            state_nxt = STATE_IDLE;
        end
    endcase
end




endmodule


`include "armleocpu_undef.vh"
