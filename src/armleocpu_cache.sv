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
// Filename: armleocpu_cache.v
// Project:	ArmleoCPU
//
// Description:
//      
//      req is first cycle <- Registers input and sends TLB/storage request
//      s1 is second cycle <- Inputs are registered and TLB/storage response are valid
//              In this cycle we make a decisions and send/recv data from backing memory
//              Then we send a bus-aligned data to next stage
//      resp is last cycle <- in this stage we do bus to register aligment and return response
//              If faster clocks need to be achieved this value can be registered
//              using register slice
//      Note: Cache will not start new AXI4 transaction until response is accepted
//          This is done intentionally to let D-Bus transaction to be accepted and done by
//          the time I-Bus starts new transaction, making deadlock impossible
// Purpose:	Cache for ArmleoCPU
//      Write-through, physically tagged, multi-way.
// Warning:
//      All cache locations should be 64 byte aligned.
//      
//      This requirement is because if AXI4 returns error after Cache
//      already responded to core there is no more way to notify it about
//      Accessfault. So instead we require that all cache-able locations
//      be aligned to 64 byte boundaries.
//      
//      This ensures that no error is returned for 64 byte burst used in refill
//      so is possible to return early response to requester
//      
// Parameters:
//      WAYS: 1..16 Specifies how many ways are implemented
//      TLB_ENTRIES_W: 1..16 See TLB parameters
//      TLB_WAYS: 1..16 See TLB parameters
//      LANES_W: 1..6 How many lanes per way
//      IS_INSTRUCTION_CACHE: 1 or 0. Is used to calculare AXI4 Prot parameter
//		
//
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`ifdef DEBUG_CACHE
`include "assert.vh"
`endif

`TIMESCALE_DEFINE

// TODO: Remove below
// verilator lint_off UNUSED
// verilator lint_off UNDRIVEN

module armleocpu_cache (
    input wire              clk,
    input wire              rst_n,

    output reg              resp_valid,
    output logic [31:0]     resp_load_data,
    output logic  [3:0]     resp_status,
    input wire              resp_ready,

    input wire              req_valid,
    input wire    [3:0]     req_cmd,
    input wire   [31:0]     req_address,
    input wire    [2:0]     req_load_type,
    input wire    [1:0]     req_store_type,
    input wire   [31:0]     req_store_data,
    input wire              req_csr_satp_mode_in, // Mode = 0 -> physical access, 1 -> virtual
    input wire   [21:0]     req_csr_satp_ppn_in,
    input wire              req_csr_mstatus_mprv_in,
    input wire              req_csr_mstatus_mxr_in,
    input wire              req_csr_mstatus_sum_in,
    input wire    [1:0]     req_csr_mstatus_mpp_in,
    input wire    [1:0]     req_csr_mcurrent_privilege_in,
    output logic            req_ready,

    `CACHE_AXI_IO(io_axi_)
    
);


// |------------------------------------------------|
// |                                                |
// |              Parameters and includes           |
// |                                                |
// |------------------------------------------------|

parameter WAYS = 2; // 2..16
localparam WAYS_W = $clog2(WAYS);

parameter TLB_ENTRIES_W = 2; // 1..16
parameter TLB_WAYS = 2; // 1..16
localparam TLB_WAYS_W = $clog2(TLB_WAYS);

parameter LANES_W = 1; // 1..6 range.
localparam LANES = 2**LANES_W;

parameter [0:0] IS_INSTRUCTION_CACHE = 0;

parameter AXI_PASSTHROUGH = 0;

// 4 = 16 words each 32 bit = 64 byte
localparam OFFSET_W = 4;
localparam INWORD_OFFSET_W = 2;
localparam WORDS_IN_LANE = 2**OFFSET_W;

localparam CACHE_PHYS_TAG_W = 34 - (LANES_W + OFFSET_W + INWORD_OFFSET_W);
localparam TLB_PHYS_TAG_W = 34 - (6 + OFFSET_W + INWORD_OFFSET_W);
// 34 = size of address
// tag = 34 to 
localparam VIRT_TAG_W = 20;
localparam PHYSICAL_ADDRESS_WIDTH = 34; // Can't be changed because in some places constant 34 is used


// AXI Register Slice

// AXI AW Bus
logic         axi_awvalid;
logic         axi_awready;
logic [33:0]  axi_awaddr;
logic         axi_awlock;
logic [2:0]   axi_awprot;

// AXI W Bus
logic         axi_wvalid;
logic         axi_wready;
logic [31:0]  axi_wdata;
logic [3:0]   axi_wstrb;
logic         axi_wlast;

// AXI B Bus
logic         axi_bvalid;
logic         axi_bready;
logic [1:0]   axi_bresp;

logic         axi_arvalid;
logic         axi_arready;
logic [33:0]  axi_araddr;
logic [7:0]   axi_arlen;
logic [1:0]   axi_arburst;
logic         axi_arlock;
logic [2:0]   axi_arprot;

logic         axi_rvalid;
logic         axi_rready;
logic [1:0]   axi_rresp;
logic         axi_rlast;
logic [31:0]  axi_rdata;


// AW Bus
armleocpu_register_slice #(
    .DW(PHYSICAL_ADDRESS_WIDTH + 1+ 3),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_aw (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (axi_awvalid),
    .in_ready   (axi_awready),
    .in_data({
        axi_awaddr,
        axi_awlock,
        axi_awprot
    }),

    .out_valid(io_axi_awvalid),
    .out_ready(io_axi_awready),
    .out_data({
        io_axi_awaddr,
        io_axi_awlock,
        io_axi_awprot
    })
);

// W Bus
armleocpu_register_slice #(
    .DW(32 + 4 + 1),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_w(
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (axi_wvalid),
    .in_ready   (axi_wready),
    .in_data    ({
        axi_wdata,
        axi_wstrb,
        axi_wlast
    }),

    .out_valid  (io_axi_wvalid),
    .out_ready  (io_axi_wready),
    .out_data   ({
        io_axi_wdata,
        io_axi_wstrb,
        io_axi_wlast
    })
);

// B Bus
armleocpu_register_slice #(
    .DW(2),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_b(
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (io_axi_bvalid),
    .in_ready   (io_axi_bready),
    .in_data    ({
        io_axi_bresp
    }),

    .out_valid  (axi_bvalid),
    .out_ready  (axi_bready),
    .out_data   ({
        axi_bresp
    })
);

// AR Bus
armleocpu_register_slice #(
    .DW(PHYSICAL_ADDRESS_WIDTH + 2 + 8 + 1 + 3),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_ar (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (axi_arvalid),
    .in_ready   (axi_arready),
    .in_data({
        axi_araddr,
        axi_arburst,
        axi_arlen,
        axi_arlock,
        axi_arprot
    }),

    .out_valid(io_axi_arvalid),
    .out_ready(io_axi_arready),
    .out_data({
        io_axi_araddr,
        io_axi_arburst,
        io_axi_arlen,
        io_axi_arlock,
        io_axi_arprot
    })
);

// R Bus
armleocpu_register_slice #(
    .DW(2 + 1 + 32),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_r (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (io_axi_rvalid),
    .in_ready   (io_axi_rready),
    .in_data    ({
        io_axi_rresp,
        io_axi_rdata,
        io_axi_rlast
    }),

    .out_valid  (axi_rvalid),
    .out_ready  (axi_rready),
    .out_data   ({
        axi_rresp,
        axi_rdata,
        axi_rlast
    })
);


// |------------------------------------------------|
// |                                                |
// |              Signals                           |
// |                                                |
// |------------------------------------------------|

// Towards req stage
reg                         s1_stall; // Shows REQ that it should not accept a request

// Request internal signals
wire req_cmd_access_request, req_cmd_write, req_cmd_read;

wire [VIRT_TAG_W-1:0] 	    req_address_vtag          = req_address[31:32-VIRT_TAG_W];
reg  [((LANES_W != 6) ? (6-LANES_W-1) : 0) :0] req_address_cptag_low;
wire [LANES_W-1:0]	        req_address_lane          = req_address[INWORD_OFFSET_W+OFFSET_W+LANES_W-1:INWORD_OFFSET_W+OFFSET_W];
wire [OFFSET_W-1:0]			req_address_offset        = req_address[INWORD_OFFSET_W+OFFSET_W-1:INWORD_OFFSET_W];
wire [1:0]			        req_address_inword_offset = req_address[INWORD_OFFSET_W-1:0];


// S1 internal signals and registers
reg                         s1_unknowntype;
reg                         s1_missaligned;
wire                        s1_pagefault,
                            s1_cmd_atomic,
                            s1_cmd_flush,
                            s1_cmd_write,
                            s1_cmd_read;

reg  [CACHE_PHYS_TAG_W-1:0] s1_address_cptag;
// Full tag including physical tag and cache tag low part

`DEFINE_REG_REG_NXT(1,          s1_active, s1_active_nxt, clk)
`DEFINE_REG_REG_NXT(WAYS_W,     victim_way, victim_way_nxt, clk)
`DEFINE_REG_REG_NXT(1,          s1_ar_done, s1_ar_done_nxt, clk)
`DEFINE_REG_REG_NXT(1,          s1_refill_errored, s1_refill_errored_nxt, clk)
`DEFINE_REG_REG_NXT(1,          s1_first_response_done, s1_first_response_done_nxt, clk)
`DEFINE_REG_REG_NXT(1,          s1_aw_done, s1_aw_done_nxt, clk)
`DEFINE_REG_REG_NXT(1,          s1_w_done, s1_w_done_nxt, clk)
`DEFINE_REG_REG_NXT(1,          s1_tlb_write_done, s1_tlb_write_done_nxt, clk)

`DEFINE_REG_REG_NXT(VIRT_TAG_W, s1_address_vtag, s1_address_vtag_nxt, clk)
`DEFINE_REG_REG_NXT(((LANES_W != 6) ? (CACHE_PHYS_TAG_W - TLB_PHYS_TAG_W) : 1),
                                s1_address_cptag_low, s1_address_cptag_low_nxt, clk)
`DEFINE_REG_REG_NXT(LANES_W,    s1_address_lane, s1_address_lane_nxt, clk)
`DEFINE_REG_REG_NXT(OFFSET_W,   s1_address_offset, s1_address_offset_nxt, clk)
`DEFINE_REG_REG_NXT(2,          s1_address_inword_offset, s1_address_inword_offset_nxt, clk)
`DEFINE_REG_REG_NXT(4,          s1_cmd, s1_cmd_nxt, clk)
`DEFINE_REG_REG_NXT(3,          s1_load_type, s1_load_type_nxt, clk)
`DEFINE_REG_REG_NXT(2,          s1_store_type, s1_store_type_nxt, clk)
`DEFINE_REG_REG_NXT(32,         s1_store_data, s1_store_data_nxt, clk)
`DEFINE_REG_REG_NXT(WAYS,       s1_valid_per_way, s1_valid_per_way_nxt, clk)

wire [1:0]                      s1_vm_privilege;
wire                            s1_vm_enabled;

`DEFINE_REG_REG_NXT(22, s1_csr_satp_ppn, s1_csr_satp_ppn_nxt, clk)
`DEFINE_REG_REG_NXT(1, s1_csr_satp_mode, s1_csr_satp_mode_nxt, clk)
`DEFINE_REG_REG_NXT(1, s1_csr_mstatus_mprv, s1_csr_mstatus_mprv_nxt, clk)
`DEFINE_REG_REG_NXT(1, s1_csr_mstatus_mxr, s1_csr_mstatus_mxr_nxt, clk)
`DEFINE_REG_REG_NXT(1, s1_csr_mstatus_sum, s1_csr_mstatus_sum_nxt, clk)

`DEFINE_REG_REG_NXT(2, s1_csr_mstatus_mpp, s1_csr_mstatus_mpp_nxt, clk)
`DEFINE_REG_REG_NXT(2, s1_csr_mcurrent_privilege, s1_csr_mcurrent_privilege_nxt, clk)

// S1 towards req
reg                             s1_restart;
// Ignore current request and s1_stall sginals, restart current active S1 request
// This will cause TLB read and storage fetch

// S1 towards resp
reg                         s1_done; // Shows Resp stage that s1 stage is done
reg   [3:0]                 s1_status; // Towards resp stage, contains cache response status
reg  [31:0]                 s1_loadgen_datain; // Output to resp stage

// Resp towards resp
reg                         resp_stall; // Stall S1, because there is no space for response

// Resp internal signals

`DEFINE_OREG(4, resp_status, `CACHE_RESPONSE_SUCCESS)
`DEFINE_REG_REG_NXT(1, resp_active, resp_active_nxt, clk)
`DEFINE_REG_REG_NXT(3, resp_load_type, resp_load_type_nxt, clk)
`DEFINE_REG_REG_NXT(2, resp_address_inword_offset, resp_address_inword_offset_nxt, clk)
`DEFINE_REG_REG_NXT(32, resp_loadgen_datain, resp_loadgen_datain_nxt, clk)





