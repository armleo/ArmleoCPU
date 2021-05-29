
`include "armleocpu_defines.vh"

module armleocpu_cache (
    input                   clk,
    input                   rst_n,

    //                      CACHE <-> EXECUTE/MEMORY
    /* verilator lint_off UNOPTFLAT */
    output reg   [3:0]      c_response, // CACHE_RESPONSE_*
    /* verilator lint_on UNOPTFLAT */
    output wire             c_reset_done,

    input  [3:0]            c_cmd, // CACHE_CMD_*
    input  [31:0]           c_address,
    input  [2:0]            c_load_type, // enum defined in ARMLEOCPU_defs LOAD_*
    output wire  [31:0]     c_load_data,
    input [1:0]             c_store_type, // enum defined in ARMLEOCPU_defs STORE_*
    input [31:0]            c_store_data,

    //                      CACHE <-> CSR
    //                      More in docs/
    //                      SATP from RISC-V privileged spec registered on FLUSH_ALL or SFENCE_VMA
    input                   csr_satp_mode, // Mode = 0 -> physical access,
                                           // 1 -> ppn valid
    input [21:0]            csr_satp_ppn,
    
    //                      MPRV from RISC-V privileged spec
    input                   csr_mstatus_mprv,
    //                      MXR from RISC-V privileged spec
    input                   csr_mstatus_mxr,
    //                      SUM from RISC-V privileged spec
    input                   csr_mstatus_sum,
    //                      MPP from RISC-V privileged spec
    input [1:0]             csr_mstatus_mpp,

    //                      MPRV from armleocpu Extension
    input [1:0]             csr_mcurrent_privilege,


    
    //                      CACHE <-> MEMORY
    output reg              m_transaction,
    output reg   [2:0]      m_cmd,         // enum `ARMLEOBUS_CMD_*
    input                   m_transaction_done,
    input        [2:0]      m_transaction_response, // enum `ARMLEOBUS_RESPONSE_*
    output reg   [33:0]     m_address,
    output reg   [3:0]      m_burstcount,
    output reg   [31:0]     m_wdata,
    output reg   [3:0]      m_wbyte_enable,
    input        [31:0]     m_rdata
);


// |------------------------------------------------|
// |                                                |
// |              Parameters and includes           |
// |                                                |
// |------------------------------------------------|
//`define DEBUG_CACHE_PTAG
//`define DEBUG_CACHE_LANESTATE_WRITE
//`define DEBUG_CACHE_LANESTATE_READ



parameter WAYS_W = 2;
localparam WAYS = 2**WAYS_W;

parameter TLB_ENTRIES_W = 4;
parameter TLB_WAYS_W = 2;
localparam TLB_ENTRIES = 2**TLB_ENTRIES_W;
parameter BYPASS_ENABLED = 0;
// TODO:

parameter LANES_W = 1;
localparam LANES = 2**LANES_W;


// 4 = 16 words each 32 bit = 64 byte
localparam OFFSET_W = 4;
localparam INWORD_OFFSET_W = 2;
localparam WORDS_IN_LANE = 2**OFFSET_W;

localparam CACHE_PHYS_TAG_W = 34 - (LANES_W + OFFSET_W + INWORD_OFFSET_W);
localparam TLB_PHYS_TAG_W = 34 - (6 + OFFSET_W + INWORD_OFFSET_W)
// 34 = size of address
// tag = 34 to 
localparam VIRT_TAG_W = 20;





// |------------------------------------------------|
// |                                                |
// |              Cache State                       |
// |                                                |
// |------------------------------------------------|

reg [3:0]   state;
reg [3:0]   return_state;
reg         aborted;
localparam 	STATE_RESET = 4'd0,
            STATE_ACTIVE = 4'd1,
            STATE_REFILL = 4'd2,
            STATE_PTW = 4'd3;

reg [WAYS_W-1:0] victim_way;

// |------------------------------------------------|
// |                                                |
// |              Output stage                      |
// |                                                |
// |------------------------------------------------|
reg                         os_active;
reg                         os_error;
reg                         os_error_type;

