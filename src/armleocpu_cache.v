
`include "armleocpu_defines.vh"

module armleocpu_cache (
    input wire              clk,
    input wire              rst_n,

    //                      CACHE <-> EXECUTE/MEMORY
    /* verilator lint_off UNOPTFLAT */
    output reg   [3:0]      c_response, // CACHE_RESPONSE_*
    /* verilator lint_on UNOPTFLAT */
    output reg              c_reset_done,

    input wire [3:0]        c_cmd, // CACHE_CMD_*
    input wire [31:0]       c_address,
    input wire [2:0]        c_load_type, // enum defined in ARMLEOCPU_defs LOAD_*
    output wire [31:0]      c_load_data,
    input wire [1:0]        c_store_type, // enum defined in ARMLEOCPU_defs STORE_*
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
    output reg  [33:0]  axi_awaddr,
    output reg          axi_awlock,
    output wire [2:0]   axi_awprot,

    // AXI W Bus
    output reg          axi_wvalid,
    input  wire         axi_wready,
    output reg  [31:0]  axi_wdata,
    output reg  [3:0]   axi_wstrb,
    output reg          axi_wlast,
    
    // AXI B Bus
    input  wire         axi_bvalid,
    output reg          axi_bready,
    input  wire [1:0]   axi_bresp,
    
    output reg          axi_arvalid,
    input  wire         axi_arready,
    output reg  [33:0]  axi_araddr,
    output reg  [7:0]   axi_arlen,
    output reg  [1:0]   axi_arburst,
    output reg          axi_arlock,
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


parameter WAYS_W = 2;
localparam WAYS = 2**WAYS_W;

parameter TLB_ENTRIES_W = 4;
parameter TLB_WAYS_W = 2;
localparam TLB_ENTRIES = 2**TLB_ENTRIES_W;

parameter LANES_W = 1;
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
`DEFINE_REG_REG_NXT(4, state, state_nxt, clk)
`DEFINE_REG_REG_NXT(4, return_state, return_state_nxt, clk)
`DEFINE_REG_REG_NXT(1, aborted, aborted_nxt, clk)
`DEFINE_REG_REG_NXT(WAYS_W, victim_way, victim_way_nxt, clk)

localparam 	STATE_RESET = 4'd0,
            STATE_ACTIVE = 4'd1,
            STATE_WRITE = 4'd2,
            STATE_REFILL = 4'd2,
            STATE_PTW = 4'd3;

// |------------------------------------------------|
// |                                                |
// |              Output stage                      |
// |                                                |
// |------------------------------------------------|
`DEFINE_REG_REG_NXT(1, os_active, os_active_nxt, clk)
`DEFINE_REG_REG_NXT(1, refill_error, refill_error_nxt, clk)
`DEFINE_REG_REG_NXT(1, refill_error_type, refill_error_type_nxt, clk)


`DEFINE_REG_REG_NXT(VIRT_TAG_W, os_address_vtag, os_address_vtag_nxt, clk)
`DEFINE_REG_REG_NXT((CACHE_PHYS_TAG_W - VIRT_TAG_W), os_address_cptag_low, os_address_cptag_low_nxt, clk)
`DEFINE_REG_REG_NXT(LANES_W, os_address_lane, os_address_lane_nxt, clk)
`DEFINE_REG_REG_NXT(OFFSET_W, os_address_offset, os_address_offset_nxt, clk)
`DEFINE_REG_REG_NXT(2, os_address_inword_offset, os_address_inword_offset_nxt, clk)
// Calculated: CPTAG

`DEFINE_REG_REG_NXT(CACHE_PHYS_TAG_W, refill_address_cptag, refill_address_cptag_nxt, clk)
`DEFINE_REG_REG_NXT(LANES_W, refill_address_lane, refill_address_lane_nxt, clk)
`DEFINE_REG_REG_NXT(OFFSET_W, refill_address_offset, refill_address_offset_nxt, clk)


`DEFINE_REG_REG_NXT(4, os_cmd, os_cmd_nxt, clk)
`DEFINE_REG_REG_NXT(3, os_load_type, os_load_type_nxt, clk)
`DEFINE_REG_REG_NXT(2, os_store_type, os_store_type_nxt, clk)

`DEFINE_REG_REG_NXT(32, os_store_data, os_store_data_nxt, clk)

