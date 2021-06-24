////////////////////////////////////////////////////////////////////////////////
//
// Filename: armleocpu_cache.v
// Project:	ArmleoCPU
//
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
//      IS_INSTURCTION_CACHE: 1 or 0. Is used to calculare AXI4 Prot parameter
//		
//
// Copyright (C) 2021, Arman Avetisyan
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`ifdef DEBUG_CACHE
`include "assert.vh"
`endif

`TIMESCALE_DEFINE

module armleocpu_cache (
    input wire              clk,
    input wire              rst_n,

    //                      CACHE <-> EXECUTE/MEMORY
    /* verilator lint_off UNOPTFLAT */
    output reg              c_done,
    output reg   [3:0]      c_response, // CACHE_RESPONSE_*
    /* verilator lint_on UNOPTFLAT */

    input wire [3:0]        c_cmd, // CACHE_CMD_*
    input wire [31:0]       c_address,
    input wire [2:0]        c_load_type, // enum defined in armleocpu_defines.vh LOAD_*
    output wire [31:0]      c_load_data,
    input wire [1:0]        c_store_type, // enum defined in armleocpu_defines.vh STORE_*
    input wire [31:0]       c_store_data,

    //                      CACHE <-> CSR
    //                      SATP from RISC-V privileged spec registered on FLUSH_ALL or SFENCE_VMA
    input wire              csr_satp_mode, // Mode = 0 -> physical access,
                                           // 1 -> ppn valid
    input wire [21:0]       csr_satp_ppn,
    
    //                      Signals from RISC-V privileged spec
    input wire              csr_mstatus_mprv,
    input wire              csr_mstatus_mxr,
    input wire              csr_mstatus_sum,
    input wire [1:0]        csr_mstatus_mpp,

    // Current privilege level. Does not account for anything
    input wire [1:0]        csr_mcurrent_privilege,

    // AXI AW Bus
    output reg          axi_awvalid,
    input  wire         axi_awready,
    output wire [33:0]  axi_awaddr,
    output wire         axi_awlock,
    output wire [2:0]   axi_awprot,

    // AXI W Bus
    output reg          axi_wvalid,
    input  wire         axi_wready,
    output wire [31:0]  axi_wdata,
    output wire [3:0]   axi_wstrb,
    output wire         axi_wlast,
    
    // AXI B Bus
    input  wire         axi_bvalid,
    output reg          axi_bready,
    input  wire [1:0]   axi_bresp,
    
    output reg          axi_arvalid,
    input  wire         axi_arready,
    output wire [33:0]  axi_araddr,
    output reg  [7:0]   axi_arlen,
    output reg  [1:0]   axi_arburst,
    output wire         axi_arlock,
    output wire [2:0]   axi_arprot,
    

    input wire          axi_rvalid,
    output reg          axi_rready,
    input wire  [1:0]   axi_rresp,
    input wire          axi_rlast,
    input wire  [31:0]  axi_rdata
);


// |------------------------------------------------|
// |                                                |
// |              Parameters and includes           |
// |                                                |
// |------------------------------------------------|
//`define DEBUG_CACHE_PTAG
//`define DEBUG_CACHE_LANESTATE_WRITE
//`define DEBUG_CACHE_LANESTATE_READ


parameter WAYS = 2; // 2..16
localparam WAYS_W = $clog2(WAYS);

parameter TLB_ENTRIES_W = 2; // 1..16
parameter TLB_WAYS = 2; // 1..16
localparam TLB_WAYS_W = $clog2(TLB_WAYS);

parameter LANES_W = 1; // 1..6 range.
localparam LANES = 2**LANES_W;

parameter [0:0] IS_INSTURCTION_CACHE = 0;


// 4 = 16 words each 32 bit = 64 byte
localparam OFFSET_W = 4;
localparam INWORD_OFFSET_W = 2;
localparam WORDS_IN_LANE = 2**OFFSET_W;

localparam CACHE_PHYS_TAG_W = 34 - (LANES_W + OFFSET_W + INWORD_OFFSET_W);
localparam TLB_PHYS_TAG_W = 34 - (6 + OFFSET_W + INWORD_OFFSET_W);
// 34 = size of address
// tag = 34 to 
localparam VIRT_TAG_W = 20;