//                          address decomposition
reg [VIRT_TAG_W-1:0]        os_address_vtag; // Used by PTW
reg [LANES_W-1:0]           os_address_lane;
reg [OFFSET_W-1:0]          os_address_offset;
reg [1:0]                   os_address_inword_offset;


reg [3:0]                   os_cmd;
reg [2:0]                   os_load_type;
reg [1:0]                   os_store_type;

reg [31:0]                  os_store_data;

reg                         csr_satp_mode_r;
reg [21:0]                  csr_satp_ppn_r;


`ifdef DEBUG_CACHE
    /*verilator coverage_off*/
    reg [7*8-1:0] c_cmd_ascii;
    always @* begin
        case(c_cmd)
            `CACHE_CMD_LOAD:        c_cmd_ascii = "LOAD";
            `CACHE_CMD_EXECUTE:     c_cmd_ascii = "EXECUTE";
            `CACHE_CMD_STORE:       c_cmd_ascii = "STORE";
            `CACHE_CMD_FLUSH_ALL:   c_cmd_ascii = "FLUSH";
            `CACHE_CMD_ABORT:       c_cmd_ascii = "ABORT";
            default:                c_cmd_ascii = "UNKNOWN";
        endcase
    end

    reg [7*8-1:0] os_cmd_ascii;
    always @* begin
        case(os_cmd)
            `CACHE_CMD_LOAD:        os_cmd_ascii = "LOAD";
            `CACHE_CMD_EXECUTE:     os_cmd_ascii = "EXECUTE";
            `CACHE_CMD_STORE:       os_cmd_ascii = "STORE";
            `CACHE_CMD_FLUSH_ALL:   os_cmd_ascii = "FLUSH";
            `CACHE_CMD_ABORT:       os_cmd_ascii = "ABORT";
            default:                os_cmd_ascii = "UNKNOWN";
        endcase
    end
    reg [3*8-1:0] os_load_type_ascii;
    always @* begin
        case (os_load_type)
            `LOAD_BYTE:
                os_load_type_ascii = "lb";
            `LOAD_BYTE_UNSIGNED:
                os_load_type_ascii = "lbu";
            `LOAD_HALF:
                os_load_type_ascii = "lh";
            `LOAD_HALF_UNSIGNED:
                os_load_type_ascii = "lhu";
            `LOAD_WORD:
                os_load_type_ascii = "lw";
            default:
                os_load_type_ascii = "???";
        endcase
    end
    
    reg [2*8-1:0] os_store_type_ascii;
    always @* begin
        case (os_store_type)
            `STORE_BYTE:
                os_store_type_ascii = "sb";
            `STORE_HALF:
                os_store_type_ascii = "sh";
            `STORE_WORD:
                os_store_type_ascii = "sw";
            default:
                os_store_type_ascii = "??";
        endcase
    end

    reg[11*8-1:0] c_response_ascii;
    always @* begin
        case (c_response)
            `CACHE_RESPONSE_IDLE:        c_response_ascii = "IDLE";
            `CACHE_RESPONSE_DONE:        c_response_ascii = "DONE";
            `CACHE_RESPONSE_WAIT:        c_response_ascii = "WAIT";
            `CACHE_RESPONSE_MISSALIGNED: c_response_ascii = "MISSALIGNED";
            `CACHE_RESPONSE_PAGEFAULT:   c_response_ascii = "PAGEFAULT";
            `CACHE_RESPONSE_UNKNOWNTYPE: c_response_ascii = "UNKNOWNTYPE";
            `CACHE_RESPONSE_ACCESSFAULT: c_response_ascii = "ACCESSFAULT";
            default:
                c_response_ascii = "???????????";
        endcase
    end
    /*verilator coverage_on*/