wire  [WAYS-1:0]            s1_way_hit;
reg  [WAYS_W-1:0]           s1_cache_hit_way;
reg                         s1_cache_hit;
reg  [31:0]                 s1_readdata; // Output from storage and cache hit logic

// CPTAG port
reg                     cptag_read;
reg  [LANES_W-1:0]      cptag_lane;
wire [CACHE_PHYS_TAG_W-1:0]
                        cptag_readdata       [WAYS-1:0];
reg                     cptag_write          [WAYS-1:0];
reg  [CACHE_PHYS_TAG_W-1:0]
                        cptag_writedata;

//                      Storage read/write port vars
reg  [LANES_W-1:0]      storage_lane;
reg  [OFFSET_W-1:0]     storage_offset;
reg                     storage_read;
wire [31:0]             storage_readdata    [WAYS-1:0];
reg  [WAYS-1:0]         storage_write;
reg  [3:0]              storage_byteenable;
reg  [31:0]             storage_writedata;





reg [LANES-1:0] valid [WAYS-1:0];
reg [LANES-1:0] valid_nxt [WAYS-1:0];


genvar way_num;

generate for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : valid_per_way_register
    always @(posedge clk) begin
        valid[way_num] <= valid_nxt[way_num];
    end
end
endgenerate

genvar byte_offset;
generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : mem_generate_for
    armleocpu_mem_1rw #(
        .ELEMENTS_W(LANES_W),
        .WIDTH(CACHE_PHYS_TAG_W)
    ) ptag_storage (
        .clk(clk),
        .address(cptag_lane),

        .read(cptag_read),
        .readdata(cptag_readdata[way_num]),
        
        .write(cptag_write[way_num]),
        .writedata(cptag_writedata)
    );
    armleocpu_mem_1rwm #(
        .ELEMENTS_W(LANES_W+OFFSET_W),
        .WIDTH(32),
        .GRANULITY(8)
    ) datastorage (
        .clk(clk),
        .address({storage_lane, storage_offset}),
        
        .read(storage_read),
        .readdata(storage_readdata[way_num]),

        .writeenable(storage_byteenable),
        .write(storage_write[way_num]),
        .writedata(storage_writedata)
    );
    
end
endgenerate




// |------------------------------------------------|
// |                   LoadGen                      |
// |------------------------------------------------|


wire                    s1_loadgen_missaligned;
wire                    s1_loadgen_unknowntype;

// Loadgen responsible for missaligned/unknowntype signals
armleocpu_loadgen s1_loadgen(
    .inword_offset          (s1_address_inword_offset),
    .loadgen_type           (s1_load_type),

    // verilator lint_off PINCONNECTEMPTY
    .loadgen_datain         (),

    .loadgen_dataout        (),
    // verilator lint_on PINCONNECTEMPTY
    .loadgen_missaligned    (s1_loadgen_missaligned),
    .loadgen_unknowntype    (s1_loadgen_unknowntype)
);

armleocpu_loadgen resp_loadgen(
    .inword_offset          (resp_address_inword_offset),
    .loadgen_type           (resp_load_type),

    .loadgen_datain         (resp_loadgen_datain),

    .loadgen_dataout        (resp_load_data),
    // verilator lint_off PINCONNECTEMPTY
    .loadgen_missaligned    (), // already done at S1 stage
    .loadgen_unknowntype    ()  // already done at S1 stage
    // verilator lint_on PINCONNECTEMPTY
);

// |------------------------------------------------|
// |                 StoreGen                       |
// |------------------------------------------------|

wire [31:0]             storegen_dataout;
wire [3:0]              storegen_datamask;
wire                    storegen_missaligned;
wire                    storegen_unknowntype;
armleocpu_storegen storegen(
    .inword_offset          (s1_address_inword_offset),
    .storegen_type          (s1_store_type),

    .storegen_datain        (s1_store_data),

    .storegen_dataout       (storegen_dataout),
    .storegen_datamask      (storegen_datamask),
    .storegen_missaligned   (storegen_missaligned),
    .storegen_unknowntype   (storegen_unknowntype)
);



// PTW request signals
reg                     ptw_resolve_request;
reg  [19:0]             ptw_resolve_virtual_address;
// PTW result signals
// verilator lint_off UNOPTFLAT
wire                    ptw_resolve_done;
// verilator lint_on UNOPTFLAT
wire                    ptw_pagefault;
wire                    ptw_accessfault;


`ifdef DEBUG_PAGEFAULT
wire [30*8-1:0] pagefault_reason;
`endif

wire [7:0]                      ptw_resolve_metadata;
wire [TLB_PHYS_TAG_W-1:0]       ptw_resolve_physical_address;
// PTW AXI4 Signals

wire ptw_axi_arvalid,
    ptw_axi_rready;

wire [33:0] ptw_axi_araddr;

// Page table walker instance
armleocpu_ptw ptw(
    .clk                        (clk),
    .rst_n                      (rst_n),

    .axi_arvalid                (ptw_axi_arvalid),
    .axi_arready                (axi_arready),
    .axi_araddr                 (ptw_axi_araddr),

    .axi_rvalid                 (axi_rvalid),
    .axi_rready                 (ptw_axi_rready),
    .axi_rresp                  (axi_rresp),
    .axi_rlast                  (axi_rlast),
    .axi_rdata                  (axi_rdata),
    
    .resolve_request            (ptw_resolve_request),
    .virtual_address            (ptw_resolve_virtual_address), //s1_address_vtag

    .resolve_done               (ptw_resolve_done),
    .resolve_pagefault          (ptw_pagefault),
    .resolve_accessfault        (ptw_accessfault),

    .resolve_metadata           (ptw_resolve_metadata),
    .resolve_physical_address   (ptw_resolve_physical_address),

    .satp_ppn                   (s1_csr_satp_ppn)
);


reg  [1:0]                  tlb_cmd;

// read port
reg  [19:0]                 tlb_vaddr_input;
// For first cycle it's req_address_vtag, for writes it's s1_address_vtag
wire                        tlb_hit;
wire [7:0]                  tlb_metadata_output;
wire [TLB_PHYS_TAG_W-1:0]   tlb_ptag_output;

// write port
reg  [7:0]                  tlb_new_entry_metadata_input;
reg  [TLB_PHYS_TAG_W-1:0]   tlb_new_entry_ptag_input;

wire [TLB_WAYS_W-1:0]       tlb_resolve_way; // Ignored

armleocpu_tlb #(TLB_ENTRIES_W, TLB_WAYS) tlb(
    .rst_n                  (rst_n),
    .clk                    (clk),
    
    .cmd                    (tlb_cmd),

    // read port
    .vaddr_input            (tlb_vaddr_input),
    .hit                    (tlb_hit),
    .resolve_metadata_output(tlb_metadata_output),
    .resolve_ptag_output    (tlb_ptag_output),
    .resolve_way            (tlb_resolve_way),
    
    .new_entry_metadata_input(tlb_new_entry_metadata_input),
    .new_entry_ptag_input    (tlb_new_entry_ptag_input)
);


armleocpu_cache_pagefault pagefault_generator(
    .csr_satp_mode          (s1_csr_satp_mode),

    .csr_mcurrent_privilege (s1_csr_mcurrent_privilege),
    .csr_mstatus_mprv       (s1_csr_mstatus_mprv),
    .csr_mstatus_mxr        (s1_csr_mstatus_mxr),
    .csr_mstatus_sum        (s1_csr_mstatus_sum),
    .csr_mstatus_mpp        (s1_csr_mstatus_mpp),

    .cmd                    (s1_cmd),
    .tlb_read_metadata      (tlb_metadata_output),

    .pagefault              (s1_pagefault)
    `ifdef DEBUG_PAGEFAULT
    , .reason               (pagefault_reason)
    `endif
);





assign axi_awprot = {IS_INSTRUCTION_CACHE, s1_vm_privilege > `ARMLEOCPU_PRIVILEGE_SUPERVISOR, s1_vm_privilege > `ARMLEOCPU_PRIVILEGE_USER}; // Fixed, not modified anywhere
assign axi_arprot = axi_awprot;
assign axi_awlock = s1_cmd_atomic; // Fixed, not modified anywhere
assign axi_awaddr = {s1_address_cptag, s1_address_lane, s1_address_offset, s1_address_inword_offset};


assign axi_wdata = storegen_dataout; // Fixed, not modified anywhere
assign axi_wstrb = storegen_datamask; // Fixed, not modified anywhere
assign axi_wlast = 1; // Fixed to 1 for all values

// If moved to always, change to reg in top
assign s1_vm_privilege = ((s1_csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) && s1_csr_mstatus_mprv) ? s1_csr_mstatus_mpp : s1_csr_mcurrent_privilege;
assign s1_vm_enabled = (s1_vm_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR || s1_vm_privilege == `ARMLEOCPU_PRIVILEGE_USER) && s1_csr_satp_mode;


assign req_cmd_read = (req_cmd == `CACHE_CMD_EXECUTE) ||
                (req_cmd == `CACHE_CMD_LOAD) ||
                (req_cmd == `CACHE_CMD_LOAD_RESERVE);
    
assign req_cmd_write             = (req_cmd == `CACHE_CMD_STORE) ||
                              (req_cmd == `CACHE_CMD_STORE_CONDITIONAL);
assign req_cmd_access_request    = req_cmd_read || req_cmd_write;


assign s1_cmd_write            = (s1_cmd == `CACHE_CMD_STORE) ||
                              (s1_cmd == `CACHE_CMD_STORE_CONDITIONAL);
assign s1_cmd_flush = (s1_cmd == `CACHE_CMD_FLUSH_ALL);
assign s1_cmd_atomic           = (s1_cmd == `CACHE_CMD_LOAD_RESERVE) || (s1_cmd == `CACHE_CMD_STORE_CONDITIONAL);
assign s1_cmd_read             = (s1_cmd == `CACHE_CMD_LOAD) || (s1_cmd == `CACHE_CMD_EXECUTE) || (s1_cmd == `CACHE_CMD_LOAD_RESERVE);

// |------------------------------------------------|
// |         S1 data multiplexer                     |
// |------------------------------------------------|

generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : way_hit_comb
    assign s1_way_hit[way_num] = s1_valid_per_way[way_num] && ((cptag_readdata[way_num]) == s1_address_cptag);
end
endgenerate

