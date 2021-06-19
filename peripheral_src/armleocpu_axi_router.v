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
//  REGION_COUNT, REGION_BASE_ADDRS, REGION_END_ADDRS, REGION_CLIENT_BASE_ADDRS
//      REGION_BASE_ADDRS, REGION_END_ADDR, REGION_CLIENT_BASE_ADDRS is 4KB aligned
//      Memory is divided into regions. Each region can be mapped to single client
//      Multiple regions can be mapped to single client
//      REGION_COUNT controls how many regions does router have
//      When host sends address request, address is decoded
//      Each region is iterated. Only one region should be mapped to single address location
//      When address matches the region (condition is = addr >= REGION_BASE_ADDRS && addr < REGION_BASE_ADDRS + REGION_BASE_LENGTH)
//      If request falls outside, then DECERR is returned
// Copyright (C) 2021, Arman Avetisyan
////////////////////////////////////////////////////////////////////////////////


module armleocpu_axi_router #(
    parameter ADDR_WIDTH = 34,
    parameter DATA_WIDTH = 32,
    parameter ID_WIDTH = 4,
    localparam DATA_STROBES = DATA_WIDTH / 8,

    parameter OPT_NUMBER_OF_CLIENTS = 2,
    localparam OPT_NUMBER_OF_CLIENTS_CLOG2 = $clog2(OPT_NUMBER_OF_CLIENTS),
    
    parameter REGION_COUNT = OPT_NUMBER_OF_CLIENTS,
    parameter [REGION_COUNT * OPT_NUMBER_OF_CLIENTS_CLOG2 -1:0] REGION_CLIENT_NUM,
    parameter [REGION_COUNT * ADDR_WIDTH-1:0] REGION_BASE_ADDRS,
    parameter [REGION_COUNT * ADDR_WIDTH-1:0] REGION_END_ADDRS,
    parameter [REGION_COUNT * ADDR_WIDTH-1:0] REGION_CLIENT_BASE_ADDRS
) (
    input clk,
    input rst_n,

    // client port, connects to CPU or other host
    input wire          fromhost_axi_awvalid;
    output reg          fromhost_axi_awready;
    input wire  [ADDR_WIDTH-1:0]
                        fromhost_axi_awaddr;
    input wire  [7:0]   fromhost_axi_awlen;
    input wire  [SIZE_WIDTH-1:0]
                        fromhost_axi_awsize;
    input wire  [1:0]   fromhost_axi_awburst;
    input wire          fromhost_axi_awlock;
    input wire  [ID_WIDTH-1:0]
                        fromhost_axi_awid;
    input wire  [2:0]   fromhost_axi_awprot;

    // AXI W Bus
    input wire          fromhost_axi_wvalid;
    output reg          fromhost_axi_wready;
    input wire  [DATA_WIDTH-1:0]
                        fromhost_axi_wdata;
    input wire  [DATA_STROBES-1:0]
                        fromhost_axi_wstrb;
    input wire          fromhost_axi_wlast;
    
    // AXI B Bus
    output reg          fromhost_axi_bvalid;
    input wire          fromhost_axi_bready;
    output reg [1:0]    fromhost_axi_bresp;
    output reg [ID_WIDTH-1:0]
                        fromhost_axi_bid;
    
    
    input wire          fromhost_axi_arvalid;
    output reg          fromhost_axi_arready;
    input wire  [ADDR_WIDTH-1:0]
                        fromhost_axi_araddr;
    input wire  [7:0]   fromhost_axi_arlen;
    input wire  [SIZE_WIDTH-1:0]
                        fromhost_axi_arsize;
    input wire  [1:0]   fromhost_axi_arburst;
    input wire  [ID_WIDTH-1:0]
                        fromhost_axi_arid;
    input wire          fromhost_axi_arlock;
    input wire  [2:0]   fromhost_axi_arprot;
    

    output reg          fromhost_axi_rvalid;
    input wire          fromhost_axi_rready;
    output reg  [1:0]   fromhost_axi_rresp;
    output reg          fromhost_axi_rlast;
    output reg  [DATA_WIDTH-1:0]
                        fromhost_axi_rdata;
    output reg [ID_WIDTH-1:0]
                        fromhost_axi_rid;

    // Host port, connects to peripheral
    // AXI AW Bus
    output reg [OPT_NUMBER_OF_CLIENTS-1:0]
                        toperipheral_axi_awvalid,
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        toperipheral_axi_awready,
    output reg [ADDR_WIDTH-1:0]
                        toperipheral_axi_awaddr,
    output wire [8-1:0]
                        toperipheral_axi_awlen,
    output wire [SIZE_WIDTH-1:0]
                        toperipheral_axi_awsize,
    output wire [2-1:0]
                        toperipheral_axi_awburst,
    output wire
                        toperipheral_axi_awlock,
    output wire [3-1:0]
                        toperipheral_axi_awprot,
    output wire [ID_WIDTH-1:0]
                        toperipheral_axi_awid,
    // TODO: Add all signals

    // AXI W Bus
    output reg [OPT_NUMBER_OF_CLIENTS-1:0] 
                        toperipheral_axi_wvalid,
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0] 
                        toperipheral_axi_wready,
    output wire [DATA_WIDTH-1:0]
                        toperipheral_axi_wdata,
    output wire [DATA_STROBES-1:0]
                        toperipheral_axi_wstrb,
    output wire
                        toperipheral_axi_wlast,
    
    // AXI B Bus
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        toperipheral_axi_bvalid,
    output reg [OPT_NUMBER_OF_CLIENTS-1:0]
                        toperipheral_axi_bready,
    input  wire [OPT_NUMBER_OF_CLIENTS*2-1:0]
                        toperipheral_axi_bresp,
    


    output reg  [OPT_NUMBER_OF_CLIENTS-1:0]
                        toperipheral_axi_arvalid,
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        toperipheral_axi_arready,
    output reg  [ADDR_WIDTH-1:0]
                        toperipheral_axi_araddr,
    output wire [8-1:0]
                        toperipheral_axi_arlen,
    output wire [SIZE_WIDTH-1:0]
                        toperipheral_axi_arsize,
    output wire [2-1:0]
                        toperipheral_axi_arburst,
    output wire
                        toperipheral_axi_arlock,
    output wire [3-1:0]
                        toperipheral_axi_arprot,
    output wire [ID_WIDTH-1:0]
                        toperipheral_axi_arid,
    

    input wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        toperipheral_axi_rvalid,
    output reg [OPT_NUMBER_OF_CLIENTS-1:0]
                        toperipheral_axi_rready,
    input wire [OPT_NUMBER_OF_CLIENTS*2-1:0]
                        toperipheral_axi_rresp,
    input wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        toperipheral_axi_rlast,
    input wire [OPT_NUMBER_OF_CLIENTS*DATA_WIDTH-1:0]
                        toperipheral_axi_rdata

);