`endif


// |------------------------------------------------|
// |                                                |
// |              Signals                           |
// |                                                |
// |------------------------------------------------|

// PTAG port
reg  [LANES_W-1:0]      ptag_lane;
reg                     ptag_read           [WAYS-1:0];
wire [CACHE_PHYS_TAG_W-1:0]
                        ptag_readdata       [WAYS-1:0];
reg                     ptag_write          [WAYS-1:0];
reg  [PHYS_W-1:0]       ptag_writedata;

//                      Storage read/write port vars
reg  [LANES_W-1:0]      storage_lane    [WAYS-1:0];
reg  [OFFSET_W-1:0]     storage_offset  [WAYS-1:0];
reg  [WAYS-1:0]         storage_read;
wire [31:0]             storage_readdata    [WAYS-1:0];
reg  [WAYS-1:0]         storage_write;
reg  [3:0]              storage_byteenable;
reg  [31:0]             storage_writedata;

//                      lanestate read port
reg                     lanestate_read           [WAYS-1:0];
reg  [LANES_W-1:0]      lanestate_lane           [WAYS-1:0];
wire [WAYS-1:0]         lanestate_readdata       [1:0];
//                      lanestate write port
reg  [WAYS-1:0]         lanestate_write;
reg  [1:0]              lanestate_writedata;


// PTW request signals
reg                     ptw_resolve_request;
reg  [19:0]             ptw_resolve_vtag;
// PTW result signals
wire                    ptw_resolve_done;
wire                    ptw_pagefault;
wire                    ptw_accessfault;


`ifdef DEBUG_PAGEFAULT
wire [30*8-1:0] pagefault_reason;
`endif

wire [7:0]              ptw_resolve_access_bits;
wire [PHYS_W-1:0]       ptw_resolve_phystag;

// PTW AXI4 Signals

// Store gen signals
wire [31:0]             storegen_dataout;
wire [3:0]              storegen_mask;
wire                    storegen_missaligned;
wire                    storegen_unknowntype;
// Load gen signals
reg [31:0]              loadgen_datain;
wire                    loadgen_missaligned;
wire                    loadgen_unknowntype;


reg  [1:0]              tlb_command;

// read port
reg  [19:0]             tlb_resolve_virtual_address;
wire                    tlb_hit;
wire [7:0]              tlb_read_accesstag;
wire [TLB_PHYS_TAG_W-1:0]       tlb_read_ptag;
wire [CACHE_PHYS_TAG_W-1:0] tlb_read_cptag;

// write port
reg  [19:0]             tlb_write_vtag;
reg  [7:0]              tlb_write_accesstag;
reg  [TLB_PHYS_TAG_W-1:0]       tlb_write_ptag;


// TODO: correctly handle vm_enabled csr_satp_mode_r
wire [1:0] vm_privilege = ((csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) && csr_mstatus_mprv) ? csr_mstatus_mpp : csr_mcurrent_privilege;
wire vm_enabled = (vm_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR || vm_privilege == `ARMLEOCPU_PRIVILEGE_USER) && csr_satp_mode_r;

