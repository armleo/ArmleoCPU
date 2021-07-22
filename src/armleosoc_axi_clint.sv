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

module armleosoc_axi_clint #(
    localparam ADDR_WIDTH = 16, // fixed 16 bits
    parameter ID_WIDTH = 4,
    localparam DATA_WIDTH = 32, // 32 or 64
    localparam DATA_STROBES = DATA_WIDTH / 8, // fixed
    localparam SIZE_WIDTH = 3, // fixed

    parameter HART_COUNT = 4, // Valid range: 1 .. 16 (Hard limit is 250)
    localparam HART_COUNT_WIDTH = $clog2(HART_COUNT)
) (
    input               clk,
    input               rst_n,

    input wire          axi_awvalid,
    output wire         axi_awready,
    input wire  [ADDR_WIDTH-1:0]
                        axi_awaddr,
    input wire [ID_WIDTH-1:0]
                        axi_awid,
    input wire  [7:0]   axi_awlen,
    input wire  [SIZE_WIDTH-1:0]
                        axi_awsize,
    input wire  [1:0]   axi_awburst,
    

    // AXI W Bus
    input wire          axi_wvalid,
    output wire          axi_wready,
    input wire  [DATA_WIDTH-1:0]
                        axi_wdata,
    input wire  [DATA_STROBES-1:0]
                        axi_wstrb,
    // verilator lint_off UNUSED
    input wire [0:0]    axi_wlast,
    // verilator lint_off UNUSED
                        

    // AXI B Bus
    output wire         axi_bvalid,
    input wire          axi_bready,
    output wire [1:0]   axi_bresp,
    output wire [ID_WIDTH-1:0]
                        axi_bid,
    
    
    input wire          axi_arvalid,
    output wire         axi_arready,
    input wire  [ADDR_WIDTH-1:0]
                        axi_araddr,
    input wire [ID_WIDTH-1:0]
                        axi_arid,
    input wire  [7:0]   axi_arlen,
    input wire  [SIZE_WIDTH-1:0]
                        axi_arsize,
    input wire  [1:0]   axi_arburst,
    
    

    output wire         axi_rvalid,
    input wire          axi_rready,
    output wire  [1:0]  axi_rresp,
    output wire  [ID_WIDTH-1:0]
                        axi_rid,
    output wire [DATA_WIDTH-1:0]
                        axi_rdata,
    output wire         axi_rlast,

    output reg [HART_COUNT-1:0]
                        hart_m_swi,
    output reg [HART_COUNT-1:0]
                        hart_s_swi,
    
    output reg [HART_COUNT-1:0]
                        hart_timeri,

    input  wire         mtime_increment
    // Input is synchronous to clk
    // This input signal will go high for no longer than one cycle,
    //     per increment
    
);

reg [63:0] mtime;

reg [63:0] mtimecmp [HART_COUNT-1:0];



wire [ADDR_WIDTH-1:0] address;
wire write;
// verilator lint_off UNUSED
wire read;
// verilator lint_on UNUSED

wire [31:0] write_data;
wire [3:0] write_byteenable;
reg [31:0] read_data; // combinational
reg address_error;
wire write_error = 0; // Not possible