`DEFINE_REG_REG_NXT(1, csr_satp_mode_r, csr_satp_mode_r_nxt, clk)
`DEFINE_REG_REG_NXT(22, csr_satp_ppn_r, csr_satp_ppn_r_nxt, clk)


`ifdef DEBUG_CACHE
    /*verilator coverage_off*/
    reg [7*8-1:0] c_cmd_ascii;
    always @* begin
        case(c_cmd)
            `CACHE_CMD_LOAD:                c_cmd_ascii = "LOAD";
            `CACHE_CMD_EXECUTE:             c_cmd_ascii = "EXECUTE";
            `CACHE_CMD_STORE:               c_cmd_ascii = "STORE";
            `CACHE_CMD_FLUSH_ALL:           c_cmd_ascii = "FLUSH";
            `CACHE_CMD_ABORT:               c_cmd_ascii = "ABORT";
            `CACHE_CMD_LOAD_RESERVE:        c_cmd_ascii = "LR";
            `CACHE_CMD_STORE_CONDITIONAL:   c_cmd_ascii = "SC";
            // TODO: Add commands
            default:                        c_cmd_ascii = "UNKNOWN";
        endcase
    end

    reg [7*8-1:0] os_cmd_ascii;
    always @* begin
        case(os_cmd)
            `CACHE_CMD_LOAD:                os_cmd_ascii = "LOAD";
            `CACHE_CMD_EXECUTE:             os_cmd_ascii = "EXECUTE";
            `CACHE_CMD_STORE:               os_cmd_ascii = "STORE";
            `CACHE_CMD_FLUSH_ALL:           os_cmd_ascii = "FLUSH";
            `CACHE_CMD_ABORT:               os_cmd_ascii = "ABORT";
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
            `CACHE_RESPONSE_IDLE:        c_response_ascii = "IDLE";
            `CACHE_RESPONSE_DONE:        c_response_ascii = "DONE";
            `CACHE_RESPONSE_WAIT:        c_response_ascii = "WAIT";
            `CACHE_RESPONSE_MISSALIGNED: c_response_ascii = "MISSALIGNED";
            `CACHE_RESPONSE_PAGEFAULT:   c_response_ascii = "PAGEFAULT";
            `CACHE_RESPONSE_UNKNOWNTYPE: c_response_ascii = "UNKNOWNTYPE";
            `CACHE_RESPONSE_ACCESSFAULT: c_response_ascii = "ACCESSFAULT";
            `CACHE_RESPONSE_ATOMIC_FAIL: c_response_ascii = "ATOMICFAIL";
            default:                     c_response_ascii = "???????????";
        endcase
    end
    /*verilator coverage_on*/
`endif


// |------------------------------------------------|
// |                                                |
// |              Signals                           |
// |                                                |
// |------------------------------------------------|


// TODO: correctly handle vm_enabled csr_satp_mode_r
reg [1:0] vm_privilege;
reg vm_enabled;

wire c_cmd_access_request, c_cmd_abort;
wire os_cmd_atomic, os_cmd_write;


wire [VIRT_TAG_W-1:0] 	    c_address_vtag          = c_address[31:32-VIRT_TAG_W];
generate
    if(LANES_W != 6)
        wire [6-LANES_W-1:0]	    c_address_cptag_low     = c_address[32-VIRT_TAG_W-1:INWORD_OFFSET_W+OFFSET_W+LANES_W];
endgenerate

wire [LANES_W-1:0]	        c_address_lane          = c_address[INWORD_OFFSET_W+OFFSET_W+LANES_W-1:INWORD_OFFSET_W+OFFSET_W];
wire [OFFSET_W-1:0]			c_address_offset        = c_address[INWORD_OFFSET_W+OFFSET_W-1:INWORD_OFFSET_W];
wire [1:0]			        c_address_inword_offset = c_address[INWORD_OFFSET_W-1:0];
// TODO: The registers for OS

reg                         stall; // Output stage stalls input stage
reg                         unknowntype;
reg                         missaligned;
wire                        pagefault;


