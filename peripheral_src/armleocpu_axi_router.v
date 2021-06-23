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
    output reg  [1:0]   upstream_axi_rresp,
    output reg          upstream_axi_rlast,
    output reg  [DATA_WIDTH-1:0]
                        upstream_axi_rdata,
    output reg  [ID_WIDTH-1:0]
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
    input wire [OPT_NUMBER_OF_CLIENTS*ID_WIDTH-1:0]
                        downstream_axi_bid,


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


armleocpu_axi_write_router #(
    .ADDR_WIDTH                 (ADDR_WIDTH),
    .ID_WIDTH                   (ID_WIDTH),
    .DATA_WIDTH                 (DATA_WIDTH),

    .OPT_NUMBER_OF_CLIENTS      (OPT_NUMBER_OF_CLIENTS),
    .REGION_COUNT               (REGION_COUNT),
    .REGION_CLIENT_NUM          (REGION_CLIENT_NUM),
    .REGION_BASE_ADDRS          (REGION_BASE_ADDRS),
    .REGION_END_ADDRS           (REGION_END_ADDRS),
    .REGION_CLIENT_BASE_ADDRS   (REGION_CLIENT_BASE_ADDRS)
) write_router (
    .clk                        (clk),
    .rst_n                      (rst_n),

    .upstream_axi_awvalid       (upstream_axi_awvalid),
    .upstream_axi_awready       (upstream_axi_awready),
    .upstream_axi_awaddr        (upstream_axi_awaddr),
    .upstream_axi_awlen         (upstream_axi_awlen),
    .upstream_axi_awsize        (upstream_axi_awsize),
    .upstream_axi_awburst       (upstream_axi_awburst),
    .upstream_axi_awlock        (upstream_axi_awlock),
    .upstream_axi_awid          (upstream_axi_awid),
    .upstream_axi_awprot        (upstream_axi_awprot),

    .upstream_axi_wvalid        (upstream_axi_wvalid),
    .upstream_axi_wready        (upstream_axi_wready),
    .upstream_axi_wdata         (upstream_axi_wdata),
    .upstream_axi_wstrb         (upstream_axi_wstrb),
    .upstream_axi_wlast         (upstream_axi_wlast),

    .upstream_axi_bvalid        (upstream_axi_bvalid),
    .upstream_axi_bready        (upstream_axi_bready),
    .upstream_axi_bresp         (upstream_axi_bresp),
    .upstream_axi_bid           (upstream_axi_bid),

    .downstream_axi_awvalid     (downstream_axi_awvalid),
    .downstream_axi_awready     (downstream_axi_awready),
    .downstream_axi_awaddr      (downstream_axi_awaddr),
    .downstream_axi_awlen       (downstream_axi_awlen),
    .downstream_axi_awsize      (downstream_axi_awsize),
    .downstream_axi_awburst     (downstream_axi_awburst),
    .downstream_axi_awlock      (downstream_axi_awlock),
    .downstream_axi_awid        (downstream_axi_awid),
    .downstream_axi_awprot      (downstream_axi_awprot),


    .downstream_axi_wvalid      (downstream_axi_wvalid),
    .downstream_axi_wready      (downstream_axi_wready),
    .downstream_axi_wdata       (downstream_axi_wdata),
    .downstream_axi_wstrb       (downstream_axi_wstrb),
    .downstream_axi_wlast       (downstream_axi_wlast),

    .downstream_axi_bvalid      (downstream_axi_bvalid),
    .downstream_axi_bready      (downstream_axi_bready),
    .downstream_axi_bresp       (downstream_axi_bresp),
    .downstream_axi_bid         (downstream_axi_bid)

);


armleocpu_axi_read_router #(
    .ADDR_WIDTH                 (ADDR_WIDTH),
    .ID_WIDTH                   (ID_WIDTH),
    .DATA_WIDTH                 (DATA_WIDTH),

    .OPT_NUMBER_OF_CLIENTS      (OPT_NUMBER_OF_CLIENTS),
    .REGION_COUNT               (REGION_COUNT),
    .REGION_CLIENT_NUM          (REGION_CLIENT_NUM),
    .REGION_BASE_ADDRS          (REGION_BASE_ADDRS),
    .REGION_END_ADDRS           (REGION_END_ADDRS),
    .REGION_CLIENT_BASE_ADDRS   (REGION_CLIENT_BASE_ADDRS)
) read_router (
    .clk                        (clk),
    .rst_n                      (rst_n),
    
    .upstream_axi_arvalid       (upstream_axi_arvalid),
    .upstream_axi_arready       (upstream_axi_arready),
    .upstream_axi_araddr        (upstream_axi_araddr),
    .upstream_axi_arlen         (upstream_axi_arlen),
    .upstream_axi_arsize        (upstream_axi_arsize),
    .upstream_axi_arburst       (upstream_axi_arburst),
    .upstream_axi_arid          (upstream_axi_arid),
    .upstream_axi_arlock        (upstream_axi_arlock),
    .upstream_axi_arprot        (upstream_axi_arprot),

    .upstream_axi_rvalid        (upstream_axi_rvalid),
    .upstream_axi_rready        (upstream_axi_rready),
    .upstream_axi_rresp         (upstream_axi_rresp),
    .upstream_axi_rlast         (upstream_axi_rlast),
    .upstream_axi_rdata         (upstream_axi_rdata),
    .upstream_axi_rid           (upstream_axi_rid),

    .downstream_axi_arvalid     (downstream_axi_arvalid),
    .downstream_axi_arready     (downstream_axi_arready),
    .downstream_axi_araddr      (downstream_axi_araddr),
    .downstream_axi_arlen       (downstream_axi_arlen),
    .downstream_axi_arsize      (downstream_axi_arsize),
    .downstream_axi_arburst     (downstream_axi_arburst),
    .downstream_axi_arid        (downstream_axi_arid),
    .downstream_axi_arlock      (downstream_axi_arlock),
    .downstream_axi_arprot      (downstream_axi_arprot),

    .downstream_axi_rvalid      (downstream_axi_rvalid),
    .downstream_axi_rready      (downstream_axi_rready),
    .downstream_axi_rresp       (downstream_axi_rresp),
    .downstream_axi_rlast       (downstream_axi_rlast),
    .downstream_axi_rdata       (downstream_axi_rdata),
    .downstream_axi_rid         (downstream_axi_rid)

    
);


endmodule

`include "armleocpu_undef.vh"