genvar m;
generate
for(m = 0; m < OPT_NUMBER_OF_CLIENTS; ) begin
    toperipheral_axi_araddr = addr_nxt;
    toperipheral_axi_arlen = fromhost_axi_arlen;
    toperipheral_axi_arlen = fromhost_axi_awsize;
    toperipheral_axi_awburst = fromhost_axi_awburst;
    toperipheral_axi_awlock = fromhost_axi_awlock;
    toperipheral_axi_awid = fromhost_axi_awid;

    toperipheral_axi_wdata = fromhost_axi_wdata;
    toperipheral_axi_wstrb = fromhost_axi_wstrb;
    toperipheral_axi_wlast = fromhost_axi_wlast;


    
end
endgenerate

`ifdef ACCESS_PACKED(idx, len) (idx+1)*len : idx*len

integer i;
always @* begin
    r_client_select_nxt = r_client_select;
    
    rstate_nxt = rstate;
    ardone_nxt = ardone;

    raddr_nxt = addr;
    rid_nxt = rid;
    rlen_nxt = rlen;



    w_client_select_nxt = w_client_select;
    wstate_nxt = wstate;

    awdone_nxt = awdone;
    wdone_nxt = wdone;

    waddr_nxt = waddr;
    wid_nxt = wid;
    // No wlen require, because wlast exists

    fromhost_axi_arready = 0;
    for(i = 0; i < OPT_NUMBER_OF_CLIENTS; i = i + 1) begin
        toperipheral_axi_arvalid[i] = 0;
    end

    if(!rst_n) begin
        rstate_nxt = IDLE;
        wstate_nxt = IDLE;
        awdone_nxt = 0;
        ardone_nxt = 0;
        wdone_nxt = 0;
    end else begin
    if(rstate == IDLE) begin
        if(fromhost_axi_arvalid) begin
            // TODO: Decode input address and go to ACTIVE
            for(i = 0; i < REGION_COUNT; i = i + 1) begin
                if(fromhost_axi_araddr >= REGION_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)]
                    && fromhost_axi_araddr < (REGION_END_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)])) begin
                    r_client_select_nxt = 
                        REGION_CLIENT_NUM[`ACCESS_PACKED(i, OPT_NUMBER_OF_CLIENTS_CLOG2)];
                    rstate_nxt = ACTIVE;
                    raddr_nxt = fromhost_axi_araddr - REGION_CLIENT_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)];
                end
            end
            /*
            if(rstate_nxt != ACTIVE) begin
                fromhost_axi_arready = 1;
                rstate_nxt = DECERR;
                rlen_nxt = fromhost_axi_arlen;
                rid_nxt = fromhost_axi_arid;
            end*/
        end
    end else if(rstate == ACTIVE) begin
        toperipheral_axi_arvalid[r_client_select] = fromhost_axi_arvalid & !ardone;
        fromhost_axi_arready = toperipheral_axi_arready[r_client_select];

        if(fromhost_axi_arready && fromhost_axi_arvalid)
            ardone_nxt = 1;
        toperipheral_axi_rready[r_client_select] = fromhost_axi_rready & !rdone;
        fromhost_axi_rvalid = toperipheral_axi_rvalid[r_client_select];

        fromhost_axi_rdata = toperipheral_axi_rdata[`ACCESS_PACKED(r_client_select, DATA_WIDTH)];
        fromhost_axi_rresp = toperipheral_axi_rresp[`ACCESS_PACKED(r_client_select, 2)];
        fromhost_axi_rlast = toperipheral_axi_rlast[r_client_select];
        
        // Connect everything to selected client
        if(toperipheral_axi_rvalid && toperipheral_axi_rlast && toperipheral_axi_rready) begin
            rdone_nxt = 1;
        end
        if(rdone_nxt || ardone_nxt) begin
            rstate_nxt = IDLE;
            ardone_nxt = 0;
            rdone_nxt = 0;
        end
    end /*else if(rstate == DECERR) begin
        fromhost_axi_rvalid = 1;
        fromhost_axi_rresp = `AXI_RESP_DECERR;
        fromhost_axi_rlast = len == 0;
        fromhost_axi_rid = id;
        fromhost_axi_rdata = 0;
        if(fromhost_axi_rready) begin
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