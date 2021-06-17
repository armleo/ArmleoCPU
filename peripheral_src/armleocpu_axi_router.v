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
//  REGION_COUNT, REGION_BASE_ADDRS, REGION_LENGTHS
//      REGION_BASE_ADDRS is 4KB aligned
//      REGION takes memory space up to REGION_LENGTHS aligned up to 4KB
//      If request falls outside, then DECERR is returned
//	OPT_PASSTHROUGH
//		Dont put register at inputs.
//      Not recommended because for high number of clients
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
    parameter [REGION_COUNT * ADDR_WIDTH-1:0] REGION_CLIENT_BASE_ADDRS,
    parameter [REGION_COUNT * ADDR_WIDTH-1:0] REGION_LENGTHS,

    parameter [0:0] OPT_PASSTHROUGH,

    
) (
    input clk,
    input rst_n,

    // client port, connects to CPU or other host
    input wire          client_axi_awvalid;
    output reg          client_axi_awready;
    input wire  [ADDR_WIDTH-1:0]
                        client_axi_awaddr;
    input wire  [7:0]   client_axi_awlen;
    input wire  [SIZE_WIDTH-1:0]
                        client_axi_awsize;
    input wire  [1:0]   client_axi_awburst;
    input wire          client_axi_awlock;
    input wire  [ID_WIDTH-1:0]
                        client_axi_awid;

    // AXI W Bus
    input wire          client_axi_wvalid;
    output reg          client_axi_wready;
    input wire  [DATA_WIDTH-1:0]
                        client_axi_wdata;
    input wire  [DATA_STROBES-1:0]
                        client_axi_wstrb;
    input wire          client_axi_wlast;
    
    // AXI B Bus
    output reg          client_axi_bvalid;
    input wire          client_axi_bready;
    output reg [1:0]    client_axi_bresp;
    output reg [ID_WIDTH-1:0]
                        client_axi_bid;
    
    
    input wire          client_axi_arvalid;
    output reg          client_axi_arready;
    input wire  [ADDR_WIDTH-1:0]
                        client_axi_araddr;
    input wire  [7:0]   client_axi_arlen;
    input wire  [SIZE_WIDTH-1:0]
                        client_axi_arsize;
    input wire  [1:0]   client_axi_arburst;
    input wire  [ID_WIDTH-1:0]
                        client_axi_arid;
    input wire          client_axi_arlock;
    

    output reg          client_axi_rvalid;
    input wire          client_axi_rready;
    output reg  [1:0]   client_axi_rresp;
    output reg          client_axi_rlast;
    output reg  [DATA_WIDTH-1:0]
                        client_axi_rdata;
    output reg [ID_WIDTH-1:0]
                        client_axi_rid;

    // Host port, connects to peripheral
    // AXI AW Bus
    output reg [OPT_NUMBER_OF_CLIENTS-1:0] 
                        host_axi_awvalid,
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        host_axi_awready,
    output reg [OPT_NUMBER_OF_CLIENTS * ADDR_WIDTH-1:0]
                        host_axi_awaddr,
    output wire [8-1:0]
                        host_axi_awlen,
    output wire [SIZE_WIDTH-1:0]
                        host_axi_awsize,
    output wire [2-1:0]
                        host_axi_awburst,
    output wire
                        host_axi_awlock,
    output wire [3-1:0]
                        host_axi_awprot,
    output wire [ID_WIDTH-1:0]
                        host_axi_awid,
    // TODO: Add all signals

    // AXI W Bus
    output reg [OPT_NUMBER_OF_CLIENTS-1:0] 
                        host_axi_wvalid,
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0] 
                        host_axi_wready,
    output wire [DATA_WIDTH-1:0]
                        host_axi_wdata,
    output wire [DATA_STROBES-1:0]
                        host_axi_wstrb,
    output wire
                        host_axi_wlast,
    
    // AXI B Bus
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        host_axi_bvalid,
    output reg [OPT_NUMBER_OF_CLIENTS-1:0]
                        host_axi_bready,
    input  wire [OPT_NUMBER_OF_CLIENTS*2-1:0]
                        host_axi_bresp,
    


    output reg  [OPT_NUMBER_OF_CLIENTS-1:0]
                        host_axi_arvalid,
    input  wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        host_axi_arready,
    output reg  [OPT_NUMBER_OF_CLIENTS*ADDR_WIDTH-1:0]
                        host_axi_araddr,
    output wire [8-1:0]
                        host_axi_arlen,
    output wire [SIZE_WIDTH-1:0]
                        host_axi_arsize,
    output wire [2-1:0]
                        host_axi_arburst,
    output wire
                        host_axi_arlock,
    output wire [3-1:0]
                        host_axi_arprot,
    output wire [ID_WIDTH-1:0]
                        host_axi_arid,
    

    input wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        host_axi_rvalid,
    output reg [OPT_NUMBER_OF_CLIENTS-1:0]
                        host_axi_rready,
    input wire [OPT_NUMBER_OF_CLIENTS*2-1:0]
                        host_axi_rresp,
    input wire [OPT_NUMBER_OF_CLIENTS-1:0]
                        host_axi_rlast,
    input wire [OPT_NUMBER_OF_CLIENTS*DATA_WIDTH-1:0]
                        host_axi_rdata

);

