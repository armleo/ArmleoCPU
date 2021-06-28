////////////////////////////////////////////////////////////////////////////////
//
// Filename:    armleocpu_axi_write_router.v
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
// Copyright (C) 2021, Arman Avetisyan
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_axi_write_router #(
    parameter ADDR_WIDTH = 34,
    parameter ID_WIDTH = 4,
    parameter DATA_WIDTH = 32,
    localparam DATA_STROBES = DATA_WIDTH / 8,
    localparam SIZE_WIDTH = 3,

    parameter OPT_NUMBER_OF_CLIENTS = 2,
    localparam OPT_NUMBER_OF_CLIENTS_CLOG2 = $clog2(OPT_NUMBER_OF_CLIENTS),
    
    parameter REGION_COUNT = OPT_NUMBER_OF_CLIENTS,
    parameter [(REGION_COUNT * OPT_NUMBER_OF_CLIENTS_CLOG2) - 1:0] REGION_CLIENT_NUM = 0,
    parameter [(REGION_COUNT * ADDR_WIDTH) - 1:0] REGION_BASE_ADDRS = 0,
    parameter [(REGION_COUNT * ADDR_WIDTH) - 1:0] REGION_END_ADDRS = 0,
    parameter [(REGION_COUNT * ADDR_WIDTH) - 1:0] REGION_CLIENT_BASE_ADDRS = 0
) (
    input clk,
    input rst_n,


    // client port, connects to CPU or other host
    input wire          upstream_axi_awvalid,
    output reg          upstream_axi_awready,
    input wire  [ADDR_WIDTH-1:0]
                        upstream_axi_awaddr,
    input wire  [7:0]   upstream_axi_awlen,
    input wire  [SIZE_WIDTH-1:0]
                        upstream_axi_awsize,
    input wire  [1:0]   upstream_axi_awburst,
    input wire          upstream_axi_awlock,
    input wire  [ID_WIDTH-1:0]
                        upstream_axi_awid,
    input wire  [2:0]   upstream_axi_awprot,

    // AXI W Bus
    input wire          upstream_axi_wvalid,
    output reg          upstream_axi_wready,
    input wire  [DATA_WIDTH-1:0]
                        upstream_axi_wdata,
    input wire  [DATA_STROBES-1:0]
                        upstream_axi_wstrb,
    input wire          upstream_axi_wlast,
    
    // AXI B Bus
    output reg          upstream_axi_bvalid,
    input wire          upstream_axi_bready,
    output reg [1:0]    upstream_axi_bresp,
    output reg [ID_WIDTH-1:0]
                        upstream_axi_bid,

    
    // Host port, connects to peripheral
    // AXI AW Bus
    output reg [OPT_NUMBER_OF_CLIENTS-1:0]
                        downstream_axi_awvalid,
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        downstream_axi_awready,
    output wire [ADDR_WIDTH-1:0]
                        downstream_axi_awaddr,
    output wire [8-1:0]
                        downstream_axi_awlen,
    output wire [SIZE_WIDTH-1:0]
                        downstream_axi_awsize,
    output wire [2-1:0]
                        downstream_axi_awburst,
    output wire [0:0]   downstream_axi_awlock,
    output wire [3-1:0]
                        downstream_axi_awprot,
    output wire [ID_WIDTH-1:0]
                        downstream_axi_awid,
    // TODO: Add all signals

    // AXI W Bus
    output reg [OPT_NUMBER_OF_CLIENTS-1:0] 
                        downstream_axi_wvalid,
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0] 
                        downstream_axi_wready,
    output wire [DATA_WIDTH-1:0]
                        downstream_axi_wdata,
    output wire [DATA_STROBES-1:0]
                        downstream_axi_wstrb,
    output wire [0:0]
                        downstream_axi_wlast,
    
    // AXI B Bus
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        downstream_axi_bvalid,
    output reg [OPT_NUMBER_OF_CLIENTS-1:0]
                        downstream_axi_bready,
    input  wire [OPT_NUMBER_OF_CLIENTS*2-1:0]
                        downstream_axi_bresp,
    input wire [OPT_NUMBER_OF_CLIENTS*ID_WIDTH-1:0]
                        downstream_axi_bid
);

localparam IDLE = 4'd0;
localparam ACTIVE = 4'd1;
localparam DECERR = 4'd2;



