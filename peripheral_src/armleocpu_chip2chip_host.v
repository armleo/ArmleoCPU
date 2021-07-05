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

module armleocpu_chip2chip_host (
    clk, rst_n,

    io_downstream_axi_awvalid, io_downstream_axi_awready, io_downstream_axi_awaddr, io_downstream_axi_awlen, io_downstream_axi_awburst, io_downstream_axi_awsize,
    io_downstream_axi_wvalid, io_downstream_axi_wready, io_downstream_axi_wdata, io_downstream_axi_wstrb, io_downstream_axi_wlast,
    io_downstream_axi_bvalid, io_downstream_axi_bready, io_downstream_axi_bresp,

    io_downstream_axi_arvalid, io_downstream_axi_arready, io_downstream_axi_araddr, io_downstream_axi_arlen, io_downstream_axi_arsize, io_downstream_axi_arburst,
    io_downstream_axi_rvalid, io_downstream_axi_rready, io_downstream_axi_rresp, io_downstream_axi_rlast, io_downstream_axi_rdata,
    
    io_upstream_axi_awvalid, io_upstream_axi_awready, io_upstream_axi_awaddr, io_upstream_axi_awlen, io_upstream_axi_awburst, io_upstream_axi_awsize,
    io_upstream_axi_wvalid, io_upstream_axi_wready, io_upstream_axi_wdata, io_upstream_axi_wstrb, io_upstream_axi_wlast,
    io_upstream_axi_bvalid, io_upstream_axi_bready, io_upstream_axi_bresp,

    io_upstream_axi_arvalid, io_upstream_axi_arready, io_upstream_axi_araddr, io_upstream_axi_arlen, io_upstream_axi_arsize, io_upstream_axi_arburst,
    io_upstream_axi_rvalid, io_upstream_axi_rready, io_upstream_axi_rresp, io_upstream_axi_rlast, io_upstream_axi_rdata,

    io_upstream_irq, io_downstream_irq,

    io_data_out, io_oe, io_data_in, bus_stuck
    
);

    parameter [0:0] OPT_HOST = 1;

    parameter BUFFERS = 4;
    parameter UPSTREAM_ID_WIDTH = 4;
    parameter DOWNSTREAM_ID_WIDTH = 8;

    localparam ADDR_WIDTH = 32;
    localparam DATA_WIDTH = 32;
    localparam DATA_STROBES = DATA_WIDTH / 8;
    localparam SIZE_WIDTH = 3;

    input                   clk;
    input                   rst_n;

    // All inputs/outputs are registered
    // client port, connects to CPU or other host

    output reg          io_upstream_irq;

    input wire          io_upstream_axi_awvalid;
    output wire         io_upstream_axi_awready;
    input wire  [ADDR_WIDTH-1:0]
                        io_upstream_axi_awaddr;
    input wire  [7:0]   io_upstream_axi_awlen;
    input wire  [SIZE_WIDTH-1:0]
                        io_upstream_axi_awsize;
    input wire  [1:0]   io_upstream_axi_awburst;
    input wire          io_upstream_axi_awlock;
    input wire  [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_awid;
    input wire  [2:0]   io_upstream_axi_awprot;

    // AXI W Bus
    input wire          io_upstream_axi_wvalid;
    output wire         io_upstream_axi_wready;
    input wire  [DATA_WIDTH-1:0]
                        io_upstream_axi_wdata;
    input wire  [DATA_STROBES-1:0]
                        io_upstream_axi_wstrb;
    input wire          io_upstream_axi_wlast;
    
    // AXI B Bus
    output wire         io_upstream_axi_bvalid;
    input wire          io_upstream_axi_bready;
    output wire [1:0]   io_upstream_axi_bresp;
    output wire [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_bid;
    
    
    input wire          io_upstream_axi_arvalid;
    output reg          io_upstream_axi_arready;
    input wire  [ADDR_WIDTH-1:0]
                        io_upstream_axi_araddr;
    input wire  [7:0]   io_upstream_axi_arlen;
    input wire  [SIZE_WIDTH-1:0]
                        io_upstream_axi_arsize;
    input wire  [1:0]   io_upstream_axi_arburst;
    input wire  [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_arid;
    input wire          io_upstream_axi_arlock;
    input wire  [2:0]   io_upstream_axi_arprot;
    

    output reg          io_upstream_axi_rvalid;
    input  wire         io_upstream_axi_rready;
    output reg  [1:0]   io_upstream_axi_rresp;
    output reg          io_upstream_axi_rlast;
    output reg  [DATA_WIDTH-1:0]
                        io_upstream_axi_rdata;
    output reg  [UPSTREAM_ID_WIDTH-1:0]
                        io_upstream_axi_rid;

    
    // Host port, connects to peripheral
    // All signals are registered


    // AXI AW Bus
    output wire  [1-1:0]            io_downstream_axi_awvalid;
    input  wire [1-1:0]             io_downstream_axi_awready;
    output wire [ADDR_WIDTH-1:0]    io_downstream_axi_awaddr;
    output wire [8-1:0]             io_downstream_axi_awlen;
    output wire [SIZE_WIDTH-1:0]    io_downstream_axi_awsize;
    output wire [2-1:0]             io_downstream_axi_awburst;
    output wire [0:0]               io_downstream_axi_awlock;
    output wire [3-1:0]             io_downstream_axi_awprot;

    // AXI W Bus
    output reg [1-1:0]              io_downstream_axi_wvalid;
    input  wire [1-1:0]             io_downstream_axi_wready;
    output wire [DATA_WIDTH-1:0]    io_downstream_axi_wdata;
    output wire [DATA_STROBES-1:0]  io_downstream_axi_wstrb;
    output wire [0:0]               io_downstream_axi_wlast;
    
    // AXI B Bus
    input  wire [1-1:0]             io_downstream_axi_bvalid;
    output reg [1-1:0]              io_downstream_axi_bready;
    input  wire [2-1:0]             io_downstream_axi_bresp;


    output reg  [1-1:0]             io_downstream_axi_arvalid;
    input  wire [1-1:0]             io_downstream_axi_arready;
    output reg  [ADDR_WIDTH-1:0]    io_downstream_axi_araddr;
    output wire [8-1:0]             io_downstream_axi_arlen;
    output wire [SIZE_WIDTH-1:0]    io_downstream_axi_arsize;
    output wire [2-1:0]             io_downstream_axi_arburst;
    output wire [0:0]               io_downstream_axi_arlock;
    output wire [3-1:0]             io_downstream_axi_arprot;
    

    input wire [1-1:0]              io_downstream_axi_rvalid;
    output reg [1-1:0]              io_downstream_axi_rready;
    input wire [2-1:0]              io_downstream_axi_rresp;
    input wire [1-1:0]              io_downstream_axi_rlast;
    input wire [DATA_WIDTH-1:0]     io_downstream_axi_rdata;
    
    input wire io_downstream_irq;


    // CHIP2CHIP interface
    // The input/output clk is "clk".
    output reg [7:0]        io_data_out;
    output reg              io_oe;
    input wire [7:0]        io_data_in;

    output reg              bus_stuck;

// Chip 2 chip interface
// One bidirection I/O 8 bit

localparam STATE_HOST_START_SEND = 0;
localparam STATE_HOST_START_WAIT = 1;
localparam STATE_POLL_REQ = 2;
localparam STATE_POLL_RESP0 = 3;
localparam STATE_POLL_RESP1 = 4;
localparam STATE_IDLE = 5;

reg [7:0] state;

reg [7:0] io_data_in_registered;

reg [15:0] timeout;

// TODO: Declarations for downstream_axi_

axi_register_slice_full downstream_registerslice(
    .clk(clk),
    .rst_n(rst_n),

    `CONNECT_AXI_BUS(io_downstream_axi_, downstream_axi_)
    // TODO: Macro to connect AXI w/o ID
)



task HOST_START_SEND;
begin
    state <= STATE_HOST_START_SEND;
    io_oe <= 1;
    io_data_out <= `CHIP2CHIP_POLL_REQ;
end
endtask

task POLL;
begin
    state <= STATE_POLL_REQ;
    io_oe <= 1;
    io_data_out <= `CHIP2CHIP_POLL_REQ;
end
endtask

task POLL_RESP;
begin
    state <= STATE_POLL_RESP0;
    io_oe <= 0;
end
endtask


task AW32;
begin

end
endtask

task AR32;
begin

end
endtask

task DOWNSTREAM_TRANSACTION;
begin

end
endtask



always @(posedge clk) begin
    io_data_in_registered <= io_data_in;

    if(!rst_n) begin
        HOST_START_SEND();
    end else begin
        if(timeout > 0) begin
            timeout <= timeout - 1;
        end

        case(state)
            // --------- RESET SEQUENCE START --------- 

            STATE_HOST_START_SEND: begin
                timeout <= 16;
                assert(io_oe == 1);
                io_oe <= 0;
                state <= STATE_HOST_START_WAIT;
            end
            STATE_HOST_START_WAIT: begin
                assert(io_oe == 0);
                if(io_data_in == `CHIP2CHIP_POLL_RESP) begin
                    POLL();
                end else if(timeout == 0) begin
                    HOST_START_SEND();
                end
                assume((io_data_in == 8'hFF) || (io_data_in == `CHIP2CHIP_POLL_RESP));
            end

            // --------- RESET SEQUENCE DONE --------- 

            // --------- POLLING SEQUENCE START --------- 
            STATE_POLL_REQ: begin
                assert(io_oe == 1);
                assert(io_data_out == `CHIP2CHIP_POLL_REQ);
                
                POLL_RESP();
            end
            STATE_POLL_RESP0: begin
                assert(io_oe == 0);
                assume(io_data_in == `CHIP2CHIP_POLL_RESP);
                if(io_data_in_registered == `CHIP2CHIP_POLL_RESP) begin
                    state <= STATE_POLL_RESP1;
                end
            end
            STATE_POLL_RESP1: begin
                assert(io_oe == 0);
                //poll_interrupt_pending == io_data_in_registered[1];
                //poll_request_pending == io_data_in_registered[2];
                assume(!(io_data_in_registered[2] & io_data_in_registered[0]));
                // Make sure that only one is asserted

                if(upstream_axi_awvalid) begin
                    AW32();
                end else if(upstream_axi_arvalid) begin
                    AR32();
                end else if(io_data_in_registered[2]) begin
                    DOWNSTREAM_TRANSACTION();
                end else begin
                    POLL();
                end
            end
            // --------- POLLING SEQUENCE DONE--------- 
            
        endcase
    end
end


`ifdef FORMAL_RULES
reg past_valid;
initial	past_valid = 1'b0;
always @(posedge clk)
    past_valid <= 1'b1;

`define signal_valid_check(assume_assert, channel, prefix, width, signal) \
reg ``prefix````channel``valid_``channel``ready_for_``signal``_old; \
reg [``width``-1:0] ``prefix````channel````signal``_old; \
always @(posedge clk) begin \
    if ((past_valid) && (``prefix````channel``valid_``channel``ready_for_``signal``_old)) begin \
        ``assume_assert``(``prefix````channel``valid); \
        ``assume_assert``(``prefix````channel````signal``_old == ``prefix````channel````signal``); \
    end \
    ``prefix````channel``valid_``channel``ready_for_``signal``_old <= ``prefix````channel``valid && !``prefix````channel``ready; \
    ``prefix````channel````signal``_old <= ``prefix````channel````signal``; \
end

`signal_valid_check(assume, io_upstream_axi_, ar, ADDR_WIDTH, addr)
`signal_valid_check(assume, io_upstream_axi_, ar, ID_WIDTH, id)
/*
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
*/

`endif

endmodule


`include "armleocpu_undef.vh"