integer i;
always @* begin
    r_client_select_nxt = r_client_select;
    w_client_select_nxt = w_client_select;
    rstate_nxt = rstate;
    wstate_nxt = wstate;

    id_nxt = ;
    len_nxt = ;

    for(i = 0; i < OPT_NUMBER_OF_CLIENTS; i = i + 1) begin
        host_axi_araddr[i] = client_axi_araddr - REGION_CLIENT_BASE_ADDRS[(r_client_select+1)*ADDR_WIDTH-1:r_client_select*ADDR_WIDTH];

    end

    client_axi_arready = 0;
    if(rstate == IDLE) begin
        if(client_axi_arvalid) begin
            // TODO: Decode input address and go to ACTIVE
            for(i = 0; i < REGION_COUNT; i = i + 1) begin
                if(client_axi_araddr >= REGION_BASE_ADDRS[(i+1)*ADDR_WIDTH-1: i*ADDR_WIDTH]
                    && client_axi_araddr < (REGION_BASE_ADDRS[(i+1)*ADDR_WIDTH-1: i*ADDR_WIDTH] + REGION_LENGTHS[(i+1)*ADDR_WIDTH-1: i*ADDR_WIDTH])) begin
                    r_client_select_nxt = 
                        REGION_CLIENT_NUM[(i+1) * OPT_NUMBER_OF_CLIENTS_CLOG2 - 1: i * OPT_NUMBER_OF_CLIENTS_CLOG2];
                    rstate_nxt = ACTIVE;
                    addr_nxt = client_axi_araddr - REGION_CLIENT_BASE_ADDRS[(i+1)*ADDR_WIDTH-1: i*ADDR_WIDTH];
                end else begin
                    rstate_nxt = DECERR;
                    len_nxt = client_axi_arlen;
                    id_nxt = client_axi_arid;
                end
            end
        end
    end else if(rstate == ACTIVE) begin
        host_axi_arvalid[client_select] = client_axi_arvalid;
        client_axi_arready = host_axi_arready[client_select];

        host_axi_rready[client_select] = client_exi_rready;
        client_axi_rvalid = host_axi_rvalid[client_select];

        // Connect everything to selected client
        if(host_axi_rvalid && host_axi_rlast && host_axi_rready) begin
            rstate_nxt = IDLE;
        end
    end else if(rstate == DECERR) begin
        client_axi_rvalid = 1;
        client_axi_rresp = `AXI_RESP_DECERR;
        client_axi_rlast = len == 0;
        client_axi_rid = id;
        client_axi_rdata = 0;
        if(client_axi_rready) begin
            len_nxt = len_nxt - 1;
        end
    end

    if(wstate == IDLE) begin
        // TODO: Decode input address and go to ACTIVE
    end else if(wstate == ACTIVE) begin
        // Connect everything to selected client
        if(bvalid && bready) begin
            wstate_nxt = IDLE;
        end
    end

end


endmodule