// |------------------------------------------------|
// |                                                |
// |              Cache State                       |
// |                                                |
// |------------------------------------------------|
`DEFINE_REG_REG_NXT(WAYS_W, victim_way, victim_way_nxt, clk)
`DEFINE_REG_REG_NXT(1, tlb_new_entry_done, tlb_new_entry_done_nxt, clk)
`DEFINE_REG_REG_NXT(1, ar_done, ar_done_nxt, clk)
`DEFINE_REG_REG_NXT(1, refill_errored, refill_errored_nxt, clk)
`DEFINE_REG_REG_NXT(1, first_response_done, first_response_done_nxt, clk)
`DEFINE_REG_REG_NXT(1, aw_done, aw_done_nxt, clk)
`DEFINE_REG_REG_NXT(1, w_done, w_done_nxt, clk)


// |------------------------------------------------|
// |                                                |
// |              Output stage                      |
// |                                                |
// |------------------------------------------------|
`DEFINE_REG_REG_NXT(1, os_active, os_active_nxt, clk)

`DEFINE_REG_REG_NXT(VIRT_TAG_W, os_address_vtag, os_address_vtag_nxt, clk)

`DEFINE_REG_REG_NXT(((LANES_W != 6) ? (CACHE_PHYS_TAG_W - TLB_PHYS_TAG_W) : 1), os_address_cptag_low, os_address_cptag_low_nxt, clk)

`DEFINE_REG_REG_NXT(LANES_W, os_address_lane, os_address_lane_nxt, clk)
`DEFINE_REG_REG_NXT(OFFSET_W, os_address_offset, os_address_offset_nxt, clk)
`DEFINE_REG_REG_NXT(2, os_address_inword_offset, os_address_inword_offset_nxt, clk)

`DEFINE_REG_REG_NXT(4, os_cmd, os_cmd_nxt, clk)
`DEFINE_REG_REG_NXT(3, os_load_type, os_load_type_nxt, clk)
`DEFINE_REG_REG_NXT(2, os_store_type, os_store_type_nxt, clk)