armleosoc_axi2simple_converter #(
    .ADDR_WIDTH(ADDR_WIDTH),
    .ID_WIDTH(ID_WIDTH)
) converter (
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

    `CONNECT_AXI_BUS(axi_, axi_),
    .axi_arprot(0),
    .axi_awprot(0),
    .axi_awlock(0),
    .axi_arlock(0)
);



 // COMB ->
reg msip_sel,
ssip_sel,
mtimecmp_sel,
mtime_sel;

wire address_match_any = msip_sel || ssip_sel || mtimecmp_sel || mtime_sel;
wire high_sel = address[2]; // Only valid for mtimecmp/mtime

reg [10:0] address_hart_id;
wire hart_id_valid = address_hart_id < HART_COUNT;

wire [HART_COUNT_WIDTH-1:0] short_hart_id = address_hart_id[HART_COUNT_WIDTH-1:0];

always @* begin : address_match_logic_always_comb
    address_hart_id = address[12:2];
    msip_sel = 0;
    mtimecmp_sel = 0;
    mtime_sel = 0;
    ssip_sel = 0;

    if(address <= 16'h3FFF) begin // 0000.3FFF MMIP
        address_hart_id = address[12:2];
        msip_sel = 1;
    end else if(address >= 16'hC000) begin // C000..FFFF SSIP
        address_hart_id = address[12:2];
        ssip_sel = 1;
    end else if((address >= 16'h4000) && (address < 16'hBFF0)) begin // 4000..BFF0 MTIMECMP
        address_hart_id = {1'b0, address[12:3]};
        mtimecmp_sel = 1;
    end else if((address == 16'hBFF8) || (address == 16'hBFF8 + 4)) begin
        address_hart_id = 0;
        // Set it to something that always exists, because hart_id_valid
        // is calculated depending on this value
        mtime_sel = 1;
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
            hart_timeri[i[HART_COUNT_WIDTH-1:0]]    <= 1'b0;
            hart_m_swi[i[HART_COUNT_WIDTH-1:0]]     <= 1'b0;
            hart_s_swi[i[HART_COUNT_WIDTH-1:0]]     <= 1'b0;
            mtimecmp[i[HART_COUNT_WIDTH-1:0]]       <= -64'd1;
        end
    end else begin
        mtime <= mtime + {63'd0, mtime_increment};

        for(i = 0; i < HART_COUNT; i = i + 1) begin
            hart_timeri[i[HART_COUNT_WIDTH-1:0]] <= (mtime >= mtimecmp[i[HART_COUNT_WIDTH-1:0]]);
        end

        if(write && !address_error) begin
            if(msip_sel) begin
                if(write_byteenable[0])
                    hart_m_swi[short_hart_id] <= write_data[0];
            end else if(ssip_sel) begin
                if(write_byteenable[0])
                    hart_s_swi[short_hart_id] <= write_data[0];
            end else if(mtime_sel) begin
                if(!high_sel) begin
                    if(write_byteenable[0])
                        mtime[7:0] <= write_data[7:0];
                    if(write_byteenable[1])
                        mtime[15:8] <= write_data[15:8];
                    if(write_byteenable[2])
                        mtime[23:16] <= write_data[23:16];
                    if(write_byteenable[3])
                        mtime[31:24] <= write_data[31:24];
                end else begin
                    if(write_byteenable[0])
                        mtime[39:32] <= write_data[7:0];
                    if(write_byteenable[1])
                        mtime[47:40] <= write_data[15:8];
                    if(write_byteenable[2])
                        mtime[55:48] <= write_data[23:16];
                    if(write_byteenable[3])
                        mtime[63:56] <= write_data[31:24];
                end
            end else if(mtimecmp_sel) begin
                if(!high_sel) begin
                    if(write_byteenable[0])
                        mtimecmp[short_hart_id][7:0] <= write_data[7:0];
                    if(write_byteenable[1])
                        mtimecmp[short_hart_id][15:8] <= write_data[15:8];
                    if(write_byteenable[2])
                        mtimecmp[short_hart_id][23:16] <= write_data[23:16];
                    if(write_byteenable[3])
                        mtimecmp[short_hart_id][31:24] <= write_data[31:24];
                end else begin
                    if(write_byteenable[0])
                        mtimecmp[short_hart_id][39:32] <= write_data[7:0];
                    if(write_byteenable[1])
                        mtimecmp[short_hart_id][47:40] <= write_data[15:8];
                    if(write_byteenable[2])
                        mtimecmp[short_hart_id][55:48] <= write_data[23:16];
                    if(write_byteenable[3])
                        mtimecmp[short_hart_id][63:56] <= write_data[31:24];
                end
            end
        end

    end
end


always @* begin : read_data_always_comb
    read_data = 0;
    if(msip_sel && hart_id_valid)
        read_data[0] = hart_m_swi[short_hart_id];
    else if(ssip_sel && hart_id_valid)
        read_data[0] = hart_s_swi[short_hart_id];
    else if(mtimecmp_sel && hart_id_valid) 
        read_data = mtimecmp[short_hart_id][32*high_sel+:32];
    else if(mtime_sel && hart_id_valid)
        read_data = mtime[32*high_sel+:32];
end

`ifdef FORMAL_RULES
always @(posedge clk) begin
    if(read)
        assert(!(|address[1:0]));
    if(write)
        assert(!(|address[1:0]));
end
`endif

endmodule

`include "armleocpu_undef.vh"
