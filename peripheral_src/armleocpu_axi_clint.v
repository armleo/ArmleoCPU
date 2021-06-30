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

// TODO: Add other part of header/footer

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_axi_clint #(
    parameter ADDR_WIDTH = 34,
    parameter ID_WIDTH = 4,
    parameter DATA_WIDTH = 32, // 32 or 64
    localparam DATA_STROBES = DATA_WIDTH / 8, // fixed
    localparam SIZE_WIDTH = 3, // fixed

    parameter HART_COUNT = 7, // Valid range: 1 .. 16
    localparam HART_COUNT_WIDTH = $clog2(HART_COUNT)
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

    output reg          hart_swi,
    output reg          hart_timeri,

    input  wire         mtime_increment
    
);

reg [63:0] mtime;

reg [63:0] mtimecmp [HART_COUNT-1:0];



wire [31:0] address;
wire write, read;
wire [31:0] write_data;
wire [3:0] write_byteenable;
reg [31:0] read_data; // combinational
reg address_error;
reg write_error;

armleocpu_axi2simple_converter converter(
    .clk(clk),
    .rst_n(rst_n),

    .address(address),
    .write(write),
    .read(read),
    .write_data(write_data),
    .write_byteenable(write_byteenable),
    .read_data(read_data),
    .address_error(address_error),
    .write_error(write_error),


    // TODO: Connect axi ports
);



 // COMB ->
reg msip_sel,
mtimecmp_sel,
mtime_sel;

wire address_match_any = msip_sel || mtimecmp_sel || mtime_sel;
wire high_sel = address[2]; // Only valid for mtimecmp/mtime

reg [HART_COUNT_WIDTH-1:0] address_hart_id;
reg hart_id_valid;

always @* begin : address_match_logic_always_comb
    address_hart_id = address[2+HART_COUNT_WIDTH-1:2];
    msip_sel = 0;
    mtimecmp_sel = 0;
    mtime_sel = 0;
    hart_id_valid = 0;
    write_error = 0;
    if(address[31:12] == 0 && address[11:2+HART_COUNT_WIDTH] == 0) begin
        msip_sel = 1;
        address_hart_id = address[2+HART_COUNT_WIDTH-1:2];
        hart_id_valid = {1'b0, address_hart_id} < HART_COUNT;
    end else if((address[31:12] == 4) && address[11:3+HART_COUNT_WIDTH] == 0) begin
        mtimecmp_sel = 1;
        address_hart_id = address[3+HART_COUNT_WIDTH-1:3];
        hart_id_valid = {1'b0, address_hart_id} < HART_COUNT;
    end else if(address == 32'hBFF8 || address == 32'hBFF8 + 4) begin
        mtime_sel = 1;
        hart_id_valid = 1;
        write_error = 1;
    end
    address_error = !hart_id_valid || !address_match_any;
end


always @(posedge clk) begin : main_always_ff
    reg [HART_COUNT_WIDTH:0] i; // Intentionally one bit wider
    // This is done to allow it to take HART_COUNT value, for loop to stop
    // Intentionally not integer because some synthesis tools dont support it
    if(!rst_n) begin
        mtime <= 0;
        for(i = 0; i < HART_COUNT; i = i + 1) begin
            hart_timeri[i[HART_COUNT_WIDTH-1:0]] <= 1'b0;
            hart_swi[i[HART_COUNT_WIDTH-1:0]] <= 1'b0;
            mtimecmp[i[HART_COUNT_WIDTH-1:0]]  <= -64'd1;
        end
    end else begin
        mtime <= mtime + mtime_increment;

        for(i = 0; i < HART_COUNT; i = i + 1) begin
            hart_timeri[i[HART_COUNT_WIDTH-1:0]] <= (mtime >= mtimecmp[i[HART_COUNT_WIDTH-1:0]]);
        end
        if(write) begin
            if(msip_sel) begin
                if(write_byteenable[0])
                    hart_swi[address_hart_id] <= write_data[0];
            end else if(mtimecmp_sel) begin
                if(!high_sel) begin
                    if(write_byteenable[0])
                        mtimecmp[address_hart_id][7:0] <= write_data[7:0];
                    if(write_byteenable[1])
                        mtimecmp[address_hart_id][15:8] <= write_data[15:8];
                    if(write_byteenable[2])
                        mtimecmp[address_hart_id][23:16] <= write_data[23:16];
                    if(write_byteenable[3])
                        mtimecmp[address_hart_id][31:24] <= write_data[31:24];
                end else begin
                    if(write_byteenable[0])
                        mtimecmp[address_hart_id][39:32] <= write_data[7:0];
                    if(write_byteenable[1])
                        mtimecmp[address_hart_id][47:40] <= write_data[15:8];
                    if(write_byteenable[2])
                        mtimecmp[address_hart_id][55:48] <= write_data[23:16];
                    if(write_byteenable[3])
                        mtimecmp[address_hart_id][63:56] <= write_data[31:24];
                end
            end
        end

    end
end


always @* begin : read_data_always_comb
    read_data = 0;
    if(msip_sel)
        read_data[0] = hart_swi[address_hart_id];
    else if(mtimecmp_sel) begin
        if(high_sel)
            read_data = mtimecmp[address_hart_id][63:32];
        else
            read_data = mtimecmp[address_hart_id][31:0];
    end else if(mtime_sel)
        if(high_sel)
            read_data = mtime[63:32];
        else
            read_data = mtime[31:0];
end

endmodule

`include "armleocpu_undef.vh"
