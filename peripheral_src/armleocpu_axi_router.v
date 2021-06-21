////////////////////////////////////////////////////////////////////////////////
//
// Filename:    armleocpu_axi_router.v
// Project:	ArmleoCPU
//
// Purpose:	A basic 1-to-N AXI4 router.
//      This allows multiple AXI4 clients to be connected to same bus
//      and to be mapped to different sections in memory address space
// Deps: armleocpu_defines.vh/undef.vh for AXI4 declarations
// Notes:
//      This routes only signals used by CPU and peripheral in "peripheral_src"
// Parameters:
//	OPT_NUMBER_OF_CLIENTS
//      Number of hosts from 1 to any value that can be synthesized
//      and fits into parameter.
//      Some synthesizer cant synthesize too many for loops.
//  REGION_COUNT, REGION_BASE_ADDRS, REGION_END_ADDRS, REGION_CLIENT_BASE_ADDRS, REGION_CLIENT_NUM
//      REGION_BASE_ADDRS, REGION_END_ADDR, REGION_CLIENT_BASE_ADDRS is 4KB aligned addresses in single unpacked array each
//      Memory is divided into regions. Each region can be mapped to single client
//      Multiple regions can be mapped to single client
//      REGION_COUNT controls how many regions does router have
//      When host sends address request, address is decoded
//      Each region is iterated. Only one region should be mapped to single address location
//      When address matches the region (condition is = addr >= REGION_BASE_ADDRS && addr < REGION_END_ADDRS)
//          If request falls outside, then DECERR is returned
//          Else if request is matched then request is sent to downstream by calculating addr - REGION_CLIENT_BASE_ADDRS
//      Example: (assuming ADDR_WIDTH = 32, OPT_NUMBER_OF_CLIENTS = 2)
//          REGION_COUNT = 2
//          REGION_BASE_ADDRS           = {32'h0000_0000, 32'h0000_1000 }
//          REGION_END_ADDRS            = {32'h0000_1000, 32'h0000_2000 }
//          REGION_CLIENT_BASE_ADDRS    = {32'h0000_0000, 32'h0000_1000 }
//          REGION_CLIENT_NUM           = {1'd0         , 1'd1          }
//          This maps 0x0000-0x1000 to 0x0000-0x1000 of client 0
//          And maps 0x1000-0x2000 to 0x1000-0x2000 of client 1
//          
//          
// Copyright (C) 2021, Arman Avetisyan
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_axi_router #(
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
    output wire [1:0]   upstream_axi_rresp,
    output wire         upstream_axi_rlast,
    output wire [DATA_WIDTH-1:0]
                        upstream_axi_rdata,
    output wire [ID_WIDTH-1:0]
                        upstream_axi_rid,

    
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
                        downstream_axi_rdata

);


localparam IDLE = 4'd0;
localparam ACTIVE = 4'd1;