`DEFINE_REG_REG_NXT(32, os_store_data, os_store_data_nxt, clk)


// |------------------------------------------------|
// |                                                |
// |              Signals                           |
// |                                                |
// |------------------------------------------------|

wire [1:0] vm_privilege;
wire vm_enabled;

wire c_cmd_access_request, c_cmd_write, c_cmd_read;
wire os_cmd_atomic, os_cmd_flush, os_cmd_write, os_cmd_read;

wire [VIRT_TAG_W-1:0] 	    c_address_vtag          = c_address[31:32-VIRT_TAG_W];
reg  [((LANES_W != 6) ? (6-LANES_W-1) : 0) :0]	    c_address_cptag_low;
wire [LANES_W-1:0]	        c_address_lane          = c_address[INWORD_OFFSET_W+OFFSET_W+LANES_W-1:INWORD_OFFSET_W+OFFSET_W];
wire [OFFSET_W-1:0]			c_address_offset        = c_address[INWORD_OFFSET_W+OFFSET_W-1:INWORD_OFFSET_W];
wire [1:0]			        c_address_inword_offset = c_address[INWORD_OFFSET_W-1:0];

reg                         stall; // Output stage stalls input stage
reg                         unknowntype;
reg                         missaligned;
wire                        pagefault;


reg  [CACHE_PHYS_TAG_W-1:0] os_address_cptag;
// Full tag including physical tag and cache tag low part

reg  [WAYS-1:0]             way_hit;
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
reg  [WAYS-1:0]         storage_read;
wire [31:0]             storage_readdata    [WAYS-1:0];
reg  [WAYS-1:0]         storage_write;
reg  [3:0]              storage_byteenable;
reg  [31:0]             storage_writedata;


reg [LANES-1:0] valid [WAYS-1:0];
reg [LANES-1:0] valid_nxt [WAYS-1:0];


genvar way_num;

generate for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin
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
    // TODO: Mem 1rw -> mem 1rwm
    for(byte_offset = 0; byte_offset < 32; byte_offset = byte_offset + 8) begin : storage_generate_for
        armleocpu_mem_1rw #(
            .ELEMENTS_W(LANES_W+OFFSET_W),
            .WIDTH(8)
        ) datastorage (
            .clk(clk),
            .address({storage_lane, storage_offset}),
            
            .read(storage_read[way_num]),
            .readdata(storage_readdata[way_num][byte_offset+:8]),

            .write(storage_write[way_num] && storage_byteenable[byte_offset/8]),
            .writedata(storage_writedata[byte_offset+:8])
        );
    end
end
endgenerate




// |------------------------------------------------|
// |                   LoadGen                      |
// |------------------------------------------------|

reg [31:0]              loadgen_datain;
wire                    loadgen_missaligned;
wire                    loadgen_unknowntype;
armleocpu_loadgen loadgen(
    .inword_offset          (os_address_inword_offset),
    .loadgen_type           (os_load_type),

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
    .inword_offset          (os_address_inword_offset),
    .storegen_type          (os_store_type),

    .storegen_datain        (os_store_data),

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
    .virtual_address            (ptw_resolve_virtual_address), //os_address_vtag

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
// For first cycle it's c_address_vtag, for writes it's os_address_vtag
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

    .os_cmd                  (os_cmd),
    .tlb_read_metadata       (tlb_metadata_output),

    .pagefault                  (pagefault)
    `ifdef DEBUG_PAGEFAULT
    , .reason(pagefault_reason)
    `endif
);



// |------------------------------------------------|
// |         Output stage data multiplexer          |
// |------------------------------------------------|
always @* begin : output_stage_mux
    integer way_idx;
    os_cache_hit = 1'b0;
    os_readdata = 32'h0;
    os_cache_hit_way = {WAYS_W{1'b0}};
    for(way_idx = WAYS-1; way_idx >= 0; way_idx = way_idx - 1) begin
        way_hit[way_idx] = valid[way_idx] && ((cptag_readdata[way_idx]) == os_address_cptag);
        if(way_hit[way_idx]) begin
            //verilator lint_off WIDTH
            os_cache_hit_way = way_idx;
            //verilator lint_on WIDTH
            os_readdata = storage_readdata[way_idx];
            os_cache_hit = 1'b1;
        end
    end
end

// TODO: Convert REGs to WIREs
assign axi_awprot = {IS_INSTURCTION_CACHE, vm_privilege > `ARMLEOCPU_PRIVILEGE_SUPERVISOR, vm_privilege > `ARMLEOCPU_PRIVILEGE_USER}; // Fixed, not modified anywhere
assign axi_arprot = axi_awprot;
assign axi_awlock = os_cmd_atomic; // Fixed, not modified anywhere
assign axi_awaddr = {os_address_cptag, os_address_lane, os_address_offset, os_address_inword_offset};


assign axi_wdata = storegen_dataout; // Fixed, not modified anywhere
assign axi_wstrb = storegen_datamask; // Fixed, not modified anywhere
assign axi_wlast = 1; // Fixed to 1 for all values

// If moved to always, change to reg in top
assign axi_araddr = {os_address_cptag, os_address_lane, os_address_offset, os_address_inword_offset};
assign axi_arlock = os_cmd_atomic;

assign vm_privilege = ((csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) && csr_mstatus_mprv) ? csr_mstatus_mpp : csr_mcurrent_privilege;
assign vm_enabled = (vm_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR || vm_privilege == `ARMLEOCPU_PRIVILEGE_USER) && csr_satp_mode;