wire c_cmd_access_request =   (c_cmd == `CACHE_CMD_EXECUTE) ||
                        (c_cmd == `CACHE_CMD_LOAD) ||
                        (c_cmd == `CACHE_CMD_STORE);
wire c_cmd_abort = c_cmd == `CACHE_CMD_ABORT;

wire [VIRT_TAG_W-1:0] 	    c_address_vtag          = c_address[31:32-VIRT_TAG_W];
wire [LANES_W-1:0]	        c_address_lane          = c_address[INWORD_OFFSET_W+OFFSET_W+LANES_W-1:2+OFFSET_W];
wire [OFFSET_W-1:0]			c_address_offset        = c_address[INWORD_OFFSET_W+OFFSET_W-1:2];
wire [1:0]			        c_address_inword_offset = c_address[INWORD_OFFSET_W-1:0];


reg                         stall; // Output stage stalls input stage
wire                        pagefault;
reg                         unknowntype;
reg                         missaligned;


wire [PHYS_W-1:0]           cptag;
[LANES_W-1:6-LANES_W-1]
assign cptag = vm_enabled ? {tlb_read_ptag, } : {2'b00, os_address_vtag};

// TODO: Calculate CPTAG

reg  [WAYS-1:0]             way_hit;
reg  [WAYS_W-1:0]           os_cache_hit_way;
reg                         os_cache_hit;
reg  [31:0]                 os_readdata;

wire [WAYS-1:0]             os_valid_per_way = lanestate_readdata[0];
wire [WAYS-1:0]             os_dirty_per_way = lanestate_readdata[1];


assign c_reset_done = state != STATE_RESET;


genvar way_num;
genvar byte_offset;
generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : mem_generate_for
    armleocpu_mem_1rw #(
        .ELEMENTS_W(LANES_W),
        .WIDTH(CACHE_PHYS_TAG_W)
    ) ptag_storage (
        .clk(clk),
        .address(ptag_lane[way_num]),

        .read(ptag_read[way_num]),
        .readdata(ptag_readdata[way_num]),
        
        .write(ptag_write[way_num]),
        .writedata(ptag_writedata)
    );
    
    armleocpu_mem_1rw #(
        .ELEMENTS_W(LANES_W),
        .WIDTH(2)
    ) lanestatestorage (
        .clk(clk),
        
        .address(lanestate_lane[way_num]),

        .read(lanestate_read[way_num]),
        .readdata({lanestate_readdata[1][way_num], lanestate_readdata[0][way_num]}),

        .write(lanestate_write[way_num]),
        .writedata(lanestate_writedata)
    );

    for(byte_offset = 0; byte_offset < 32; byte_offset = byte_offset + 8) begin : storage_generate_for
        armleocpu_mem_1rw #(
            .ELEMENTS_W(LANES_W+OFFSET_W),
            .WIDTH(8)
        ) datastorage (
            .clk(clk),
            .address({storage_lane[way_num], storage_offset[way_num]}),
            
            .read(storage_read[way_num]),
            .readdata(storage_readdata[way_num][byte_offset+7:byte_offset]),

            .write(storage_write[way_num] && storage_byteenable[byte_offset/8]),
            .writedata(storage_writedata[byte_offset+7:byte_offset])
        );
    end
end
endgenerate




// |------------------------------------------------|
// |                   LoadGen                      |
// |------------------------------------------------|

armleocpu_loadgen loadgen(
    .inwordOffset       (os_address_inword_offset),
    .loadType           (os_load_type),

    .LoadGenDataIn      (loadgen_datain),

    .LoadGenDataOut     (c_load_data),
    .LoadMissaligned    (loadgen_missaligned),
    .LoadUnknownType    (loadgen_unknowntype)
);

// |------------------------------------------------|
// |                 StoreGen                       |
// |------------------------------------------------|


armleocpu_storegen storegen(
    .inwordOffset           (os_address_inword_offset),
    .storegenType           (os_store_type),

    .storegenDataIn         (os_store_data),

    .storegenDataOut        (storegen_dataout),
    .storegenDataMask       (storegen_mask),
    .storegenMissAligned    (storegen_missaligned),
    .storegenUnknownType    (storegen_unknowntype)
);


// Page table walker instance
armleocpu_ptw ptw(
    .clk                    (clk),
    .rst_n                  (rst_n),

    .m_transaction          (ptw_m_transaction),
    .m_cmd                  (ptw_m_cmd),
    .m_address              (ptw_m_address),
    .m_transaction_done     (m_transaction_done),
    .m_transaction_response (m_transaction_response),
    .m_rdata                (m_rdata),
    
    .resolve_request        (ptw_resolve_request),
    /*verilator lint_off PINCONNECTEMPTY*/
    .resolve_ack            (),
    /*verilator lint_on PINCONNECTEMPTY*/
    .virtual_address        (ptw_resolve_vtag/*os_address_vtag*/),

    .resolve_done           (ptw_resolve_done),
    .resolve_pagefault      (ptw_pagefault),
    .resolve_accessfault    (ptw_accessfault),

    .resolve_access_bits    (ptw_resolve_access_bits),
    .resolve_physical_address(ptw_resolve_phystag),

    .satp_mode              (csr_satp_mode_r),
    .satp_ppn               (csr_satp_ppn_r)
);