reg  [CACHE_PHYS_TAG_W-1:0] cptag;

// TODO: Calculate CPTAG

reg  [WAYS-1:0]             way_hit;
reg  [WAYS_W-1:0]           os_cache_hit_way;
reg                         os_cache_hit;
reg  [31:0]                 os_readdata;






// PTAG port
reg  [LANES_W-1:0]      cptag_lane;
reg                     cptag_read;
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

//                      lanestate read port
reg                     lanestate_read;
reg  [LANES_W-1:0]      lanestate_lane;
wire [WAYS-1:0]         lanestate_readdata;
//                      lanestate write port
reg  [WAYS-1:0]         lanestate_write;
reg                     lanestate_writedata;


genvar way_num;
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
    
    armleocpu_mem_1rw #(
        .ELEMENTS_W(LANES_W),
        .WIDTH(1)
    ) lanestatestorage (
        .clk(clk),
        
        .address(lanestate_lane),

        .read(lanestate_read),
        .readdata(lanestate_readdata[way_num]),

        .write(lanestate_write[way_num]),
        .writedata(lanestate_writedata)
    );

    for(byte_offset = 0; byte_offset < 32; byte_offset = byte_offset + 8) begin : storage_generate_for
        armleocpu_mem_1rw #(
            .ELEMENTS_W(LANES_W+OFFSET_W),
            .WIDTH(8)
        ) datastorage (
            .clk(clk),
            .address({storage_lane, storage_offset}),
            
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
wire [3:0]              storegen_mask;
wire                    storegen_missaligned;
wire                    storegen_unknowntype;
armleocpu_storegen storegen(
    .inword_offset          (os_address_inword_offset),
    .storegen_type          (os_store_type),

    .storegen_datain        (os_store_data),

    .storegen_dataout       (storegen_dataout),
    .storegen_datamask      (storegen_mask),
    .storegen_missaligned   (storegen_missaligned),
    .storegen_unknowntype   (storegen_unknowntype)
);

/*

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




// Page table walker instance
armleocpu_ptw ptw(
    .clk                        (clk),
    .rst_n                      (rst_n),

    // TODO: AXI Connection
    
    .resolve_request            (ptw_resolve_request),
    .virtual_address            (ptw_resolve_virtual_address), //os_address_vtag

    .resolve_done               (ptw_resolve_done),
    .resolve_pagefault          (ptw_pagefault),
    .resolve_accessfault        (ptw_accessfault),

    .ptw_resolve_metadata       (ptw_resolve_metadata),
    .resolve_physical_address   (ptw_resolve_physical_address),

    .satp_ppn                   (csr_satp_ppn_r)
);
*/

/*

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

[LANES_W-1:6-LANES_W-1]
assign cptag = vm_enabled ? {tlb_read_ptag, } : {2'b00, os_address_vtag, };

*/

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

`DEFINE_REG_REG_NXT(LANES_W+1, reset_lane_counter, reset_lane_counter_nxt, clk)


always @* begin : cache_comb
    integer i;

    // Core output
    c_response = `CACHE_RESPONSE_IDLE;
    c_reset_done = 1;
    // c_load_data = loadgen_dataout

    axi_awvalid = 0;
    axi_awaddr = {cptag, os_address_lane, os_address_offset, os_address_inword_offset};
    axi_awlock = 0;
    axi_awprot = {IS_INSTURCTION_CACHE, vm_privilege > `ARMLEOCPU_PRIVILEGE_SUPERVISOR, vm_privilege > `ARMLEOCPU_PRIVILEGE_USER};
    
    axi_wvalid = 0;
    axi_wdata = storegen_dataout;
    axi_wstrb = storegen_mask;
    axi_wlast = 0;

    axi_bready = 0;

    axi_arvalid = 0;
    axi_araddr = {cptag, os_address_lane, os_address_offset, os_address_inword_offset};
    axi_arlen = 1;
    axi_arburst = AXI_BURST_INCR;
    axi_arlock = 0;
    axi_arprot = axi_awprot;

    axi_rready = 0;


    state_nxt = state;
    return_state_nxt = return_state;
    aborted_nxt = aborted;
    victim_way_nxt = victim_way;

    os_active_nxt = os_active;
    refill_error_nxt = refill_error;
    refill_error_type_nxt = refill_error_type;
    os_address_vtag_nxt = os_address_vtag;
    os_address_lane_nxt = os_address_lane;
    os_address_offset_nxt = os_address_offset;
    os_address_inword_offset_nxt = os_address_inword_offset;

    refill_address_cptag_nxt = refill_address_cptag;
    refill_address_lane_nxt = refill_address_lane;
    refill_address_offset_nxt = refill_address_offset;

    os_cmd_nxt = os_cmd;
    os_load_type_nxt = os_load_type;
    os_store_type_nxt = os_store_type;

    os_store_data_nxt = os_store_data;
    csr_satp_mode_r_nxt = csr_satp_mode_r;
    csr_satp_ppn_r_nxt = csr_satp_ppn_r;

    

    vm_privilege = ((csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) && csr_mstatus_mprv) ? csr_mstatus_mpp : csr_mcurrent_privilege;
    vm_enabled = (vm_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR || vm_privilege == `ARMLEOCPU_PRIVILEGE_USER) && csr_satp_mode_r;
    
    c_cmd_access_request =   (c_cmd == `CACHE_CMD_EXECUTE) ||
                        (c_cmd == `CACHE_CMD_LOAD) ||
                        (c_cmd == `CACHE_CMD_STORE) ||
                        (c_cmd == `CACHE_CMD_LOAD_RESERVE) ||
                        (c_cmd == `CACHE_CMD_STORE_CONDITIONAL);
    c_cmd_abort = c_cmd == `CACHE_CMD_ABORT;

    os_cmd_write = (os_cmd == `CACHE_CMD_STORE) || (os_cmd == `CACHE_CMD_STORE_CONDITIONAL);
    os_cmd_atomic = (os_cmd == `CACHE_CMD_LOAD_RESERVE) || (os_cmd == `CACHE_CMD_STORE_CONDITIONAL);
    
    stall = 1;
    // TODO: Add atomic instructions
    if(os_cmd == `CACHE_CMD_LOAD) begin
        unknowntype = loadgen_unknowntype;
        missaligned = loadgen_missaligned;
    end else begin
        unknowntype = storegen_unknowntype;
        missaligned = storegen_missaligned;
    end
    tlb_command = `TLB_CMD_NONE;

    generate
        if(LANES_W != 6) begin
            cptag = vm_enabled ?
                {tlb_read_ptag, os_address_cptag_low}
                : {2'b00, os_address_vtag, os_address_cptag_low};
        end else begin
            cptag = vm_enabled ?
                tlb_read_ptag 
                : {2'b00, os_address_vtag};
        end
    endgenerate

    for(i = 0; i < WAYS; i = i + 1) begin
        ptag_read = 1'b0;
        ptag_readlane = c_address_lane;
        ptag_write[i] = 1'b0;

        lanestate_read = 1'b0;
        lanestate_lane = c_address_lane;
        lanestate_write[i] = 1'b0;

        storage_read = 1'b0;
        storage_lane = refill_address_lane; // TODO: Replace for ACTIVE
        storage_offset = refill_address_offset; // TODO: Replace for ACTIVE
        storage_write[i] = 1'b0;
        storage_writedata = axi_rdata;
        storage_byteenable = 1;
    end
    
    lanestate_writedata = 2'b11; // valid and dirty

    ptag_writedata = ptag;
    ptag_writelane = os_address_lane;
    ptw_resolve_request = 1'b0;
    ptw_resolve_vtag = os_address_vtag;
    loadgen_datain = os_readdata; // For bypassed read this is registered axi_rdata
    
    /*
    tlb_resolve_virtual_address = c_address_vtag;
    tlb_write_vtag = os_address_vtag;
    tlb_write_accesstag = ptw_resolve_access_bits;
    tlb_write_ptag = ptw_resolve_phystag;
    
    // used by state reset
    if(reset_lane_counter == LANES-1) begin
        reset_valid_reset_done = 1'b1;
    end else begin
        reset_valid_reset_done = 1'b0;
    end
    */

    reset_lane_counter_nxt = reset_lane_counter;
    



    if(!rst_n) begin
        state_nxt = STATE_RESET;
        reset_lane_counter_nxt = 0;
        os_active_nxt = 0;
        victim_way_nxt = 0;
    end else begin
        case (state)
            STATE_RESET: begin
                c_reset_done = 0;
                for(i = 0; i < WAYS; i = i + 1)
                    lanestate_write[i] = 1'b1;
                lanestate_writedata = 0;
                lanestate_lane = reset_lane_counter;
                reset_lane_counter_nxt = reset_lane_counter + 1;
                if(reset_lane_counter == LANES-1) begin
                    state_nxt = STATE_ACTIVE;
                end
                // TLB Invalidate all
                tlb_command = `TLB_CMD_INVALIDATE_ALL;
                
                c_response = `CACHE_RESPONSE_WAIT;
                stall = 1;

                csr_satp_mode_r_nxt = csr_satp_mode;
                csr_satp_ppn_r_nxt = csr_satp_ppn;
            end
            STATE_ACTIVE: begin
                stall = 0;
                if(os_active) begin
                    if(refill_error) begin
                        // Returned from refill and error
                        c_response =
                            refill_error_type == `CACHE_ERROR_ACCESSFAULT ?
                                `CACHE_RESPONSE_ACCESSFAULT : `CACHE_RESPONSE_PAGEFAULT;
                    end else if(ptw_error) begin
                        c_response =
                            ptw_error_type == `CACHE_ERROR_ACCESSFAULT ?
                                `CACHE_RESPONSE_ACCESSFAULT : `CACHE_RESPONSE_PAGEFAULT;
                    end else if(unknowntype) begin
                        c_response = `CACHE_RESPONSE_UNKNOWNTYPE;
                    end else if(missaligned) begin
                        c_response = `CACHE_RESPONSE_MISSALIGNED;
                    end else if(vm_enabled && !tlb_hit) begin
                        // TLB Miss
                        stall = 1;
                        c_response = `CACHE_RESPONSE_WAIT;
                        state_nxt = STATE_PTW;
                        // PTW for os_address_cptag, os_address_lane, os_address_offset
                    end else if(vm_enabled && pagefault) begin
                        c_response = `CACHE_RESPONSE_PAGEFAULT;
                    end else if((!vm_enabled) || (vm_enabled && tlb_hit)) begin
                        // If physical address
                        // Or if atomic write or atomic read
                        // Or if write
                        if(!cptag[CACHE_PHYS_TAG_W-1] ||
                            os_cmd_write ||
                            os_cmd_atomic) begin // TODO: Bypass case
                                axi_awlock = os_cmd_atomic;
                                axi_arlock = os_cmd_atomic;
                                
                        end else begin
                            if(os_cache_hit) begin
                                os_active_nxt = 0;
                                // TODO: Implement
                            end else begin
                                // Cache Miss
                                state_nxt = STATE_REFILL;
                                refill_address_cptag_nxt = cptag;
                                refill_address_lane_nxt = os_address_lane
                                refill_address_offset_nxt = os_address_offset;
                                stall = 1;
                            end // not cache hit
                            // For everything else use cached data
                        end // Not bypassed
                    end // vm + tlb hit / no vm
                end // OS_ACTIVE
            end // STATE_ACTIVE
            STATE_PTW: begin
                // TODO:
                if(ptw_resolve_done) begin
                    state_nxt = STATE_ACTIVE;
                    if(ptw_accessfault) begin
                        
                    end else if(ptw_pagefault) begin
                        
                    end else begin

                    end
            end // STATE_PTW
            STATE_REFILL: begin
                // TODO:
            end // STATE_REFILL
        endcase
        if(!stall) begin
            if(c_cmd_access_request) begin
                os_active_nxt = 1;
                os_address_vtag_nxt = c_address_vtag;
                os_address_cptag_low_nxt = c_address_cptag_low;
                os_address_lane_nxt = c_address_lane;
                os_address_offset_nxt = c_address_offset;
                os_address_inword_offset_nxt = c_address_inword_offset;

                os_cmd_nxt = c_cmd;
                os_load_type_nxt = c_load_type;
                os_store_type_nxt = c_store_type;
                os_store_data_nxt = c_store_data;
            end
        end
    end

end