`DEFINE_REG_REG_NXT(OPT_NUMBER_OF_CLIENTS_CLOG2, w_client_select, w_client_select_nxt, clk)
`DEFINE_REG_REG_NXT(4, wstate, wstate_nxt, clk)
`DEFINE_REG_REG_NXT(1, awdone, awdone_nxt, clk)
`DEFINE_REG_REG_NXT(1, wdone, wdone_nxt, clk)
`DEFINE_REG_REG_NXT(ADDR_WIDTH, waddr, waddr_nxt, clk)
`DEFINE_REG_REG_NXT(ID_WIDTH, wid, wid_nxt, clk)


assign downstream_axi_awaddr = waddr_nxt;
assign downstream_axi_awlen = upstream_axi_awlen;
assign downstream_axi_awsize = upstream_axi_awsize;
assign downstream_axi_awburst = upstream_axi_awburst;
assign downstream_axi_awlock = upstream_axi_awlock;
assign downstream_axi_awprot = upstream_axi_awprot;
assign downstream_axi_awid = upstream_axi_awid;

assign downstream_axi_wdata = upstream_axi_wdata;
assign downstream_axi_wstrb = upstream_axi_wstrb;
assign downstream_axi_wlast = upstream_axi_wlast;


integer i;

always @* begin
    `ifdef SIMULATION
    #1
    `endif
    w_client_select_nxt = w_client_select;
    wstate_nxt = wstate;

    awdone_nxt = awdone;
    wdone_nxt = wdone;

    waddr_nxt = waddr;
    wid_nxt = wid;


    upstream_axi_awready = 0;
    upstream_axi_wready = 0;

    upstream_axi_bvalid = 0;
    upstream_axi_bresp = downstream_axi_bresp[`ACCESS_PACKED(w_client_select, 2)];
    upstream_axi_bid = downstream_axi_bid[`ACCESS_PACKED(w_client_select, ID_WIDTH)];
    for(i = 0; i < OPT_NUMBER_OF_CLIENTS; i = i + 1) begin
        downstream_axi_bready[i] = 0;
        downstream_axi_awvalid[i] = 0;
        downstream_axi_wvalid[i] = 0;
    end

    if(!rst_n) begin
        wstate_nxt = IDLE;
        awdone_nxt = 0;
        wdone_nxt = 0;
    end else begin
    if(wstate == IDLE) begin
        if(upstream_axi_awvalid) begin
            for(i = 0; i < REGION_COUNT; i = i + 1) begin
                if((upstream_axi_awaddr >= REGION_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)])
                    && 
                        (upstream_axi_awaddr < REGION_END_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)])) begin
                    w_client_select_nxt = 
                        REGION_CLIENT_NUM[`ACCESS_PACKED(i, OPT_NUMBER_OF_CLIENTS_CLOG2)];
                    wstate_nxt = ACTIVE;
                    waddr_nxt = upstream_axi_awaddr - REGION_CLIENT_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)];
                end
            end
            if(wstate_nxt != ACTIVE) begin
                wstate_nxt = DECERR;
                wid_nxt = upstream_axi_awid;
                upstream_axi_awready = 1;
            end
        end
    end else if(wstate == ACTIVE) begin
        // AW handshake signals
        downstream_axi_awvalid[w_client_select] = upstream_axi_awvalid & !awdone;
        upstream_axi_awready = downstream_axi_awready[w_client_select] & !awdone;


        if(upstream_axi_awready && upstream_axi_awvalid)
            awdone_nxt = 1;
        

        // W handshake signals
        downstream_axi_wvalid[w_client_select] = upstream_axi_wvalid  & !wdone;
        upstream_axi_wready = downstream_axi_wready[w_client_select] & !wdone;

        if(downstream_axi_wvalid && downstream_axi_wlast && downstream_axi_wready) begin
            wdone_nxt = 1;
        end

        // State signals
        if(wdone && awdone) begin
            upstream_axi_bvalid = downstream_axi_bvalid[w_client_select];
            downstream_axi_bready[w_client_select] = upstream_axi_bready;
        end
        // Note: upstream_axi_bvalid is set only after both W and AW are done
        // So if above will happen only after both signals are set
        if(upstream_axi_bvalid && upstream_axi_bready) begin
            wstate_nxt = IDLE;
            awdone_nxt = 0;
            wdone_nxt = 0;
        end
    end else if(wstate == DECERR) begin
        upstream_axi_wready = !wdone;
        if(upstream_axi_wready && upstream_axi_wvalid && upstream_axi_wlast) begin
            wdone_nxt = 1;
        end

        if(wdone) begin
            upstream_axi_bvalid = 1;
            upstream_axi_bresp = `AXI_RESP_DECERR;
            upstream_axi_bid = wid;
            if(upstream_axi_bready) begin
                wdone_nxt = 0;
                wstate_nxt = IDLE;
            end
        end
    end
    end
end



endmodule

`include "armleocpu_undef.vh"