`DEFINE_REG_REG_NXT(OPT_NUMBER_OF_CLIENTS_CLOG2, r_client_select, r_client_select_nxt, clk)
`DEFINE_REG_REG_NXT(4, rstate, rstate_nxt, clk)
`DEFINE_REG_REG_NXT(4, ardone, ardone_nxt, clk)
`DEFINE_REG_REG_NXT(4, rdone, rdone_nxt, clk)
`DEFINE_REG_REG_NXT(ADDR_WIDTH, raddr, raddr_nxt, clk)
`DEFINE_REG_REG_NXT(ID_WIDTH, rid, rid_nxt, clk)
`DEFINE_REG_REG_NXT(8, rlen, rlen_nxt, clk)

/*
`DEFINE_REG_REG_NXT(OPT_NUMBER_OF_CLIENTS_CLOG2, w_client_select, w_client_select_nxt, clk)
`DEFINE_REG_REG_NXT(4, wstate, wstate_nxt, clk)
`DEFINE_REG_REG_NXT(4, awdone, awdone_nxt, clk)
`DEFINE_REG_REG_NXT(4, wdone, wdone_nxt, clk)*/


assign downstream_axi_araddr = raddr_nxt;
assign downstream_axi_arlen = upstream_axi_arlen;
assign downstream_axi_arsize = upstream_axi_arsize;
assign downstream_axi_arburst = upstream_axi_arburst;
assign downstream_axi_arlock = upstream_axi_arlock;
assign downstream_axi_arprot = upstream_axi_arprot;
assign downstream_axi_arid = upstream_axi_arid;

assign upstream_axi_rdata = downstream_axi_rdata[`ACCESS_PACKED(r_client_select, DATA_WIDTH)];
assign upstream_axi_rresp = downstream_axi_rresp[`ACCESS_PACKED(r_client_select, 2)];
assign upstream_axi_rlast = downstream_axi_rlast[r_client_select];


/*
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
*/

integer i;
always @* begin
    r_client_select_nxt = r_client_select;
    
    rstate_nxt = rstate;
    ardone_nxt = ardone;
    rdone_nxt = rdone;

    raddr_nxt = raddr;
    rid_nxt = rid;
    rlen_nxt = rlen;


    /*
    w_client_select_nxt = w_client_select;
    wstate_nxt = wstate;

    awdone_nxt = awdone;
    wdone_nxt = wdone;

    waddr_nxt = waddr;
    wid_nxt = wid;*/
    // No wlen require, because wlast exists

    upstream_axi_arready = 0;
    upstream_axi_rvalid = 0;
    
    for(i = 0; i < OPT_NUMBER_OF_CLIENTS; i = i + 1) begin
        downstream_axi_arvalid[i] = 0;
        downstream_axi_rready[i] = 0;
    end

    if(!rst_n) begin
        //wstate_nxt = IDLE;
        rstate_nxt = IDLE;
        //awdone_nxt = 0;
        ardone_nxt = 0;
        //wdone_nxt = 0;
        rdone_nxt = 0;
    end else begin
    if(rstate == IDLE) begin
        if(upstream_axi_arvalid) begin
            // TODO: Decode input address and go to ACTIVE
            for(i = 0; i < REGION_COUNT; i = i + 1) begin
                if(upstream_axi_araddr >= REGION_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)]
                    && upstream_axi_araddr < (REGION_END_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)])) begin
                    r_client_select_nxt = 
                        REGION_CLIENT_NUM[`ACCESS_PACKED(i, OPT_NUMBER_OF_CLIENTS_CLOG2)];
                    rstate_nxt = ACTIVE;
                    raddr_nxt = upstream_axi_araddr - REGION_CLIENT_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)];
                end
            end
            /*
            if(rstate_nxt != ACTIVE) begin
                upstream_axi_arready = 1;
                rstate_nxt = DECERR;
                rlen_nxt = upstream_axi_arlen;
                rid_nxt = upstream_axi_arid;
            end*/
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

        if(downstream_axi_rvalid && downstream_axi_rlast && downstream_axi_rready) begin
            rdone_nxt = 1;
        end

        // State signals
        if(rdone_nxt || ardone_nxt) begin
            rstate_nxt = IDLE;
            ardone_nxt = 0;
            rdone_nxt = 0;
        end
    end /*else if(rstate == DECERR) begin
        upstream_axi_rvalid = 1;
        upstream_axi_rresp = `AXI_RESP_DECERR;
        upstream_axi_rlast = len == 0;
        upstream_axi_rid = id;
        upstream_axi_rdata = 0;
        if(upstream_axi_rready) begin
            len_nxt = len_nxt - 1;
            if(len == 0) begin
                rstate_nxt = IDLE;
            end

        end
        
    end

    if(wstate == IDLE) begin
        // TODO: Decode input address and go to ACTIVE
    end else if(wstate == ACTIVE) begin
        // Connect everything to selected client
        // TODO: wdone/awdone so only one transaction is passed by
        if(bvalid && bready) begin
            wstate_nxt = IDLE;
        end
    end*/
    end // rst_n
end

endmodule

`include "armleocpu_undef.vh"