armleocpu_tlb #(TLB_ENTRIES_W, TLB_WAYS_W) tlb(
    .rst_n                  (rst_n),
    .clk                    (clk),
    
    .command                (tlb_command),

    // read port
    .virtual_address        (tlb_resolve_virtual_address),
    .hit                    (tlb_hit),
    .accesstag_r            (tlb_read_accesstag),
    .phys_r                 (tlb_read_ptag),
    
    // write for for entry virt
    .virtual_address_w      (tlb_write_vtag),
    .accesstag_w            (tlb_write_accesstag),
    .phys_w                 (tlb_write_ptag),

    .invalidate_set_index   (tlb_invalidate_set_index)
);


armleocpu_cache_pagefault pagefault_generator(
    .csr_satp_mode_r            (csr_satp_mode_r),

    .csr_mcurrent_privilege  (csr_mcurrent_privilege),
    .csr_mstatus_mprv        (csr_mstatus_mprv),
    .csr_mstatus_mxr         (csr_mstatus_mxr),
    .csr_mstatus_sum         (csr_mstatus_sum),
    .csr_mstatus_mpp         (csr_mstatus_mpp),

    .os_cmd                     (os_cmd),
    .tlb_read_accesstag         (tlb_read_accesstag),

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
        way_hit[way_idx] = os_valid_per_way[way_idx] && ((ptag_readdata[way_idx]) == ptag);
        if(way_hit[way_idx]) begin
            /*verilator lint_off WIDTH*/
            os_cache_hit_way = way_idx;
            /*verilator lint_on WIDTH*/
            os_readdata = storage_readdata[way_idx];
            os_cache_hit = 1'b1;
        end
    end
end


// unknowntype, missaligned multiplexer
always @* begin
    if(os_cmd == `CACHE_CMD_LOAD) begin
        unknowntype = loadgen_unknowntype;
        missaligned = loadgen_missaligned;
    end else begin
        unknowntype = storegen_unknowntype;
        missaligned = storegen_missaligned;
    end
end


always @* begin : cache_comb
    integer i;

    tlb_command = `TLB_CMD_NONE;
    stall = 1;
    c_response = `CACHE_RESPONSE_IDLE;

    m_transaction = 0;
    m_cmd = `ARMLEOBUS_CMD_NONE;
    m_address = {ptag, os_address_lane, os_address_offset, 2'b00};
    m_burstcount = 0;
    m_wdata = storegen_dataout;
    m_wbyte_enable = storegen_mask;

    for(i = 0; i < WAYS; i = i + 1) begin
        ptag_read[i] = 1'b0;
        ptag_readlane[i] = c_address_lane;
        ptag_write[i] = 1'b0;

        lanestate_read[i] = 1'b0;
        lanestate_readlane[i] = c_address_lane;
        lanestate_write[i] = 1'b0;

        storage_read[i] = 1'b0;
        storage_readlane[i] = {LANES_W{1'b0}};
        storage_readoffset[i] = {OFFSET_W{1'b0}};
        storage_write[i] = 1'b0;
    end
    storage_writedata = storegen_dataout;
    storage_writelane = os_address_lane;
    storage_writeoffset = os_address_offset;
    storage_byteenable = storegen_mask;

    lanestate_writelane = {LANES_W{1'b0}};
    lanestate_writedata = 2'b11; // valid and dirty

    ptag_writedata = ptag;
    ptag_writelane = os_address_lane;
    ptw_resolve_request = 1'b0;
    ptw_resolve_vtag = os_address_vtag;
    loadgen_datain_sel = 0;
    
    tlb_resolve_virtual_address = c_address_vtag;
    tlb_write_vtag = os_address_vtag;
    tlb_write_accesstag = ptw_resolve_access_bits;
    tlb_write_ptag = ptw_resolve_phystag;
    
    if(tlb_invalidate_set_index == TLB_ENTRIES-1) begin
        tlb_invalidate_done = 1'b1;
    end else begin
        tlb_invalidate_done = 1'b0;
    end
    // used by state reset
    if(reset_lane_counter == LANES-1) begin
        reset_valid_reset_done = 1'b1;
    end else begin
        reset_valid_reset_done = 1'b0;
    end
end