assign c_cmd_read = (c_cmd == `CACHE_CMD_EXECUTE) ||
                (c_cmd == `CACHE_CMD_LOAD) ||
                (c_cmd == `CACHE_CMD_LOAD_RESERVE);
    
assign c_cmd_write             = (c_cmd == `CACHE_CMD_STORE) ||
                              (c_cmd == `CACHE_CMD_STORE_CONDITIONAL);
assign c_cmd_access_request    = c_cmd_read || c_cmd_write;


assign os_cmd_write            = (os_cmd == `CACHE_CMD_STORE) ||
                              (os_cmd == `CACHE_CMD_STORE_CONDITIONAL);
assign os_cmd_flush = (os_cmd == `CACHE_CMD_FLUSH_ALL);
assign os_cmd_atomic           = (os_cmd == `CACHE_CMD_LOAD_RESERVE) || (os_cmd == `CACHE_CMD_STORE_CONDITIONAL);
assign os_cmd_read             = (os_cmd == `CACHE_CMD_LOAD) || (os_cmd == `CACHE_CMD_EXECUTE) || (os_cmd == `CACHE_CMD_LOAD_RESERVE);


always @* begin : cache_comb

    integer i;
`ifdef SIMULATION
    #1
`endif
    // Core output
    c_done = 0;
    c_response = `CACHE_RESPONSE_SUCCESS;
    // c_load_data = loadgen_dataout

    axi_awvalid = 0;
    
    axi_wvalid = 0;

    axi_bready = 0;

    axi_arvalid = 0;
    
    axi_arlen = 1; // 1 or 16
    axi_arburst = `AXI_BURST_INCR; // INCR or WRAP

    axi_rready = 0;

    victim_way_nxt = victim_way;
    tlb_new_entry_done_nxt = tlb_new_entry_done;
    ar_done_nxt = ar_done;
    refill_errored_nxt = refill_errored;
    first_response_done_nxt = first_response_done;
    aw_done_nxt = aw_done;
    w_done_nxt = w_done;
    

    os_active_nxt = os_active;
    os_address_vtag_nxt = os_address_vtag;
    if(LANES_W != 6)
        os_address_cptag_low_nxt = os_address_cptag_low;

    os_address_lane_nxt = os_address_lane;
    os_address_offset_nxt = os_address_offset;
    os_address_inword_offset_nxt = os_address_inword_offset;

    os_cmd_nxt = os_cmd;
    os_load_type_nxt = os_load_type;
    os_store_type_nxt = os_store_type;

    os_store_data_nxt = os_store_data;

    
    if(LANES_W != 6)
        c_address_cptag_low = c_address[32-VIRT_TAG_W-1:INWORD_OFFSET_W+OFFSET_W+LANES_W];
    

    stall = 1;
    if(os_cmd_read) begin
        unknowntype = loadgen_unknowntype;
        missaligned = loadgen_missaligned;
    end else begin
        unknowntype = storegen_unknowntype;
        missaligned = storegen_missaligned;
    end
    // pagefault = pagefault generator's output
    tlb_cmd = `TLB_CMD_NONE;

    if(LANES_W != 6) begin
        os_address_cptag = vm_enabled ?
            {tlb_ptag_output, os_address_cptag_low}
            : {2'b00, os_address_vtag, os_address_cptag_low};
    end else begin
        os_address_cptag = vm_enabled ?
            tlb_ptag_output 
            : {2'b00, os_address_vtag};
    end


    // way_hit, os_cache_hit_way, os_cache_hit, os_readdata
    // signals are assigned in always_comb above


    for(i = 0; i < WAYS; i = i + 1) begin
        cptag_read = 1'b0;
        cptag_lane = c_address_lane; // For write this is os_address_lane
        
        cptag_write[i] = 1'b0;
        cptag_writedata = os_address_cptag;

        storage_read = 1'b0;
        storage_lane = c_address_lane; // TODO: Replace for ACTIVE
        storage_offset = c_address_offset; // TODO: Replace for ACTIVE
        
        storage_write[i] = 1'b0;
        storage_writedata = axi_rdata;
        storage_byteenable = 4'hF;
    end
    for(i = 0; i < WAYS; i = i + 1)
        valid_nxt[i] = valid[i];
    

    ptw_resolve_request = 1'b0;
    ptw_resolve_virtual_address = os_address_vtag;
    
    loadgen_datain = os_readdata; // For bypassed read this is registered axi_rdata
    
    // TODO: Resolve
    // TODO: TLB Connections changed
    tlb_vaddr_input = c_address_vtag;

    tlb_new_entry_metadata_input = ptw_resolve_metadata;
    tlb_new_entry_ptag_input = ptw_resolve_physical_address;
    

    
    if(!rst_n) begin
        os_active_nxt = 0;
        victim_way_nxt = 0;
        for(i = 0; i < WAYS; i = i + 1)
            valid_nxt[i] = {LANES{1'b0}};
        // TLB Invalidate all
        tlb_cmd = `TLB_CMD_INVALIDATE_ALL;
        first_response_done_nxt = 0;
        c_response = `CACHE_RESPONSE_SUCCESS;
        tlb_new_entry_done_nxt = 0;
        aw_done_nxt = 0;
        ar_done_nxt = 0;
        w_done_nxt = 0;
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
        if(os_active) begin
            if(os_cmd_flush) begin
                // cptag storage: written
                // data storage: noop
                // ptw, noop
                // axi: noop
                // loadgen = does not matter
                // tlb, invalidate
                // returns response when done
                os_active_nxt = 0;
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
                os_active_nxt = 0;
            end else if(missaligned) begin
                c_done = 1;
                c_response = `CACHE_RESPONSE_MISSALIGNED;
                os_active_nxt = 0;
            end else if(vm_enabled && !tlb_hit) begin
                // TLB Miss
                stall = 1;
                // TODO: Do actual PTW
                // TODO: Connect AXI to PTW
                    
                // cptag storage: noop
                // data storage: noop
                // TODO: ptw, resolve request
                // TODO: axi is controlled by ptw,
                // axi const ports other ports controlled by logic below
                // loadgen = does not matter
                // TODO: tlb, written
                // next victim is elected, next victim capped to WAYS variable
                // returns response when errors
                ptw_resolve_request = 1;

                if(ptw_resolve_done) begin
                    if(ptw_accessfault || ptw_pagefault) begin
                        os_active_nxt = 0;
                        c_done = 1;
                        c_response = ptw_accessfault ?
                            `CACHE_RESPONSE_ACCESSFAULT : `CACHE_RESPONSE_PAGEFAULT;
                    end else begin
                        tlb_cmd = tlb_new_entry_done ? `TLB_CMD_NONE : `TLB_CMD_NEW_ENTRY;
                        tlb_vaddr_input = os_address_vtag;
                        tlb_new_entry_done_nxt = 1;
                        if(tlb_new_entry_done) begin
                            os_active_nxt = 0;
                            stall = 0;
                            tlb_new_entry_done_nxt = 0;
                        end
                    end
                end
            end else if(vm_enabled && pagefault) begin
                c_done = 1;
                c_response = `CACHE_RESPONSE_PAGEFAULT;
                os_active_nxt = 0;
            end else if((!vm_enabled) || (vm_enabled && tlb_hit)) begin
                // If physical address or virtual and tlb is hit
                // For atomic operations or writes do AXI request
                // No magic value below:
                //      if 31th bit is reset then data is not cached
                //      31th bit (starting from 0) is value below, because cptag is top part of 34 bits of physical address
                if(!os_address_cptag[CACHE_PHYS_TAG_W-1-2] ||
                    os_cmd_write ||
                    os_cmd_atomic) begin // TODO: Bypass case
                        stall = 1;
                        if(os_cmd_write && os_cache_hit) begin // TODO: Check for 31th bit (starting from 0), and dont invalidate if it is not set
                            valid_nxt[os_cache_hit_way][os_address_lane] = 1; // TODO: Maybe instead of invalidating, just rewrite it?
                        end
                        if(os_cmd_write) begin
                            stall = 1;
                            // Note: AW and W ports need to start request at the same time
                            // Note: AW and W might be "ready" in different order
                            axi_awvalid = !aw_done;
                            // axi_awaddr and other aw* values is set in logic at the start of always block
                            if(axi_awready) begin
                                aw_done_nxt = 1;
                            end

                            // only axi port active
                            // TODO: Make sure that storegen is WORD and not anything else for atomic access
                            axi_wvalid = !w_done;
                            // axi_wdata = storegen_dataout; // This is fixed in assignments above
                            //axi_wlast = 1; This is set in logic at the start of always block
                            //axi_wstrb = storegen_datamask; This is set in logic at the start of always block
                            if(axi_wready) begin
                                w_done_nxt = 1;
                            end

                            if(w_done && aw_done) begin
                                axi_bready = 1;
                                if(axi_bvalid) begin
                                    // TODO: Return response, depending on axi_bresp
                                    // TODO: Convert axi bresp EXOKAY and non-EXOKAYs accordingly
                                    // TODO: Dont return to os_error
                                    c_done = 1;
                                    if(os_cmd_atomic && axi_bresp == `AXI_RESP_EXOKAY) begin
                                        c_response = `CACHE_RESPONSE_SUCCESS;
                                    end else if(os_cmd_atomic && axi_bresp == `AXI_RESP_OKAY) begin
                                        c_response = `CACHE_RESPONSE_ATOMIC_FAIL;
                                    end else if(!os_cmd_atomic && axi_bresp == `AXI_RESP_OKAY) begin
                                        c_response = `CACHE_RESPONSE_SUCCESS;
                                    end else begin
                                        c_response = `CACHE_RESPONSE_ACCESSFAULT;
                                    end
                                    w_done_nxt = 0;
                                    aw_done_nxt = 0;
                                    os_active_nxt = 0;
                                end
                            end
                        end else if(os_cmd_read) begin
                            stall = 1;
                            // ATOMIC operation or cache bypassed access
                            // cptag storage: noop
                            // data storage: noop
                            // ptw, noop
                            // axi is controlled by code below,
                            // loadgen = axi_rdata
                            // tlb, output used
                            // returns response when errors

                            axi_arvalid = !ar_done;
                            axi_arlen = 0;
                            axi_arburst = `AXI_BURST_INCR;
                            // axi_araddr is assigned above

                            if(axi_arready) begin
                                ar_done_nxt = 1;
                            end
                            loadgen_datain = axi_rdata;
                            if(ar_done && axi_rvalid) begin
                                axi_rready = 1;
                                if(os_cmd_atomic && axi_rresp == `AXI_RESP_EXOKAY) begin
                                    c_response = `CACHE_RESPONSE_SUCCESS;
                                    c_done = 1;
                                end else if(os_cmd_atomic && axi_rresp == `AXI_RESP_OKAY) begin
                                    c_response = `CACHE_RESPONSE_ATOMIC_FAIL;
                                    c_done = 1;
                                end else if(!os_cmd_atomic && axi_rresp == `AXI_RESP_OKAY) begin
                                    c_response = `CACHE_RESPONSE_SUCCESS;
                                    c_done = 1;
                                end else begin
                                    c_response = `CACHE_RESPONSE_ACCESSFAULT;
                                    c_done = 1;
                                end
                                os_active_nxt = 0;
                                ar_done_nxt = 0;
                                stall = 0;
                            end
                        end else begin
                            `ifdef DEBUG_CACHE
                            $display("Cache: BUG: Invalid state neither write or read");
                            `assert_equal(0, 1)
                            // Only way to force return error code
                            `endif
                        end
                end else if(os_cmd_read) begin // Not atomic, not bypassed
                    if(os_cache_hit) begin
                        os_active_nxt = 0;
                        c_response = `CACHE_RESPONSE_SUCCESS;
                        c_done = 1;
                        loadgen_datain = os_readdata;
                        stall = 0;
                    end else begin
                        // Cache Miss
                        stall = 1;

                        axi_arlen = WORDS_IN_LANE-1;
                        axi_arburst = `AXI_BURST_WRAP;

                        // TODO: cptag storage: written
                        // data storage: written
                        // ptw, noop
                        // axi: read only, wrap burst
                        // loadgen = outputs data for first beat, because it's wrap request
                        // returns response
                        // No need to output error for other error types other than accessfault for rresp,
                        //      because that cases are covered by code in active state
                        //      transition to this means that this errors (unknown type, pagefault) are already covered
                        // tlb, output is used
                        // TODO: os_address_offset is incremented and looped
                        // TODO: Implement reading
                        // TODO: If done
                        axi_arvalid = !ar_done;
                        if(axi_arready) begin
                            ar_done_nxt = 1;
                        end

                        if(axi_rvalid) begin
                            if(refill_errored) begin
                                // If error happened, fast forward, until last result
                                // And on last response return access fault
                                axi_rready = 1;
                                if(axi_rlast) begin
                                    // No response is returned because
                                    //  
                                    os_active_nxt = 0;
                                    ar_done_nxt = 0;
                                    refill_errored_nxt = 0;
                                    first_response_done_nxt = 0;
                                end
                            end else begin
                                axi_rready = 1;

                                if(axi_rresp != `AXI_RESP_OKAY) begin
                                    refill_errored_nxt = 1;
                                    // If last then no next cycle is possible
                                    // So just return the response
                                    if(axi_rlast) begin
                                        c_done = 1;
                                        c_response = `CACHE_RESPONSE_ACCESSFAULT;
                                        os_active_nxt = 0;
                                        ar_done_nxt = 0;
                                        refill_errored_nxt = 0;
                                        first_response_done_nxt = 0;
                                        `ifdef DEBUG_CACHE
                                            $display("Error: !ERROR!: !BUG!: Non OKAY AXI response after OKAY response");
                                            `assert_equal(0, 1)
                                        `endif
                                        valid_nxt[victim_way][os_address_lane] = 0;
                                    end
                                end else begin
                                    // Response is valid and resp is OKAY
                                    first_response_done_nxt = 1;
                                    if(!first_response_done) begin
                                        c_done = 1;
                                        c_response = `CACHE_RESPONSE_SUCCESS;
                                        loadgen_datain = axi_rdata;
                                    end

                                    cptag_lane = os_address_lane;
                                    cptag_write[victim_way] = 1;
                                    cptag_writedata = os_address_cptag;

                                    storage_lane = os_address_lane;
                                    storage_offset = os_address_offset;
                                    storage_write[victim_way] = 1;
                                    storage_writedata = axi_rdata;
                                    storage_byteenable = 4'hF;
                                    os_address_offset_nxt = os_address_offset + 1; // Note: 64 bit replace number
                                    
                                    //  TODO: return first response for WRAP burst
                                    // TODO: Write the cptag and state
                                    // TODO: 
                                    if(axi_rlast) begin
                                        os_active_nxt = 0;
                                        ar_done_nxt = 0;
                                        refill_errored_nxt = 0;
                                        first_response_done_nxt = 0;
                                        valid_nxt[victim_way][os_address_lane] = 1;

                                        if(victim_way == WAYS - 1)
                                            victim_way_nxt = 0;
                                        else
                                            victim_way_nxt = victim_way + 1;
                                    end
                                end
                            end
                            
                        end
                        
                    end
                end else begin
                    `ifdef DEBUG_CACHE
                    $display("BUG: os_active is set but os_cmd is neither write or read");
                    `assert_equal(0, 1)
                    `endif
                end // Not bypassed
            end // vm + tlb hit / no vm
        end // OS_ACTIVE*/
        if(!stall) begin
            if(c_cmd != `CACHE_CMD_NONE) begin // TODO: Accept all requests
                os_active_nxt = 1;
                
                os_address_vtag_nxt = c_address_vtag;
                if(LANES_W != 6)
                    os_address_cptag_low_nxt = c_address_cptag_low;
                os_address_lane_nxt = c_address_lane;
                os_address_offset_nxt = c_address_offset;
                os_address_inword_offset_nxt = c_address_inword_offset;

                os_cmd_nxt = c_cmd;
                os_load_type_nxt = c_load_type;
                os_store_type_nxt = c_store_type;
                os_store_data_nxt = c_store_data;

                // TODO: Make sure if stall = 0, no tlb operation is active
                tlb_cmd = `TLB_CMD_RESOLVE;
                tlb_vaddr_input = c_address_vtag;
                
                storage_read = 1'b1;
                cptag_read = 1'b1;

                aw_done_nxt = 0;
                ar_done_nxt = 0;
                w_done_nxt = 0;
                tlb_new_entry_done_nxt = 0;
            end
        end
    end
end


`ifdef DEBUG_CACHE
    /*always @(posedge clk) begin
        if(!stall) begin
            if()
        end
    end*/
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
            // TODO: Add commands
            default:                        c_cmd_ascii = "UNKNOWN";
        endcase
    end

    reg [7*8-1:0] os_cmd_ascii;
    always @* begin
        case(os_cmd)
            `CACHE_CMD_NONE:                os_cmd_ascii = "NONE";
            `CACHE_CMD_LOAD:                os_cmd_ascii = "LOAD";
            `CACHE_CMD_EXECUTE:             os_cmd_ascii = "EXECUTE";
            `CACHE_CMD_STORE:               os_cmd_ascii = "STORE";
            `CACHE_CMD_FLUSH_ALL:           os_cmd_ascii = "FLUSH";
            `CACHE_CMD_LOAD_RESERVE:        os_cmd_ascii = "LR";
            `CACHE_CMD_STORE_CONDITIONAL:   os_cmd_ascii = "SC";
            default:                        os_cmd_ascii = "UNKNOWN";
        endcase
    end
    reg [3*8-1:0] os_load_type_ascii;
    always @* begin
        case (os_load_type)
            `LOAD_BYTE:             os_load_type_ascii = "lb";
            `LOAD_BYTE_UNSIGNED:    os_load_type_ascii = "lbu";
            `LOAD_HALF:             os_load_type_ascii = "lh";
            `LOAD_HALF_UNSIGNED:    os_load_type_ascii = "lhu";
            `LOAD_WORD:             os_load_type_ascii = "lw";
            default:                os_load_type_ascii = "???";
        endcase
    end
    
    reg [2*8-1:0] os_store_type_ascii;
    always @* begin
        case (os_store_type)
            `STORE_BYTE:    os_store_type_ascii = "sb";
            `STORE_HALF:    os_store_type_ascii = "sh";
            `STORE_WORD:    os_store_type_ascii = "sw";
            default:        os_store_type_ascii = "??";
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


endmodule

`include "armleocpu_undef.vh"