always @* begin : output_stage_mux
    integer way_idx;
    `ifdef SIMULATION
    #1
    `endif
    s1_cache_hit = 1'b0;
    s1_readdata = storage_readdata[0];
    s1_cache_hit_way = {WAYS_W{1'b0}};
    for(way_idx = 0; way_idx < WAYS; way_idx = way_idx + 1) begin
        if(s1_way_hit[way_idx]) begin
            //verilator lint_off WIDTH
            s1_cache_hit_way = way_idx;
            //verilator lint_on WIDTH
            s1_readdata = storage_readdata[way_idx];
            s1_cache_hit = 1'b1;
        end
    end
end


task s1_respond(
    input s1_stall_in,
    input s1_done_in,
    input [3:0] s1_status_in,
    input [31:0] s1_loadgen_datain_in);

    s1_stall = s1_stall_in;
    s1_done = s1_done_in;
    s1_status = s1_status_in;
    s1_loadgen_datain = s1_loadgen_datain_in;
endtask

always @* begin
    // verilator lint_off WIDTH
    if(LANES_W != 6) begin
        s1_address_cptag = s1_vm_enabled ?
            {tlb_ptag_output, s1_address_cptag_low}
            : {2'b00, s1_address_vtag, s1_address_cptag_low};
    end else begin
        s1_address_cptag = s1_vm_enabled ?
            tlb_ptag_output 
            : {2'b00, s1_address_vtag};
    end
    // verilator lint_on WIDTH
end


integer i;
always @* begin : s1_comb
    `ifdef SIMULATION
        #1
    `endif

    req_ready = 0;

    s1_respond(1, 0, `CACHE_RESPONSE_SUCCESS, s1_readdata);
    // Selected depending if operation was cache hit or bus response
    // valid when s1_stall = 0; contains bus aligned read data

    axi_awvalid = 0;
    
    axi_wvalid = 0;

    axi_bready = 0;

    axi_araddr = {s1_address_cptag, s1_address_lane, s1_address_offset, s1_address_inword_offset};
    axi_arvalid = 0;
    axi_arlock = s1_cmd_atomic;
    
    axi_arlen = 0; // 0 or 16
    axi_arburst = `AXI_BURST_INCR; // INCR or WRAP

    axi_rready = 0;


    victim_way_nxt = victim_way;
    s1_ar_done_nxt = s1_ar_done;
    s1_refill_errored_nxt = s1_refill_errored;
    s1_first_response_done_nxt = s1_first_response_done;
    s1_aw_done_nxt = s1_aw_done;
    s1_w_done_nxt = s1_w_done;
    s1_tlb_write_done_nxt = s1_tlb_write_done;
    
    s1_active_nxt = s1_active;
    s1_address_vtag_nxt = s1_address_vtag;
    if(LANES_W != 6)
        s1_address_cptag_low_nxt = s1_address_cptag_low;

    s1_address_lane_nxt = s1_address_lane;
    s1_address_offset_nxt = s1_address_offset;
    s1_address_inword_offset_nxt = s1_address_inword_offset;

    s1_cmd_nxt = s1_cmd;
    s1_load_type_nxt = s1_load_type;
    s1_store_type_nxt = s1_store_type;

    s1_store_data_nxt = s1_store_data;


    s1_valid_per_way_nxt = s1_valid_per_way;
    
    if(LANES_W != 6)
        req_address_cptag_low = req_address[32-VIRT_TAG_W-1:INWORD_OFFSET_W+OFFSET_W+LANES_W];
    
    s1_restart = 0;

    if(s1_cmd_read) begin
        s1_unknowntype = s1_loadgen_unknowntype;
        s1_missaligned = s1_loadgen_missaligned;
    end else begin
        s1_unknowntype = storegen_unknowntype;
        s1_missaligned = storegen_missaligned;
    end
    // pagefault = pagefault generator's output
    tlb_cmd = `TLB_CMD_NONE;

    // way_hit, s1_cache_hit_way, s1_cache_hit, os_readdata
    // signals are assigned in always_comb above

    for(i = 0; i < WAYS; i = i + 1) begin
        cptag_read = 1'b0;
        cptag_lane = s1_address_lane; // For write this is s1_address_lane
        
        cptag_write[i] = 1'b0;
        cptag_writedata = s1_address_cptag;

        storage_read = 1'b0;
        storage_lane = s1_address_lane;
        storage_offset = s1_address_offset;
        
        storage_write[i] = 1'b0;
        storage_writedata = axi_rdata;
        storage_byteenable = 4'hF;
    end
    for(i = 0; i < WAYS; i = i + 1)
        valid_nxt[i] = valid[i];
    

    ptw_resolve_request = 1'b0;
    ptw_resolve_virtual_address = s1_address_vtag;
    

    tlb_vaddr_input = req_address_vtag;

    tlb_new_entry_metadata_input = ptw_resolve_metadata;
    tlb_new_entry_ptag_input = ptw_resolve_physical_address;
    


    s1_csr_satp_mode_nxt = s1_csr_satp_mode;
    s1_csr_satp_ppn_nxt = s1_csr_satp_ppn;
    s1_csr_mstatus_mprv_nxt = s1_csr_mstatus_mprv;
    s1_csr_mstatus_mxr_nxt = s1_csr_mstatus_mxr;
    s1_csr_mstatus_sum_nxt = s1_csr_mstatus_sum;
    s1_csr_mstatus_mpp_nxt = s1_csr_mstatus_mpp;
    s1_csr_mcurrent_privilege_nxt = s1_csr_mcurrent_privilege;
    

    
    if(!rst_n) begin
        s1_active_nxt = 0;
        victim_way_nxt = 0;
        for(i = 0; i < WAYS; i = i + 1)
            valid_nxt[i] = {LANES{1'b0}};
        // TLB Invalidate all
        tlb_cmd = `TLB_CMD_INVALIDATE_ALL;
        s1_first_response_done_nxt = 0;
        s1_status = `CACHE_RESPONSE_SUCCESS;
        
        s1_aw_done_nxt = 0;
        s1_ar_done_nxt = 0;
        s1_w_done_nxt = 0;
        s1_respond(1, 0, `CACHE_RESPONSE_SUCCESS, s1_readdata);
    end  else begin
        // cptag storage, read request
        //      cptag invalidated when write request comes in
        // data storage, read request
        // ptw, no resolve request
        // axi is controlled by logic below
        // loadgen = os_readdata
        // tlb, resolve request
        s1_respond(0, 0, `CACHE_RESPONSE_SUCCESS, s1_readdata);
        if(s1_active) begin
            if(s1_cmd_flush) begin
                // cptag storage: written
                // data storage: noop
                // ptw, noop
                // axi: noop
                // loadgen = does not matter
                // tlb, invalidate
                // returns response when done
                
                victim_way_nxt = 0;
                for(i = 0; i < WAYS; i = i + 1) begin
                    valid_nxt[i] = {LANES{1'b0}};
                end
                
                tlb_cmd = `TLB_CMD_INVALIDATE_ALL;
                
                // Stall, because TLB invalidate can't be interrupted
                // By a request because CMD line is shared
                s1_active_nxt = 0;
                s1_respond(1, 1, `CACHE_RESPONSE_SUCCESS, s1_readdata);
            end else if(s1_unknowntype) begin
                s1_active_nxt = 0;
                s1_respond(0, 1, `CACHE_RESPONSE_UNKNOWNTYPE, s1_readdata);
            end else if(s1_missaligned) begin
                s1_active_nxt = 0;
                s1_respond(0, 1, `CACHE_RESPONSE_MISSALIGNED, s1_readdata);
            end else if(s1_vm_enabled && !tlb_hit) begin
                // TODO: Make sure that no AXI4 transaction is issued while
                // Response is stalled
                // TLB Miss
                s1_active_nxt = s1_active;
                s1_respond(1, 0, `CACHE_RESPONSE_SUCCESS, s1_readdata);
                // Stall request, no response yet

                axi_arvalid = ptw_axi_arvalid;
                axi_araddr = ptw_axi_araddr;
                axi_arlen = 0;
                axi_arburst = `AXI_BURST_INCR;
                axi_arlock = 0;

                axi_rready = ptw_axi_rready;
                    
                // cptag storage: noop
                // data storage: noop
                // ptw, resolve request
                // axi is controlled by ptw,
                // axi const ports other ports controlled by logic below
                // loadgen = does not matter
                // tlb, written
                // next victim is elected, next victim capped to WAYS variable
                // returns response when errors
                ptw_resolve_request = 1;

                if(ptw_resolve_done) begin
                    if(ptw_accessfault || ptw_pagefault) begin
                        s1_active_nxt = 0;
                        s1_respond(0, 1, ptw_accessfault ?
                            `CACHE_RESPONSE_ACCESSFAULT : `CACHE_RESPONSE_PAGEFAULT, s1_readdata);
                    end else if(!s1_tlb_write_done) begin
                        tlb_cmd = `TLB_CMD_NEW_ENTRY;
                        tlb_vaddr_input = s1_address_vtag;
                        s1_tlb_write_done_nxt = 1;
                    end else if(s1_tlb_write_done) begin
                        s1_tlb_write_done_nxt = 0;
                        // Dont respond, instead restart S1 request
                        s1_respond(1, 0, `CACHE_RESPONSE_SUCCESS, s1_readdata);
                        s1_restart = 1;
                    end
                end
            end else if(s1_vm_enabled && s1_pagefault) begin
                s1_active_nxt = 0;
                s1_respond(0, 1, `CACHE_RESPONSE_PAGEFAULT, s1_readdata);
            end else if((!s1_vm_enabled) || (s1_vm_enabled && tlb_hit)) begin
                // If physical address or virtual and tlb is hit
                // For atomic operations or writes do AXI request
                // No magic value below:
                //      if 31th bit is reset then data is not cached
                //      31th bit (starting from 0) is value below, because cptag is top part of 34 bits of physical address
                if(!s1_address_cptag[CACHE_PHYS_TAG_W-1-2] ||
                    s1_cmd_write ||
                    s1_cmd_atomic) begin // Bypass case or write or atomic
                        s1_respond(1, 0, `CACHE_RESPONSE_SUCCESS, s1_readdata);
                        // TODO: Implement this
                        /// Make sure no AXI transaction is started before response is free

                        if(s1_cmd_write && s1_cache_hit && s1_address_cptag[CACHE_PHYS_TAG_W-1-2]) begin
                            valid_nxt[s1_cache_hit_way][s1_address_lane] = 0;
                            // See #50 Issue: For the future maybe instead of invalidating, just rewrite it?
                        end
                        if(s1_cmd_write) begin
                            // TODO: Send AXI transaction only if resp stage is not active
                            s1_respond(1, 0, `CACHE_RESPONSE_SUCCESS, s1_readdata);
                            // Note: AW and W ports need to start request at the same time
                            // Note: AW and W might be "ready" in different order
                            axi_awvalid = !s1_aw_done;
                            // axi_awaddr and other aw* values is set in logic at the start of always block
                            if(axi_awready) begin
                                s1_aw_done_nxt = 1;
                            end

                            // only axi port active
                            

                            axi_wvalid = !s1_w_done;
                            // axi_wdata = storegen_dataout; // This is fixed in assignments above
                            //axi_wlast = 1; This is set in logic at the start of always block
                            //axi_wstrb = storegen_datamask; This is set in logic at the start of always block
                            if(axi_wready) begin
                                s1_w_done_nxt = 1;
                            end

                            if(s1_w_done && s1_aw_done) begin
                                axi_bready = 1;
                                if(axi_bvalid) begin
                                    if(s1_cmd_atomic && axi_bresp == `AXI_RESP_EXOKAY) begin
                                        s1_respond(1, 1, `CACHE_RESPONSE_SUCCESS, s1_readdata);
                                    end else if(s1_cmd_atomic && axi_bresp == `AXI_RESP_OKAY) begin
                                        s1_respond(1, 1, `CACHE_RESPONSE_ATOMIC_FAIL, s1_readdata);
                                    end else if(!s1_cmd_atomic && axi_bresp == `AXI_RESP_OKAY) begin
                                        s1_respond(1, 1, `CACHE_RESPONSE_SUCCESS, s1_readdata);
                                    end else begin
                                        s1_respond(1, 1, `CACHE_RESPONSE_ACCESSFAULT, s1_readdata);
                                    end
                                    s1_w_done_nxt = 0;
                                    s1_aw_done_nxt = 0;
                                    
                                    s1_active_nxt = 0;
                                end
                            end
                        end else if(s1_cmd_read) begin
                            s1_respond(1, 0, `CACHE_RESPONSE_SUCCESS, s1_readdata);
                            // TODO: Send AXI transaction only if resp stage is not active
                            
                            // ATOMIC operation or cache bypassed access
                            // cptag storage: noop
                            // data storage: noop
                            // ptw, noop
                            // axi is controlled by code below,
                            // loadgen = axi_rdata
                            // tlb, output used
                            // returns response when errors
                            
                            

                            axi_arvalid = !s1_ar_done;
                            axi_arlen = 0;
                            axi_arburst = `AXI_BURST_INCR;
                            // axi_araddr is assigned above

                            if(axi_arready) begin
                                s1_ar_done_nxt = 1;
                            end
                            // TODO: Fix logic below
                            s1_loadgen_datain = axi_rdata;
                            if(s1_ar_done && axi_rvalid) begin
                                axi_rready = 1;
                                // TODO: RREADY should not be asserted until response
                                // is accepted by pipeline
                                if(s1_cmd_atomic && axi_rresp == `AXI_RESP_EXOKAY) begin
                                    s1_respond(1, 1, `CACHE_RESPONSE_SUCCESS, axi_rdata);
                                end else if(s1_cmd_atomic && axi_rresp == `AXI_RESP_OKAY) begin
                                    s1_respond(1, 1, `CACHE_RESPONSE_ATOMIC_FAIL, axi_rdata);
                                end else if(!s1_cmd_atomic && axi_rresp == `AXI_RESP_OKAY) begin
                                    s1_respond(1, 1, `CACHE_RESPONSE_SUCCESS, axi_rdata);
                                end else begin
                                    s1_respond(1, 1, `CACHE_RESPONSE_ACCESSFAULT, axi_rdata);
                                end
                                s1_active_nxt = 0;
                                s1_ar_done_nxt = 0;
                            end
                        end
                end else if(s1_cmd_read) begin // Not atomic, not bypassed
                    if(s1_cache_hit) begin
                        s1_active_nxt = 0;
                        s1_respond(0, 1, `CACHE_RESPONSE_SUCCESS, s1_readdata);
                        // TODO: If stalled response then 
                    end else begin
                        // TODO: Send AXI transaction only if resp stage is not active
                        
                        // Cache Miss
                        s1_respond(1, 0, `CACHE_RESPONSE_SUCCESS, s1_readdata);

                        axi_arlen = WORDS_IN_LANE-1;
                        axi_arburst = `AXI_BURST_WRAP;

                        // cptag storage: written
                        // data storage: written
                        // ptw, noop
                        // axi: read only, wrap burst
                        // loadgen = outputs data for first beat, because it's wrap request
                        // returns response
                        // No need to output error for other error types other than accessfault for rresp,
                        //      because that cases are covered by code in active state
                        //      transition to this means that this errors (unknown type, pagefault) are already covered
                        // tlb, output is used
                        // s1_address_offset is incremented and looped

                        axi_arvalid = !s1_ar_done;
                        if(axi_arready) begin
                            s1_ar_done_nxt = 1;
                        end

                        if(axi_rvalid) begin
                            if(s1_refill_errored) begin
                                // If error happened, fast forward, until last result
                                // And on last response return access fault
                                axi_rready = 1;
                                if(axi_rlast) begin
                                    s1_active_nxt = 0;
                                    s1_ar_done_nxt = 0;
                                    s1_refill_errored_nxt = 0;
                                    s1_first_response_done_nxt = 0;

                                    s1_respond(1, 1, `CACHE_RESPONSE_ACCESSFAULT, s1_readdata);
                                    
                                    
                                    valid_nxt[victim_way][s1_address_lane] = 0;
                                end
                            end else begin
                                axi_rready = 1;
                                
                                if(axi_rresp != `AXI_RESP_OKAY) begin
                                    s1_refill_errored_nxt = 1;
                                    // If last then no next cycle is possible
                                    // this case is impossible because all cached requests are 64 byte aligned
                                    
                                    // `ifdef DEBUG_CACHE
                                    // if(axi_rlast)
                                    //     $display("Error: !ERROR!: !BUG!: Error returned nopt on first cycle of burst");
                                    // `assert_equal(axi_rlast, 0)
                                    // `endif

                                    // `ifdef DEBUG_CACHE
                                    // if(s1_first_response_done)
                                    //     $display("Error: !ERROR!: !BUG!: Non OKAY AXI response after OKAY response");
                                    // `assert_equal(s1_first_response_done, 0)
                                    // `endif
                                    
                                end else begin
                                    // Response is valid and resp is OKAY

                                    // return first response for WRAP burst
                                    s1_first_response_done_nxt = 1;
                                    if(!s1_first_response_done) begin
                                        // TODO: Stall in both cases
                                        s1_respond(1, 1, `CACHE_RESPONSE_SUCCESS, s1_readdata);
                                        s1_loadgen_datain = axi_rdata;
                                    end

                                    // Write the cptag and state to values read from memory
                                    // s1_address_offset contains current write location
                                    // It does not matter what value is araddr (which depends on s1_address_offset), because AR request
                                    // is complete

                                    // After request is done s1_address_offset is invalid
                                    // But it does not matter because next request will overwrite
                                    // it anwyas

                                    cptag_lane = s1_address_lane;
                                    cptag_write[victim_way] = 1;
                                    cptag_writedata = s1_address_cptag;

                                    storage_lane = s1_address_lane;
                                    storage_offset = s1_address_offset;
                                    storage_write[victim_way] = 1;
                                    storage_writedata = axi_rdata;
                                    storage_byteenable = 4'hF;
                                    s1_address_offset_nxt = s1_address_offset + 1; // Note: 64 bit replace number
                                    
                                    
                                    if(axi_rlast) begin
                                        s1_active_nxt = 0;
                                        s1_ar_done_nxt = 0;
                                        s1_refill_errored_nxt = 0;
                                        s1_first_response_done_nxt = 0;
                                        valid_nxt[victim_way][s1_address_lane] = 1;
                                        // TODO: stall the req stage and no response
                                        // verilator lint_off WIDTH
                                        if(victim_way == WAYS - 1)
                                            victim_way_nxt = 0;
                                        else
                                            victim_way_nxt = victim_way + 1;
                                        // verilator lint_on WIDTH
                                    end
                                end
                            end
                            
                        end
                        
                    end
                end
            end // vm + tlb hit / no vm
        end // s1_active

        if((!s1_stall) || s1_restart) begin
            if((req_valid && (req_cmd != `CACHE_CMD_NONE)) || s1_restart) begin
                if((req_valid && (req_cmd != `CACHE_CMD_NONE)) && !s1_restart)
                    req_ready = 1;
                
                s1_csr_satp_mode_nxt = s1_restart ? s1_csr_satp_mode : req_csr_satp_mode_in;
                s1_csr_satp_ppn_nxt = s1_restart ? s1_csr_satp_ppn : req_csr_satp_ppn_in;
                s1_csr_mstatus_mprv_nxt = s1_restart ? s1_csr_mstatus_mprv : req_csr_mstatus_mprv_in;
                s1_csr_mstatus_mxr_nxt = s1_restart ? s1_csr_mstatus_mxr : req_csr_mstatus_mxr_in;
                s1_csr_mstatus_sum_nxt = s1_restart ? s1_csr_mstatus_sum : req_csr_mstatus_sum_in;
                s1_csr_mstatus_mpp_nxt = s1_restart ? s1_csr_mstatus_mpp : req_csr_mstatus_mpp_in;
                s1_csr_mcurrent_privilege_nxt = s1_restart ? s1_csr_mcurrent_privilege : req_csr_mcurrent_privilege_in;
                
                s1_active_nxt = 1;
                
                s1_address_vtag_nxt = s1_restart ? s1_address_vtag : req_address_vtag;
                if(LANES_W != 6)
                    s1_address_cptag_low_nxt = s1_restart ? s1_address_cptag_low : req_address_cptag_low;
                s1_address_lane_nxt = s1_restart ? s1_address_lane : req_address_lane;
                s1_address_offset_nxt = s1_restart ? s1_address_offset : req_address_offset;
                s1_address_inword_offset_nxt = s1_restart ? s1_address_inword_offset : req_address_inword_offset;

                s1_cmd_nxt = s1_restart ? s1_cmd : req_cmd;
                s1_load_type_nxt = s1_restart ? s1_load_type : req_load_type;
                s1_store_type_nxt = s1_restart ? s1_store_type : req_store_type;
                s1_store_data_nxt = s1_restart ? s1_store_data : req_store_data;

                
                for(i = 0; i < WAYS; i = i + 1) begin
                    s1_valid_per_way_nxt[i] = valid[i][s1_address_lane_nxt];
                end
                // Logic above has to make sure if stall = 0,
                //      no tlb operation is active
                tlb_cmd = `TLB_CMD_RESOLVE;
                tlb_vaddr_input = s1_address_vtag_nxt;
                
                
                storage_read = 1'b1;
                storage_lane = s1_address_lane_nxt;
                storage_offset = s1_address_offset_nxt;

                cptag_read = 1'b1;
                cptag_lane = s1_address_lane_nxt;

                s1_aw_done_nxt = 0;
                s1_ar_done_nxt = 0;
                s1_w_done_nxt = 0;
                s1_first_response_done_nxt = 0;
                s1_refill_errored_nxt = 0;
            end
        end
    end
end




always @* begin : resp_comb
    `ifdef SIMULATION
        #1
    `endif
    
    // Registers:
    resp_active_nxt = resp_active;
    resp_status_nxt = resp_status;
    resp_load_type_nxt = resp_load_type;
    resp_address_inword_offset_nxt = resp_address_inword_offset;
    resp_loadgen_datain_nxt = resp_loadgen_datain;

    // Combinational
    resp_stall = 1;
    resp_valid = resp_active;

    if(!resp_valid) begin
        resp_stall = 0;
    end else if(resp_valid && resp_ready) begin
        resp_stall = 0;
        resp_active_nxt = 0;
    end else begin
        // Resp is set but resp_ready is not set then
        resp_stall = 1;
    end

    if(s1_done && !resp_stall) begin
        resp_active_nxt = 1;
        resp_loadgen_datain_nxt = s1_loadgen_datain;
        resp_status_nxt = s1_status;
        resp_load_type_nxt = s1_load_type;
        resp_address_inword_offset_nxt = s1_address_inword_offset;
    end
end


endmodule

/*
module armleocpu_cache (
    input wire              clk,
    input wire              rst_n,

    //                      CACHE <-> EXECUTE/MEMORY
    // verilator lint_off UNOPTFLAT
    output reg              c_done,
    output reg   [3:0]      c_response, // CACHE_RESPONSE_*
    // verilator lint_on UNOPTFLAT

    input wire              c_force_bypass,
    input wire [3:0]        c_cmd, // CACHE_CMD_*
    input wire [31:0]       c_address,
    input wire [2:0]        c_load_type, // enum defined in armleocpu_defines.vh LOAD_*
    output wire [31:0]      c_load_data, // Also output for RMW sequence
    input wire [1:0]        c_store_type, // enum defined in armleocpu_defines.vh STORE_*
    input wire [31:0]       c_store_data, // Also input for RMW sequence

    //                      CACHE <-> CSR
    //                      SATP from RISC-V privileged spec registered on FLUSH_ALL or SFENCE_VMA
    input wire              csr_satp_mode_in, // Mode = 0 -> physical access,
                                           // 1 -> ppn valid
    input wire [21:0]       csr_satp_ppn_in,
    
    //                      Signals from RISC-V privileged spec
    input wire              csr_mstatus_mprv_in,
    input wire              csr_mstatus_mxr_in,
    input wire              csr_mstatus_sum_in,
    input wire [1:0]        csr_mstatus_mpp_in,

    // Current privilege level. Does not account for anything
    input wire [1:0]        csr_mcurrent_privilege_in,

    // AXI AW Bus
    output reg          io_axi_awvalid,
    input  wire         io_axi_awready,
    output wire [33:0]  io_axi_awaddr,
    output wire         io_axi_awlock,
    output wire [2:0]   io_axi_awprot,

    // AXI W Bus
    output reg          io_axi_wvalid,
    input  wire         io_axi_wready,
    output wire [31:0]  io_axi_wdata,
    output wire [3:0]   io_axi_wstrb,
    output wire         io_axi_wlast,
    
    // AXI B Bus
    input  wire         io_axi_bvalid,
    output reg          io_axi_bready,
    input  wire [1:0]   io_axi_bresp,
    
    output reg          io_axi_arvalid,
    input  wire         io_axi_arready,
    output reg  [33:0]  io_axi_araddr,
    output reg  [7:0]   io_axi_arlen,
    output reg  [1:0]   io_axi_arburst,
    output reg          io_axi_arlock,
    output wire [2:0]   io_axi_arprot,
    

    input wire          io_axi_rvalid,
    output reg          io_axi_rready,
    input wire  [1:0]   io_axi_rresp,
    input wire          io_axi_rlast,
    input wire  [31:0]  io_axi_rdata
);


// |------------------------------------------------|
// |                                                |
// |              Parameters and includes           |
// |                                                |
// |------------------------------------------------|

parameter WAYS = 2; // 2..16
localparam WAYS_W = $clog2(WAYS);

parameter TLB_ENTRIES_W = 2; // 1..16
parameter TLB_WAYS = 2; // 1..16
localparam TLB_WAYS_W = $clog2(TLB_WAYS);

parameter LANES_W = 1; // 1..6 range.
localparam LANES = 2**LANES_W;

parameter [0:0] IS_INSTRUCTION_CACHE = 0;

parameter AXI_PASSTHROUGH = 0;

// 4 = 16 words each 32 bit = 64 byte
localparam OFFSET_W = 4;
localparam INWORD_OFFSET_W = 2;
localparam WORDS_IN_LANE = 2**OFFSET_W;

localparam CACHE_PHYS_TAG_W = 34 - (LANES_W + OFFSET_W + INWORD_OFFSET_W);
localparam TLB_PHYS_TAG_W = 34 - (6 + OFFSET_W + INWORD_OFFSET_W);
// 34 = size of address
// tag = 34 to 
localparam VIRT_TAG_W = 20;


// AXI Register Slice

// AXI AW Bus
logic         axi_awvalid;
logic         axi_awready;
logic [33:0]  axi_awaddr;
logic         axi_awlock;
logic [2:0]   axi_awprot;

// AXI W Bus
logic         axi_wvalid;
logic         axi_wready;
logic [31:0]  axi_wdata;
logic [3:0]   axi_wstrb;
logic         axi_wlast;

// AXI B Bus
logic         axi_bvalid;
logic         axi_bready;
logic [1:0]   axi_bresp;

logic         axi_arvalid;
logic         axi_arready;
logic [33:0]  axi_araddr;
logic [7:0]   axi_arlen;
logic [1:0]   axi_arburst;
logic         axi_arlock;
logic [2:0]   axi_arprot;


logic         axi_rvalid;
logic         axi_rready;
logic [1:0]   axi_rresp;
logic         axi_rlast;
logic [31:0]  axi_rdata;


// AW Bus
armleocpu_register_slice #(
    .DW(32 + 1+ 3),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_aw (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (axi_awvalid),
    .in_ready   (axi_awready),
    .in_data({
        axi_awaddr,
        axi_awlock,
        axi_awprot
    }),

    .out_valid(io_axi_awvalid),
    .out_ready(io_axi_awready),
    .out_data({
        io_axi_awaddr,
        io_axi_awlock,
        io_axi_awprot
    })
);

// W Bus
armleocpu_register_slice #(
    .DW(32 + 4 + 1),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_w(
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (axi_wvalid),
    .in_ready   (axi_wready),
    .in_data    ({
        axi_wdata,
        axi_wstrb,
        axi_wlast
    }),

    .out_valid  (io_axi_wvalid),
    .out_ready  (io_axi_wready),
    .out_data   ({
        io_axi_wdata,
        io_axi_wstrb,
        io_axi_wlast
    })
);

// B Bus
armleocpu_register_slice #(
    .DW(2),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_b(
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (io_axi_bvalid),
    .in_ready   (io_axi_bready),
    .in_data    ({
        io_axi_bresp
    }),

    .out_valid  (axi_bvalid),
    .out_ready  (axi_bready),
    .out_data   ({
        axi_bresp
    })
);

// AR Bus
armleocpu_register_slice #(
    .DW(34 + 2 + 8 + 1 + 3),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_ar (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (axi_arvalid),
    .in_ready   (axi_arready),
    .in_data({
        axi_araddr,
        axi_arburst,
        axi_arlen,
        axi_arlock,
        axi_arprot
    }),

    .out_valid(io_axi_arvalid),
    .out_ready(io_axi_arready),
    .out_data({
        io_axi_araddr,
        io_axi_arburst,
        io_axi_arlen,
        io_axi_arlock,
        io_axi_arprot
    })
);

// R Bus
armleocpu_register_slice #(
    .DW(2 + 1 + 32),
    .PASSTHROUGH(AXI_PASSTHROUGH)
) U_r (
    .clk(clk),
    .rst_n(rst_n),

    .in_valid   (io_axi_rvalid),
    .in_ready   (io_axi_rready),
    .in_data    ({
        io_axi_rresp,
        io_axi_rdata,
        io_axi_rlast
    }),

    .out_valid  (axi_rvalid),
    .out_ready  (axi_rready),
    .out_data   ({
        axi_rresp,
        axi_rdata,
        axi_rlast
    })
);


// |------------------------------------------------|
// |                                                |
// |              Cache State                       |
// |                                                |
// |------------------------------------------------|
`DEFINE_REG_REG_NXT(WAYS_W, victim_way, victim_way_nxt, clk)
`DEFINE_REG_REG_NXT(1, s1_ar_done, s1_ar_done_nxt, clk)
`DEFINE_REG_REG_NXT(1, s1_refill_errored, s1_refill_errored_nxt, clk)
`DEFINE_REG_REG_NXT(1, s1_first_response_done, s1_first_response_done_nxt, clk)
`DEFINE_REG_REG_NXT(1, s1_aw_done, s1_aw_done_nxt, clk)
`DEFINE_REG_REG_NXT(1, s1_w_done, s1_w_done_nxt, clk)

reg [21:0] csr_satp_ppn;
reg csr_satp_mode;
reg csr_mstatus_mprv;
reg csr_mstatus_mxr;
reg csr_mstatus_sum;

reg [1:0] csr_mstatus_mpp;
reg [1:0] csr_mcurrent_privilege;


// |------------------------------------------------|
// |                                                |
// |              Output stage                      |
// |                                                |
// |------------------------------------------------|
`DEFINE_REG_REG_NXT(1, s1_active, s1_active_nxt, clk)

`DEFINE_REG_REG_NXT(VIRT_TAG_W, s1_address_vtag, s1_address_vtag_nxt, clk)

`DEFINE_REG_REG_NXT(((LANES_W != 6) ? (CACHE_PHYS_TAG_W - TLB_PHYS_TAG_W) : 1), s1_address_cptag_low, s1_address_cptag_low_nxt, clk)

`DEFINE_REG_REG_NXT(LANES_W, s1_address_lane, s1_address_lane_nxt, clk)
`DEFINE_REG_REG_NXT(OFFSET_W, s1_address_offset, s1_address_offset_nxt, clk)
`DEFINE_REG_REG_NXT(2, s1_address_inword_offset, s1_address_inword_offset_nxt, clk)

`DEFINE_REG_REG_NXT(4, s1_cmd, s1_cmd_nxt, clk)
`DEFINE_REG_REG_NXT(3, s1_load_type, s1_load_type_nxt, clk)
`DEFINE_REG_REG_NXT(2, s1_store_type, s1_store_type_nxt, clk)

`DEFINE_REG_REG_NXT(32, s1_store_data, s1_store_data_nxt, clk)


`DEFINE_REG_REG_NXT(WAYS, s1_valid_per_way, s1_valid_per_way_nxt, clk)

// |------------------------------------------------|
// |                                                |
// |              Signals                           |
// |                                                |
// |------------------------------------------------|

reg csr_inputs_register;

wire [1:0] vm_privilege;
wire vm_enabled;

wire c_cmd_access_request, c_cmd_write, c_cmd_read;
wire s1_cmd_atomic, s1_cmd_flush, s1_cmd_write, s1_cmd_read;

wire [VIRT_TAG_W-1:0] 	    c_address_vtag          = c_address[31:32-VIRT_TAG_W];
reg  [((LANES_W != 6) ? (6-LANES_W-1) : 0) :0]	    c_address_cptag_low;
wire [LANES_W-1:0]	        c_address_lane          = c_address[INWORD_OFFSET_W+OFFSET_W+LANES_W-1:INWORD_OFFSET_W+OFFSET_W];
wire [OFFSET_W-1:0]			c_address_offset        = c_address[INWORD_OFFSET_W+OFFSET_W-1:INWORD_OFFSET_W];
wire [1:0]			        c_address_inword_offset = c_address[INWORD_OFFSET_W-1:0];

reg                         stall; // Output stage stalls input stage
reg                         unknowntype;
reg                         missaligned;
wire                        pagefault;


reg  [CACHE_PHYS_TAG_W-1:0] s1_address_cptag;
// Full tag including physical tag and cache tag low part

wire  [WAYS-1:0]             way_hit;
reg  [WAYS_W-1:0]           os_cache_hit_way;
reg                         os_cache_hit;
reg  [31:0]                 os_readdata;



// CPTAG port
reg                     cptag_read;
reg  [LANES_W-1:0]      cptag_lane;
wire [CACHE_PHYS_TAG_W-1:0]
                        cptag_readdata       [WAYS-1:0];
reg                     cptag_write          [WAYS-1:0];
reg  [CACHE_PHYS_TAG_W-1:0]
                        cptag_writedata;

//                      Storage read/write port vars
reg  [LANES_W-1:0]      storage_lane;
reg  [OFFSET_W-1:0]     storage_offset;
reg                     storage_read;
wire [31:0]             storage_readdata    [WAYS-1:0];
reg  [WAYS-1:0]         storage_write;
reg  [3:0]              storage_byteenable;
reg  [31:0]             storage_writedata;


reg [LANES-1:0] valid [WAYS-1:0];
reg [LANES-1:0] valid_nxt [WAYS-1:0];


genvar way_num;

generate for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : valid_per_way_register
    always @(posedge clk) begin
        valid[way_num] <= valid_nxt[way_num];
    end
end
endgenerate


always @(posedge clk) begin
    if(csr_inputs_register) begin
        csr_satp_mode <= csr_satp_mode_in;
        csr_satp_ppn <= csr_satp_ppn_in;
        csr_mstatus_mprv <= csr_mstatus_mprv_in;
        csr_mstatus_mxr <= csr_mstatus_mxr_in;
        csr_mstatus_sum <= csr_mstatus_sum_in;
        csr_mstatus_mpp <= csr_mstatus_mpp_in;
        csr_mcurrent_privilege <= csr_mcurrent_privilege_in;
    end
end


genvar byte_offset;
generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : mem_generate_for
    armleocpu_mem_1rw #(
        .ELEMENTS_W(LANES_W),
        .WIDTH(CACHE_PHYS_TAG_W)
    ) ptag_storage (
        .clk(clk),
        .address(cptag_lane),

        .read(cptag_read),
        .readdata(cptag_readdata[way_num]),
        
        .write(cptag_write[way_num]),
        .writedata(cptag_writedata)
    );
    armleocpu_mem_1rwm #(
        .ELEMENTS_W(LANES_W+OFFSET_W),
        .WIDTH(32),
        .GRANULITY(8)
    ) datastorage (
        .clk(clk),
        .address({storage_lane, storage_offset}),
        
        .read(storage_read),
        .readdata(storage_readdata[way_num]),

        .writeenable(storage_byteenable),
        .write(storage_write[way_num]),
        .writedata(storage_writedata)
    );
    
end
endgenerate




// |------------------------------------------------|
// |                   LoadGen                      |
// |------------------------------------------------|

reg [31:0]              loadgen_datain;
wire                    loadgen_missaligned;
wire                    loadgen_unknowntype;

armleocpu_loadgen loadgen(
    .inword_offset          (s1_address_inword_offset),
    .loadgen_type           (s1_load_type),

    .loadgen_datain         (loadgen_datain),

    .loadgen_dataout        (c_load_data),
    .loadgen_missaligned    (loadgen_missaligned),
    .loadgen_unknowntype    (loadgen_unknowntype)
);

// |------------------------------------------------|
// |                 StoreGen                       |
// |------------------------------------------------|

wire [31:0]             storegen_dataout;
wire [3:0]              storegen_datamask;
wire                    storegen_missaligned;
wire                    storegen_unknowntype;
armleocpu_storegen storegen(
    .inword_offset          (s1_address_inword_offset),
    .storegen_type          (s1_store_type),

    .storegen_datain        (s1_store_data),

    .storegen_dataout       (storegen_dataout),
    .storegen_datamask      (storegen_datamask),
    .storegen_missaligned   (storegen_missaligned),
    .storegen_unknowntype   (storegen_unknowntype)
);



// PTW request signals
reg                     ptw_resolve_request;
reg  [19:0]             ptw_resolve_virtual_address;
// PTW result signals
wire                    ptw_resolve_done;
wire                    ptw_pagefault;
wire                    ptw_accessfault;


`ifdef DEBUG_PAGEFAULT
wire [30*8-1:0] pagefault_reason;
`endif

wire [7:0]              ptw_resolve_metadata;
wire [TLB_PHYS_TAG_W-1:0]       ptw_resolve_physical_address;
// PTW AXI4 Signals

wire ptw_axi_arvalid,
    ptw_axi_rready;

wire [33:0] ptw_axi_araddr;

// Page table walker instance
armleocpu_ptw ptw(
    .clk                        (clk),
    .rst_n                      (rst_n),

    .axi_arvalid                (ptw_axi_arvalid),
    .axi_arready                (axi_arready),
    .axi_araddr                 (ptw_axi_araddr),

    .axi_rvalid                 (axi_rvalid),
    .axi_rready                 (ptw_axi_rready),
    .axi_rresp                  (axi_rresp),
    .axi_rlast                  (axi_rlast),
    .axi_rdata                  (axi_rdata),
    
    .resolve_request            (ptw_resolve_request),
    .virtual_address            (ptw_resolve_virtual_address), //s1_address_vtag

    .resolve_done               (ptw_resolve_done),
    .resolve_pagefault          (ptw_pagefault),
    .resolve_accessfault        (ptw_accessfault),

    .resolve_metadata           (ptw_resolve_metadata),
    .resolve_physical_address   (ptw_resolve_physical_address),

    .satp_ppn                   (csr_satp_ppn)
);


reg  [1:0]              tlb_cmd;

// read port
reg  [19:0]                 tlb_vaddr_input;
// For first cycle it's c_address_vtag, for writes it's s1_address_vtag
wire                        tlb_hit;
wire [7:0]                  tlb_metadata_output;
wire [TLB_PHYS_TAG_W-1:0]   tlb_ptag_output;

// write port
reg  [7:0]                  tlb_new_entry_metadata_input;
reg  [TLB_PHYS_TAG_W-1:0]   tlb_new_entry_ptag_input;

wire [TLB_WAYS_W-1:0]       tlb_resolve_way; // Ignored

armleocpu_tlb #(TLB_ENTRIES_W, TLB_WAYS) tlb(
    .rst_n                  (rst_n),
    .clk                    (clk),
    
    .cmd                    (tlb_cmd),

    // read port
    .vaddr_input            (tlb_vaddr_input),
    .hit                    (tlb_hit),
    .resolve_metadata_output(tlb_metadata_output),
    .resolve_ptag_output    (tlb_ptag_output),
    .resolve_way            (tlb_resolve_way),
    
    .new_entry_metadata_input(tlb_new_entry_metadata_input),
    .new_entry_ptag_input    (tlb_new_entry_ptag_input)
);


armleocpu_cache_pagefault pagefault_generator(
    .csr_satp_mode_r         (csr_satp_mode),

    .csr_mcurrent_privilege  (csr_mcurrent_privilege),
    .csr_mstatus_mprv        (csr_mstatus_mprv),
    .csr_mstatus_mxr         (csr_mstatus_mxr),
    .csr_mstatus_sum         (csr_mstatus_sum),
    .csr_mstatus_mpp         (csr_mstatus_mpp),

    .s1_cmd                  (s1_cmd),
    .tlb_read_metadata       (tlb_metadata_output),

    .pagefault                  (pagefault)
    `ifdef DEBUG_PAGEFAULT
    , .reason(pagefault_reason)
    `endif
);



// |------------------------------------------------|
// |         Output stage data multiplexer          |
// |------------------------------------------------|



generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : way_hit_comb
    assign way_hit[way_num] = s1_valid_per_way[way_num] && ((cptag_readdata[way_num]) == s1_address_cptag);
end
endgenerate

always @* begin : output_stage_mux
    integer way_idx;
    `ifdef SIMULATION
    #1
    `endif
    os_cache_hit = 1'b0;
    os_readdata = storage_readdata[0];
    os_cache_hit_way = {WAYS_W{1'b0}};
    for(way_idx = 0; way_idx < WAYS; way_idx = way_idx + 1) begin
        if(way_hit[way_idx]) begin
            //verilator lint_off WIDTH
            os_cache_hit_way = way_idx;
            //verilator lint_on WIDTH
            os_readdata = storage_readdata[way_idx];
            os_cache_hit = 1'b1;
        end
    end
end

assign axi_awprot = {IS_INSTRUCTION_CACHE, vm_privilege > `ARMLEOCPU_PRIVILEGE_SUPERVISOR, vm_privilege > `ARMLEOCPU_PRIVILEGE_USER}; // Fixed, not modified anywhere
assign axi_arprot = axi_awprot;
assign axi_awlock = s1_cmd_atomic; // Fixed, not modified anywhere
assign axi_awaddr = {s1_address_cptag, s1_address_lane, s1_address_offset, s1_address_inword_offset};


assign axi_wdata = storegen_dataout; // Fixed, not modified anywhere
assign axi_wstrb = storegen_datamask; // Fixed, not modified anywhere
assign axi_wlast = 1; // Fixed to 1 for all values

// If moved to always, change to reg in top
assign vm_privilege = ((csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) && csr_mstatus_mprv) ? csr_mstatus_mpp : csr_mcurrent_privilege;
assign vm_enabled = (vm_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR || vm_privilege == `ARMLEOCPU_PRIVILEGE_USER) && csr_satp_mode;


assign c_cmd_read = (c_cmd == `CACHE_CMD_EXECUTE) ||
                (c_cmd == `CACHE_CMD_LOAD) ||
                (c_cmd == `CACHE_CMD_LOAD_RESERVE);
    
assign c_cmd_write             = (c_cmd == `CACHE_CMD_STORE) ||
                              (c_cmd == `CACHE_CMD_STORE_CONDITIONAL);
assign c_cmd_access_request    = c_cmd_read || c_cmd_write;


assign s1_cmd_write            = (s1_cmd == `CACHE_CMD_STORE) ||
                              (s1_cmd == `CACHE_CMD_STORE_CONDITIONAL);
assign s1_cmd_flush = (s1_cmd == `CACHE_CMD_FLUSH_ALL);
assign s1_cmd_atomic           = (s1_cmd == `CACHE_CMD_LOAD_RESERVE) || (s1_cmd == `CACHE_CMD_STORE_CONDITIONAL);
assign s1_cmd_read             = (s1_cmd == `CACHE_CMD_LOAD) || (s1_cmd == `CACHE_CMD_EXECUTE) || (s1_cmd == `CACHE_CMD_LOAD_RESERVE);


always @* begin : cache_comb

    integer i;
`ifdef SIMULATION
    #1
`endif


    if(LANES_W != 6) begin
        s1_address_cptag = vm_enabled ?
            {tlb_ptag_output, s1_address_cptag_low}
            : {2'b00, s1_address_vtag, s1_address_cptag_low};
    end else begin
        s1_address_cptag = vm_enabled ?
            tlb_ptag_output 
            : {2'b00, s1_address_vtag};
    end


    // Core output
    c_done = 0;
    c_response = `CACHE_RESPONSE_SUCCESS;
    // c_load_data = loadgen_dataout

    axi_awvalid = 0;
    
    axi_wvalid = 0;

    axi_bready = 0;

    axi_araddr = {s1_address_cptag, s1_address_lane, s1_address_offset, s1_address_inword_offset};
    axi_arvalid = 0;
    axi_arlock = s1_cmd_atomic;
    
    axi_arlen = 0; // 0 or 16
    axi_arburst = `AXI_BURST_INCR; // INCR or WRAP

    axi_rready = 0;

    victim_way_nxt = victim_way;
    s1_ar_done_nxt = s1_ar_done;
    s1_refill_errored_nxt = s1_refill_errored;
    s1_first_response_done_nxt = s1_first_response_done;
    s1_aw_done_nxt = s1_aw_done;
    s1_w_done_nxt = s1_w_done;
    
    csr_inputs_register = 0;


    s1_active_nxt = s1_active;
    s1_address_vtag_nxt = s1_address_vtag;
    if(LANES_W != 6)
        s1_address_cptag_low_nxt = s1_address_cptag_low;

    s1_address_lane_nxt = s1_address_lane;
    s1_address_offset_nxt = s1_address_offset;
    s1_address_inword_offset_nxt = s1_address_inword_offset;

    s1_cmd_nxt = s1_cmd;
    s1_load_type_nxt = s1_load_type;
    s1_store_type_nxt = s1_store_type;

    s1_store_data_nxt = s1_store_data;


    s1_valid_per_way_nxt = s1_valid_per_way;
    
    if(LANES_W != 6)
        c_address_cptag_low = c_address[32-VIRT_TAG_W-1:INWORD_OFFSET_W+OFFSET_W+LANES_W];
    

    stall = 1;
    if(s1_cmd_read) begin
        unknowntype = loadgen_unknowntype;
        missaligned = loadgen_missaligned;
    end else begin
        unknowntype = storegen_unknowntype;
        missaligned = storegen_missaligned;
    end
    // pagefault = pagefault generator's output
    tlb_cmd = `TLB_CMD_NONE;


    // way_hit, os_cache_hit_way, os_cache_hit, os_readdata
    // signals are assigned in always_comb above


    for(i = 0; i < WAYS; i = i + 1) begin
        cptag_read = 1'b0;
        cptag_lane = c_address_lane; // For write this is s1_address_lane
        
        cptag_write[i] = 1'b0;
        cptag_writedata = s1_address_cptag;

        storage_read = 1'b0;
        storage_lane = c_address_lane;
        storage_offset = c_address_offset;
        
        storage_write[i] = 1'b0;
        storage_writedata = axi_rdata;
        storage_byteenable = 4'hF;
    end
    for(i = 0; i < WAYS; i = i + 1)
        valid_nxt[i] = valid[i];
    

    ptw_resolve_request = 1'b0;
    ptw_resolve_virtual_address = s1_address_vtag;
    
    loadgen_datain = os_readdata; // For bypassed read this is registered axi_rdata
    
    tlb_vaddr_input = c_address_vtag;

    tlb_new_entry_metadata_input = ptw_resolve_metadata;
    tlb_new_entry_ptag_input = ptw_resolve_physical_address;
    

    
    if(!rst_n) begin
        s1_active_nxt = 0;
        victim_way_nxt = 0;
        for(i = 0; i < WAYS; i = i + 1)
            valid_nxt[i] = {LANES{1'b0}};
        // TLB Invalidate all
        tlb_cmd = `TLB_CMD_INVALIDATE_ALL;
        s1_first_response_done_nxt = 0;
        c_response = `CACHE_RESPONSE_SUCCESS;
        
        s1_aw_done_nxt = 0;
        s1_ar_done_nxt = 0;
        s1_w_done_nxt = 0;
        stall = 1;
    end else begin
        // cptag storage, read request
        //      cptag invalidated when write request comes in
        // data storage, read request
        // ptw, no resolve request
        // axi is controlled by logic below
        // loadgen = os_readdata
        // tlb, resolve request
        stall = 0;
        if(s1_active) begin
            if(s1_cmd_flush) begin
                // cptag storage: written
                // data storage: noop
                // ptw, noop
                // axi: noop
                // loadgen = does not matter
                // tlb, invalidate
                // returns response when done
                s1_active_nxt = 0;
                victim_way_nxt = 0;
                for(i = 0; i < WAYS; i = i + 1) begin
                    valid_nxt[i] = {LANES{1'b0}};
                end
                
                tlb_cmd = `TLB_CMD_INVALIDATE_ALL;
                

                
                stall = 1; // Stall till next cycle, because TLB is busy
                c_response = `CACHE_RESPONSE_SUCCESS;
                c_done = 1;
            end else if(unknowntype) begin
                c_done = 1;
                c_response = `CACHE_RESPONSE_UNKNOWNTYPE;
                s1_active_nxt = 0;
            end else if(missaligned) begin
                c_done = 1;
                c_response = `CACHE_RESPONSE_MISSALIGNED;
                s1_active_nxt = 0;
            end else if(vm_enabled && !tlb_hit) begin
                // TLB Miss
                stall = 1;

                axi_arvalid = ptw_axi_arvalid;
                axi_araddr = ptw_axi_araddr;
                axi_arlen = 0;
                axi_arburst = `AXI_BURST_INCR;
                axi_arlock = 0;

                axi_rready = ptw_axi_rready;
                    
                // cptag storage: noop
                // data storage: noop
                // ptw, resolve request
                // axi is controlled by ptw,
                // axi const ports other ports controlled by logic below
                // loadgen = does not matter
                // tlb, written
                // next victim is elected, next victim capped to WAYS variable
                // returns response when errors
                ptw_resolve_request = 1;

                if(ptw_resolve_done) begin
                    if(ptw_accessfault || ptw_pagefault) begin
                        s1_active_nxt = 0;
                        c_done = 1;
                        c_response = ptw_accessfault ?
                            `CACHE_RESPONSE_ACCESSFAULT : `CACHE_RESPONSE_PAGEFAULT;
                    end else begin
                        tlb_cmd = `TLB_CMD_NEW_ENTRY;
                        tlb_vaddr_input = s1_address_vtag;
                        s1_active_nxt = 0;
                        stall = 1;
                    end
                end
            end else if(vm_enabled && pagefault) begin
                c_done = 1;
                c_response = `CACHE_RESPONSE_PAGEFAULT;
                s1_active_nxt = 0;
            end else if((!vm_enabled) || (vm_enabled && tlb_hit)) begin
                // If physical address or virtual and tlb is hit
                // For atomic operations or writes do AXI request
                // No magic value below:
                //      if 31th bit is reset then data is not cached
                //      31th bit (starting from 0) is value below, because cptag is top part of 34 bits of physical address
                if(!s1_address_cptag[CACHE_PHYS_TAG_W-1-2] ||
                    s1_cmd_write ||
                    s1_cmd_atomic || c_force_bypass) begin // Bypass case or write or atomic
                        stall = 1;
                        if(s1_cmd_write && os_cache_hit && s1_address_cptag[CACHE_PHYS_TAG_W-1-2]) begin
                            valid_nxt[os_cache_hit_way][s1_address_lane] = 0;
                            // See #50 Issue: For the future maybe instead of invalidating, just rewrite it?
                        end
                        if(s1_cmd_write) begin
                            stall = 1;
                            // Note: AW and W ports need to start request at the same time
                            // Note: AW and W might be "ready" in different order
                            axi_awvalid = !s1_aw_done;
                            // axi_awaddr and other aw* values is set in logic at the start of always block
                            if(axi_awready) begin
                                s1_aw_done_nxt = 1;
                            end

                            // only axi port active
                            

                            axi_wvalid = !s1_w_done;
                            // axi_wdata = storegen_dataout; // This is fixed in assignments above
                            //axi_wlast = 1; This is set in logic at the start of always block
                            //axi_wstrb = storegen_datamask; This is set in logic at the start of always block
                            if(axi_wready) begin
                                s1_w_done_nxt = 1;
                            end

                            if(s1_w_done && s1_aw_done) begin
                                axi_bready = 1;
                                if(axi_bvalid) begin
                                    c_done = 1;
                                    if(s1_cmd_atomic && axi_bresp == `AXI_RESP_EXOKAY) begin
                                        c_response = `CACHE_RESPONSE_SUCCESS;
                                    end else if(s1_cmd_atomic && axi_bresp == `AXI_RESP_OKAY) begin
                                        c_response = `CACHE_RESPONSE_ATOMIC_FAIL;
                                    end else if(!s1_cmd_atomic && axi_bresp == `AXI_RESP_OKAY) begin
                                        c_response = `CACHE_RESPONSE_SUCCESS;
                                    end else begin
                                        c_response = `CACHE_RESPONSE_ACCESSFAULT;
                                    end
                                    s1_w_done_nxt = 0;
                                    s1_aw_done_nxt = 0;
                                    s1_active_nxt = 0;
                                    stall = 1;
                                end
                            end
                        end else if(s1_cmd_read || c_force_bypass) begin
                            stall = 1;
                            // ATOMIC operation or cache bypassed access
                            // cptag storage: noop
                            // data storage: noop
                            // ptw, noop
                            // axi is controlled by code below,
                            // loadgen = axi_rdata
                            // tlb, output used
                            // returns response when errors
                            
                            

                            axi_arvalid = !s1_ar_done;
                            axi_arlen = 0;
                            axi_arburst = `AXI_BURST_INCR;
                            // axi_araddr is assigned above

                            if(axi_arready) begin
                                s1_ar_done_nxt = 1;
                            end
                            loadgen_datain = axi_rdata;
                            if(s1_ar_done && axi_rvalid) begin
                                axi_rready = 1;
                                if(s1_cmd_atomic && axi_rresp == `AXI_RESP_EXOKAY) begin
                                    c_response = `CACHE_RESPONSE_SUCCESS;
                                    c_done = 1;
                                end else if(s1_cmd_atomic && axi_rresp == `AXI_RESP_OKAY) begin
                                    c_response = `CACHE_RESPONSE_ATOMIC_FAIL;
                                    c_done = 1;
                                end else if(!s1_cmd_atomic && axi_rresp == `AXI_RESP_OKAY) begin
                                    c_response = `CACHE_RESPONSE_SUCCESS;
                                    c_done = 1;
                                end else begin
                                    c_response = `CACHE_RESPONSE_ACCESSFAULT;
                                    c_done = 1;
                                end
                                s1_active_nxt = 0;
                                s1_ar_done_nxt = 0;
                                stall = 1;
                            end
                        end
                end else if(s1_cmd_read) begin // Not atomic, not bypassed
                    if(os_cache_hit) begin
                        s1_active_nxt = 0;
                        c_response = `CACHE_RESPONSE_SUCCESS;
                        c_done = 1;
                        loadgen_datain = os_readdata;
                        stall = 0;
                    end else begin
                        // Cache Miss
                        stall = 1;

                        axi_arlen = WORDS_IN_LANE-1;
                        axi_arburst = `AXI_BURST_WRAP;

                        // cptag storage: written
                        // data storage: written
                        // ptw, noop
                        // axi: read only, wrap burst
                        // loadgen = outputs data for first beat, because it's wrap request
                        // returns response
                        // No need to output error for other error types other than accessfault for rresp,
                        //      because that cases are covered by code in active state
                        //      transition to this means that this errors (unknown type, pagefault) are already covered
                        // tlb, output is used
                        // s1_address_offset is incremented and looped

                        axi_arvalid = !s1_ar_done;
                        if(axi_arready) begin
                            s1_ar_done_nxt = 1;
                        end

                        if(axi_rvalid) begin
                            if(s1_refill_errored) begin
                                // If error happened, fast forward, until last result
                                // And on last response return access fault
                                axi_rready = 1;
                                if(axi_rlast) begin
                                    s1_active_nxt = 0;
                                    s1_ar_done_nxt = 0;
                                    s1_refill_errored_nxt = 0;
                                    s1_first_response_done_nxt = 0;

                                    c_done = 1;
                                    c_response = `CACHE_RESPONSE_ACCESSFAULT;
                                    
                                    
                                    valid_nxt[victim_way][s1_address_lane] = 0;
                                end
                            end else begin
                                axi_rready = 1;
                                
                                if(axi_rresp != `AXI_RESP_OKAY) begin
                                    s1_refill_errored_nxt = 1;
                                    // If last then no next cycle is possible
                                    // this case is impossible because all cached requests are 64 byte aligned
                                    
                                    // `ifdef DEBUG_CACHE
                                    // if(axi_rlast)
                                    //     $display("Error: !ERROR!: !BUG!: Error returned nopt on first cycle of burst");
                                    // `assert_equal(axi_rlast, 0)
                                    // `endif

                                    // `ifdef DEBUG_CACHE
                                    // if(s1_first_response_done)
                                    //     $display("Error: !ERROR!: !BUG!: Non OKAY AXI response after OKAY response");
                                    // `assert_equal(s1_first_response_done, 0)
                                    // `endif
                                    
                                end else begin
                                    // Response is valid and resp is OKAY

                                    // return first response for WRAP burst
                                    s1_first_response_done_nxt = 1;
                                    if(!s1_first_response_done) begin
                                        c_done = 1;
                                        c_response = `CACHE_RESPONSE_SUCCESS;
                                        loadgen_datain = axi_rdata;
                                    end

                                    // Write the cptag and state to values read from memory
                                    // s1_address_offset contains current write location
                                    // It does not matter what value is araddr (which depends on s1_address_offset), because AR request
                                    // is complete

                                    // After request is done s1_address_offset is invalid
                                    // But it does not matter because next request will overwrite
                                    // it anwyas

                                    cptag_lane = s1_address_lane;
                                    cptag_write[victim_way] = 1;
                                    cptag_writedata = s1_address_cptag;

                                    storage_lane = s1_address_lane;
                                    storage_offset = s1_address_offset;
                                    storage_write[victim_way] = 1;
                                    storage_writedata = axi_rdata;
                                    storage_byteenable = 4'hF;
                                    s1_address_offset_nxt = s1_address_offset + 1; // Note: 64 bit replace number
                                    
                                    
                                    if(axi_rlast) begin
                                        s1_active_nxt = 0;
                                        s1_ar_done_nxt = 0;
                                        s1_refill_errored_nxt = 0;
                                        s1_first_response_done_nxt = 0;
                                        valid_nxt[victim_way][s1_address_lane] = 1;

                                        if(victim_way == WAYS - 1)
                                            victim_way_nxt = 0;
                                        else
                                            victim_way_nxt = victim_way + 1;
                                    end
                                end
                            end
                            
                        end
                        
                    end
                end
            end // vm + tlb hit / no vm
        end // s1_active
        if(!stall) begin
            if(c_cmd != `CACHE_CMD_NONE) begin
                csr_inputs_register = 1;
                
                s1_active_nxt = 1;
                
                s1_address_vtag_nxt = c_address_vtag;
                if(LANES_W != 6)
                    s1_address_cptag_low_nxt = c_address_cptag_low;
                s1_address_lane_nxt = c_address_lane;
                s1_address_offset_nxt = c_address_offset;
                s1_address_inword_offset_nxt = c_address_inword_offset;

                s1_cmd_nxt = c_cmd;
                s1_load_type_nxt = c_load_type;
                s1_store_type_nxt = c_store_type;
                s1_store_data_nxt = c_store_data;

                
                for(i = 0; i < WAYS; i = i + 1) begin
                    s1_valid_per_way_nxt[i] = valid[i][c_address_lane];
                end
                // Logic above has to make sure if stall = 0,
                //      no tlb operation is active
                tlb_cmd = `TLB_CMD_RESOLVE;
                tlb_vaddr_input = c_address_vtag;
                
                
                storage_read = 1'b1;
                storage_lane = c_address_lane;
                storage_offset = c_address_offset;

                cptag_read = 1'b1;
                cptag_lane = c_address_lane;

                s1_aw_done_nxt = 0;
                s1_ar_done_nxt = 0;
                s1_w_done_nxt = 0;
                s1_first_response_done_nxt = 0;
                s1_refill_errored_nxt = 0;
            end
        end
    end
end


`ifdef DEBUG_CACHE

`ifdef FORMAL_RULES
reg formal_reseted;

reg [3:0] formal_last_cmd;
reg [31:0] formal_last_c_address;
reg [2:0] formal_last_c_load_type;
reg [1:0] formal_last_c_store_type;
reg [31:0] formal_last_c_store_data;

always @(posedge clk) begin
    formal_reseted <= formal_reseted || !rst_n;

    if(rst_n && formal_reseted) begin
        assume(
            (c_cmd == `CACHE_CMD_NONE) ||
            (c_cmd == `CACHE_CMD_EXECUTE) ||
            (c_cmd == `CACHE_CMD_STORE) ||
            (c_cmd == `CACHE_CMD_LOAD) ||
            (c_cmd == `CACHE_CMD_FLUSH_ALL) ||
            (c_cmd == `CACHE_CMD_LOAD_RESERVE) ||
            (c_cmd == `CACHE_CMD_STORE_CONDITIONAL)
            );
        
        assume(LANES_W > 0 && LANES_W <= 6);
        formal_last_cmd <= c_cmd;
        formal_last_c_address <= c_address;

        
        formal_last_c_load_type <= c_load_type;
        formal_last_c_store_type <= c_store_type;
        formal_last_c_store_data <= c_store_data;


        // Cases:
        // formal_last_cmd = NONE, c_cmd = x, if c_done -> ERROR
        // formal_last_cmd != NONE, c_done = 0, if c_cmd != formal_last_cmd -> ERROR
        // formal_last_cmd != NONE, c_done = 1 -> NOTHING TO CHECK
        
        //      either last cycle c_done == 1 or c_cmd for last cycle == NONE
        // c_cmd != NONE -> check that
        //      either last cycle (c_done == 1 and formal_last_cmd == NONE)
        //          or formal_last_cmd != 
        if(formal_last_cmd == `CACHE_CMD_NONE) begin
            assert(c_done == 0);
        end
        if((formal_last_cmd != `CACHE_CMD_NONE) && (c_done == 0)) begin
            assume(formal_last_cmd == c_cmd);
            assume(formal_last_c_address == c_address);
            assume(formal_last_c_load_type == c_load_type);
            assume(formal_last_c_store_type == c_store_type);
            assume(formal_last_c_store_data == c_store_data);
        end
    end

    
end
`endif


    always @(posedge clk) begin
        if(IS_INSTRUCTION_CACHE) begin
            assert(!axi_awvalid);
            assert(!axi_wvalid);
        end

        if(s1_active) begin
            if(s1_cmd_flush) begin
                $display("[%m] [CACHE] Flush done");
            end else if(unknowntype) begin
                $display("[%m] [CACHE] Operation done, unknown type");
            end else if(missaligned) begin
                $display("[%m] [CACHE] Operation done, missaligned");
            end else if(vm_enabled && !tlb_hit) begin
                if(ptw_resolve_done) begin
                    $display("[%m] [CACHE] TLB Miss, PTW done, accessfault = %b, pagefault = %b", ptw_accessfault, ptw_pagefault);
                end
            end else if(vm_enabled && pagefault) begin
                $display("[%m] [CACHE] Operation done, Pagefault");
            end else if((!vm_enabled) || (vm_enabled && tlb_hit)) begin
                if(!s1_address_cptag[CACHE_PHYS_TAG_W-1-2] ||
                    s1_cmd_write ||
                    s1_cmd_atomic) begin
                    if(s1_cmd_write) begin
                        if(axi_awvalid && axi_awready) begin
                            $display("[%m] [CACHE] AW done");
                        end
                        if(axi_wvalid && axi_wready) begin
                            $display("[%m] [CACHE] W done");
                        end
                        if(s1_w_done && s1_aw_done && axi_bvalid) begin
                            $display("[%m] [CACHE] write complete, s1_cmd_atomic = 0b%b, axi_bresp = 0b%b", s1_cmd_atomic, axi_bresp);
                        end
                    end else if(s1_cmd_read) begin
                        if(axi_arready && axi_arvalid) begin
                            $display("[%m] [CACHE] AR done");
                        end

                        if(s1_ar_done && axi_rvalid) begin
                            $display("[%m] [CACHE] read complete, s1_cmd_atomic = 0b%b, axi_bresp = 0b%b, axi_rdata = 0x%x", s1_cmd_atomic, axi_rresp, axi_rdata);
                            `assert_equal(axi_rlast, 1)
                        end
                    end else begin
                        `ifdef DEBUG_CACHE
                        $display("Cache: BUG: Invalid state neither write or read");
                        `assert_equal(0, 1)
                        // Only way to force return error code
                        `endif
                    end
                end else if (s1_cmd_read) begin
                    if(os_cache_hit) begin
                        $display("[%m] [CACHE] Cache Hit, os_readdata = 0x%x", os_readdata);
                    end else begin
                        if(axi_arready && axi_arvalid) begin
                            $display("[%m] [CACHE] Refill: AR done");
                        end
                        if(axi_rvalid) begin
                            if(s1_refill_errored) begin
                                `assert(axi_rresp != `AXI_RESP_OKAY);
                                if(axi_rresp != `AXI_RESP_OKAY && !s1_refill_errored) begin
                                    if(!s1_first_response_done)
                                        $display("Error: !ERROR!: !BUG!: Non OKAY AXI response after OKAY response");
                                    `assert_equal(s1_first_response_done, 0)
                                end
                                if(axi_rlast) begin
                                    $display("[%m] [CACHE] Refill: Refill done, refill errored");
                                end
                            end else begin
                                if(axi_rresp != `AXI_RESP_OKAY) begin
                                    if(s1_first_response_done)
                                        $display("Error: !ERROR!: !BUG!: Non OKAY AXI response after OKAY response");
                                    `assert_equal(s1_first_response_done, 0)
                                end
                                if(axi_rlast) begin
                                    $display("[%m] [CACHE] Refill: Refill done, no error");
                                end
                            end

                        end
                    end
                end else begin
                    `ifdef DEBUG_CACHE
                    $display("BUG: s1_active is set but s1_cmd is neither write or read");
                    `assert_equal(0, 1)
                    `endif
                end
            end
        end

        if(!stall) begin
            if(c_cmd == `CACHE_CMD_LOAD) begin
                $display("[%m] [CACHE] Starting load operation c_address = 0x%x, c_load_type = 0b%b",
                    c_address, c_load_type);
                assume(!IS_INSTRUCTION_CACHE);
            end

            if(c_cmd == `CACHE_CMD_EXECUTE) begin
                $display("[%m] [CACHE] Starting execute operation c_address = 0x%x, c_load_type = 0b%b",
                    c_address, c_load_type);
                if(c_load_type != `LOAD_WORD) begin
                    $display("!ERROR!: Error: Execute can only be WORD");
                    `assert_equal(c_load_type, `LOAD_WORD)
                end
            end

            if(c_cmd == `CACHE_CMD_FLUSH_ALL) begin
                $display("[%m] [CACHE] Starting flush operation");
            end

            if(c_cmd == `CACHE_CMD_STORE) begin
                $display("[%m] [CACHE] Starting store operation c_address = 0x%x, c_store_type = 0b%b, c_store_data = 0x%x",
                    c_address, c_store_type, c_store_data);
                assume(!IS_INSTRUCTION_CACHE);
            end

            

            if(c_cmd == `CACHE_CMD_STORE_CONDITIONAL) begin
                $display("[%m] [CACHE] Starting atomic store operation c_address = 0x%x, c_store_data = 0x%x",
                    c_address, c_store_data);
                if(c_store_type != `STORE_WORD) begin
                    $display("!ERROR!: Error: Store type for atomic can only be WORD");
                    `assert_equal(c_store_type, `STORE_WORD)
                end
                assume(!IS_INSTRUCTION_CACHE);
            end

            if(c_cmd == `CACHE_CMD_LOAD_RESERVE) begin
                $display("[%m] [CACHE] Starting execute operation c_address = 0x%x",
                    c_address);
                if(c_load_type != `LOAD_WORD) begin
                    $display("!ERROR!: Error: Load type for atomic can only be WORD");
                    `assert_equal(c_load_type, `LOAD_WORD)
                end
                assume(!IS_INSTRUCTION_CACHE);
            end

        end
    end
    //verilator coverage_off
    reg [7*8-1:0] c_cmd_ascii;
    always @* begin
        case(c_cmd)
            `CACHE_CMD_NONE:                c_cmd_ascii = "NONE";
            `CACHE_CMD_LOAD:                c_cmd_ascii = "LOAD";
            `CACHE_CMD_EXECUTE:             c_cmd_ascii = "EXECUTE";
            `CACHE_CMD_STORE:               c_cmd_ascii = "STORE";
            `CACHE_CMD_FLUSH_ALL:           c_cmd_ascii = "FLUSH";
            `CACHE_CMD_LOAD_RESERVE:        c_cmd_ascii = "LR";
            `CACHE_CMD_STORE_CONDITIONAL:   c_cmd_ascii = "SC";
            default:                        c_cmd_ascii = "UNKNOWN";
        endcase
    end

    reg [7*8-1:0] s1_cmd_ascii;
    always @* begin
        case(s1_cmd)
            `CACHE_CMD_NONE:                s1_cmd_ascii = "NONE";
            `CACHE_CMD_LOAD:                s1_cmd_ascii = "LOAD";
            `CACHE_CMD_EXECUTE:             s1_cmd_ascii = "EXECUTE";
            `CACHE_CMD_STORE:               s1_cmd_ascii = "STORE";
            `CACHE_CMD_FLUSH_ALL:           s1_cmd_ascii = "FLUSH";
            `CACHE_CMD_LOAD_RESERVE:        s1_cmd_ascii = "LR";
            `CACHE_CMD_STORE_CONDITIONAL:   s1_cmd_ascii = "SC";
            default:                        s1_cmd_ascii = "UNKNOWN";
        endcase
    end
    reg [3*8-1:0] s1_load_type_ascii;
    always @* begin
        case (s1_load_type)
            `LOAD_BYTE:             s1_load_type_ascii = "lb";
            `LOAD_BYTE_UNSIGNED:    s1_load_type_ascii = "lbu";
            `LOAD_HALF:             s1_load_type_ascii = "lh";
            `LOAD_HALF_UNSIGNED:    s1_load_type_ascii = "lhu";
            `LOAD_WORD:             s1_load_type_ascii = "lw";
            default:                s1_load_type_ascii = "???";
        endcase
    end
    
    reg [2*8-1:0] s1_store_type_ascii;
    always @* begin
        case (s1_store_type)
            `STORE_BYTE:    s1_store_type_ascii = "sb";
            `STORE_HALF:    s1_store_type_ascii = "sh";
            `STORE_WORD:    s1_store_type_ascii = "sw";
            default:        s1_store_type_ascii = "??";
        endcase
    end

    reg[11*8-1:0] c_response_ascii;
    always @* begin
        case (c_response)
            `CACHE_RESPONSE_SUCCESS:     c_response_ascii = "SUCCESS";
            `CACHE_RESPONSE_MISSALIGNED: c_response_ascii = "MISSALIGNED";
            `CACHE_RESPONSE_PAGEFAULT:   c_response_ascii = "PAGEFAULT";
            `CACHE_RESPONSE_UNKNOWNTYPE: c_response_ascii = "UNKNOWNTYPE";
            `CACHE_RESPONSE_ACCESSFAULT: c_response_ascii = "ACCESSFAULT";
            `CACHE_RESPONSE_ATOMIC_FAIL: c_response_ascii = "ATOMICFAIL";
            default:                     c_response_ascii = "???????????";
        endcase
    end
    //verilator coverage_on
`endif
*/

// verilator lint_on UNUSED
// verilator lint_on UNDRIVEN
`include "armleocpu_undef.vh"

