module armleocpu_cache(
    input                   clk,
    input                   rst_n,

    //                      CACHE <-> EXECUTE/MEMORY
    input  [31:0]           c_address,
    output logic            c_wait,
    output logic            c_pagefault,
    output logic            c_accessfault,
    output logic            c_done,

    input                   c_load,
    input  [2:0]            c_load_type, // enum defined in armleocpu_defs
    output logic [31:0]     c_load_data,
    output logic            c_load_unknowntype,
    output logic            c_load_missaligned,

    input                   c_store,
    input        [1:0]      c_store_type, // enum defined in armleocpu_defs
    input         [31:0]    c_store_data,
    output logic            c_store_unknowntype,
    output logic            c_store_missaligned,
    
    input                   c_flush,
    output logic            c_flushing,
    output logic            c_flush_done,
    
    `ifdef DEBUG
        output logic        c_miss,
    `endif


    //                      CACHE <-> CSR
    input                   csr_matp_mode, // Mode = 0 -> physical access, 1 -> ppn valid
    input        [21:0]     csr_matp_ppn,
    
    //                      CACHE <-> MEMORY
    output logic [33:0]     m_address,
    output logic [OFFSET_W:0]m_burstcount,
    input                   m_waitrequest,
    input        [1:0]      m_response,
    
    output logic            m_read,
    input        [31:0]     m_readdata,
    input                   m_readdatavalid,
    
    output logic            m_write,
    output logic [31:0]     m_writedata,
    output logic [3:0]      m_byteenable
    
    `ifdef DEBUG
    , output trace_error

    `endif
);

// |------------------------------------------------|
// |                                                |
// |              Parameters and includes           |
// |                                                |
// |------------------------------------------------|

`include "armleocpu_defs.sv"


parameter WAYS_W = 2;
localparam WAYS = 2**WAYS_W;

localparam LANES_W = 6;
localparam LANES = 2**LANES_W;

localparam PHYS_W = 22;
localparam VIRT_W = 20;

// 4 = 16 words each 32 bit = 64 byte
localparam OFFSET_W = 4;

// |------------------------------------------------|
// |                                                |
// |              Cache State                       |
// |                                                |
// |------------------------------------------------|

logic [3:0] state;
logic [3:0] return_state;
localparam 	STATE_IDLE = 4'd0,
            STATE_FLUSH = 4'd1,
            STATE_REFILL = 4'd2,
            STATE_FLUSH_ALL = 4'd3,
            STATE_PTW = 4'd4,
            STATE_BYPASS = 4'd5;

logic [WAYS_W-1:0] current_way;




// |------------------------------------------------|
// |                                                |
// |              Cache Ptag storage                |
// |                    Data Storage                |
// |                    Tag bits storage            |
// |                                                |
// |------------------------------------------------|

genvar way_num;


// Valid, dirty storage
reg	[LANES-1:0]     valid               [WAYS-1:0];
reg	[LANES-1:0]     dirty               [WAYS-1:0];


// Storage read port mux
reg                 storage_read        [WAYS-1:0];
reg  [LANES_W-1:0]  storage_readlane    [WAYS-1:0];
reg  [OFFSET_W-1:0] storage_readoffset  [WAYS-1:0];
wire [31:0]         storage_readdata    [WAYS-1:0];

always @* begin
    for(i = 0; i < WAYS; i = i + 1) begin
        storage_read[i] = state == STATE_IDLE && !c_flush && (c_load || c_store) && !c_wait;
        storage_readlane[i] = c_address_lane;
        storage_readoffset[i] = c_address_offset;
    end
    if(state == STATE_FLUSH) begin
        storage_read[current_way] = (flush_initial_storageread_done) || (!m_waitrequest && m_readdatavalid);
        storage_readlane[current_way] = os_address_lane;
        storage_readoffset[current_way] = os_address_offset;
    end
end

// Storage write port
reg                 storage_write       [WAYS-1:0];
reg  [LANES_W-1:0]  storage_writelane   [WAYS-1:0];
reg  [OFFSET_W-1:0] storage_writeoffset [WAYS-1:0];
reg  [31:0]         storage_writedata   [WAYS-1:0];



always @* begin
    for(i = 0; i < WAYS; i = i + 1) begin
        storage_write[i] = 0; // TODO
        storage_readlane[i] = c_address_lane;
        storage_readoffset[i] = c_address_offset;
    end
    if(state == STATE_REFILL) begin
        storage_read[current_way] = (refill_initial_ptagread_done) || (!m_waitrequest && m_readdatavalid);
        storage_readlane[current_way] = os_address_lane;
        storage_readoffset[current_way] = os_address_offset;
    end
end

for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin
    mem_1w1r #(
        .ELEMENTS_W(LANES_W+OFFSET_W),
        .WIDTH(32)
    ) datastorage (
        .clk(clk),
        
        .readaddress({storage_readlane[way_num], storage_readoffset[way_num]}),
        .read(storage_read[way_num]),
        .readdata(storage_readdata[way_num]),

        .writeaddress({storage_writelane[way_num], storage_writeoffset[way_num]}),
        .write(storage_write[way_num]),
        .writedata(storage_writedata[way_num])
    );
end


// PTAG Storage read port
reg  [LANES_W-1:0]  ptag_readlane       [WAYS-1:0];
reg                 ptag_read           [WAYS-1:0];
wire [PHYS_W-1:0]   ptag_readdata       [WAYS-1:0];

localparam PTAGREAD_MUX_IDLE = 0;
localparam PTAGREAD_MUX_FLUSH = 1;
localparam PTAGREAD_MUX_REFILL = 2;
logic [3:0] ptagread_mux;


// PTAG Write port
reg  [LANES_W-1:0]  ptag_writelane      [WAYS-1:0];
reg                 ptag_write          [WAYS-1:0];
reg  [PHYS_W-1:0]   ptag_writedata      [WAYS-1:0];

// PTAG Storage write port mux
localparam PTAGWRITE_MUX_IDLE = 0;
localparam PTAGWRITE_MUX_PTW = 3;



// backstorage


for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin
    mem_1w1r #(
        .ELEMENTS_W(LANES_W),
        .WIDTH(PHYS_W)
    ) ptag_storage (
        .clk(clk),
        
        .readaddress(ptag_readlane[way_num]),
        .read(ptag_read[way_num]),
        .readdata(ptag_readdata[way_num]),

        .writeaddress(ptag_writelane[way_num]),
        .write(ptag_write[way_num]),
        .writedata(ptag_writedata[way_num])
    );
end



// |------------------------------------------------|
// |                                                |
// |              Address composition               |
// |                                                |
// |------------------------------------------------|


wire [VIRT_W-1:0] 	        c_address_vtag          = c_address[31:32-VIRT_W]; // Goes to TLB/PTW only
wire [LANES_W-1:0]	        c_address_lane          = c_address[2+OFFSET_W:2+OFFSET_W];
wire [OFFSET_W-1:0]			c_address_offset        = c_address[2+OFFSET_W-1:2];
wire [1:0]			        c_address_inword_offset = c_address[1:0];

// |------------------------------------------------|
// |                                                |
// |              Output stage                      |
// |                                                |
// |------------------------------------------------|
// (see schematic view in docs/Cache.png)
reg                         os_active;

reg [LANES_W-1:0]           os_address_lane;
reg [OFFSET_W-1:0]          os_address_offset;
reg [1:0]                   os_address_inword_offset;

reg [WAYS-1:0]              os_valid;
reg [WAYS-1:0]              os_dirty;

reg                         os_load;
reg [2:0]                   os_load_type;

reg                         os_store;
reg [1:0]                   os_store_type;
reg [31:0]                  os_store_data;

logic [WAYS-1:0]            os_cache_hit                ;
logic [PHYS_W-1:0]          os_ptag           [WAYS-1:0];
logic [WAYS_W-1:0]          os_cache_hit_way            ;
logic                       os_cache_hit_any            ;

always @* begin
    integer way_num;
    os_cache_hit_any = 0;
    for(way_num = WAYS-1; way_num >= 0; way_num = way_num - 1) begin
        os_ptag[way_num] = ptag_readdata[way_num];
        os_cache_hit[way_num] = os_valid[way_num] && os_ptag[way_num] == tlb_ptag_read;
        if(os_cache_hit[way_num]) begin
            os_cache_hit_way = way_num;
            os_cache_hit_any = 1;
        end
    end
end


// |------------------------------------------------|
// |                                                |
// |                   LoadGen                      |
// |                                                |
// |------------------------------------------------|

armleocpu_loadgen loadgen(
    .inwordOffset       (os_address_inword_offset),
    .loadType           (os_load_type),

    .LoadGenDataIn      (state == STATE_BYPASS ? m_readdata : storage_readdata[os_cache_hit_way]), // TODO:

    .LoadGenDataOut     (c_load_data),
    .LoadMissaligned    (c_load_missaligned),
    .LoadUnknownType    (c_load_unknowntype)
);

// |------------------------------------------------|
// |                                                |
// |                 StoreGen                       |
// |                                                |
// |------------------------------------------------|

// Storegen mux
logic [31:0]    storegen_datain;
logic [1:0]     storegen_inwordOffset;
logic [1:0]     storegen_type;

logic [31:0]    bypass_store_datain;
logic [1:0]     bypass_store_inwordOffset;
logic [1:0]     bypass_store_type;

localparam STOREGEN_MUX_IDLE = STATE_IDLE;
localparam STOREGEN_MUX_BYPASS = STATE_BYPASS;
logic [3:0] storegen_mux = state;

always @* begin
    storegen_type           = os_store_type;
    storegen_datain         = os_store_data;
    storegen_inwordOffset   = os_address_inword_offset;
    case(storegen_mux)
        STOREGEN_MUX_IDLE: begin

        end
        STOREGEN_MUX_BYPASS: begin
            storegen_type           = bypass_store_type;
            storegen_inwordOffset   = bypass_store_inwordOffset;
            storegen_datain         = bypass_store_datain;
        end
    endcase
end

// Outputs
logic [31:0]    storegen_dataout;
logic [3:0]     storegen_mask;


//os_store_data
//os_store_type
//os_address_inword_offset
armleocpu_storegen storegen(
    .inwordOffset           (storegen_inwordOffset),
    .storegenType           (storegen_type),

    .storegenDataIn         (storegen_datain),

    .storegenDataOut        (storegen_dataout),
    .storegenDataMask       (storegen_mask),
    .storegenMissAligned    (c_store_missaligned),
    .storegenUnknownType    (c_store_unknowntype)
);

// |------------------------------------------------|
// |                                                |
// |         Translation Lookaside buffer           |
// |                                                |
// |------------------------------------------------|

logic                   tlb_resolve;
logic                   tlb_write;
logic                   tlb_done;
logic                   tlb_miss;
logic   [19:0]          tlb_write_vtag;
logic   [21:0]          tlb_ptag_read;
logic   [21:0]          tlb_ptag_write;
logic   [7:0]           tlb_accesstag_write;
logic   [7:0]           tlb_accesstag_read;

armleocpu_tlb tlb(
    .rst_n              (rst_n),
    .clk                (clk),
    
    .enable             (csr_matp_mode),
    .virtual_address    (c_address_vtag),
    // For flush request it's safe
    // to invalidate all tlb because
    // cache keeps track of access validity
    // and uses physical tagging
    .invalidate         (c_flush),
    .resolve            (!(c_flush || flush_pending) && state == STATE_IDLE && (c_load || c_store)),
    
    .miss               (tlb_miss),
    .done               (tlb_done),
    
    // resolve result for virt
    .accesstag_r        (tlb_accesstag_read),
    .phys_r             (tlb_ptag_read),
    
    // write for for entry virt
    .write              (tlb_write),
    // where to write
    .virtual_address_w  (tlb_write_vtag),
    // access tag
    .accesstag_w        (tlb_accesstag_write),
    // and phys
    .phys_w             (tlb_ptag_write)
);

// |------------------------------------------------|
// |                                                |
// |             Page Table Walker                  |
// |                                                |
// |------------------------------------------------|

logic                   ptw_resolve_request;
logic                   ptw_resolve_ack;

logic                   ptw_resolve_done;
logic                   ptw_pagefault;
logic                   ptw_accessfault;


logic [7:0]             ptw_resolve_access_bits;
logic [PHYS_W-1:0]      ptw_resolve_phystag;


logic                   ptw_avl_read;
logic [33:0]            ptw_avl_address;

// Page table walker
armleocpu_ptw ptw(
    .clk                (clk),
    .rst_n              (rst_n),

    .avl_address        (ptw_avl_address),
    .avl_read           (ptw_avl_read),
    .avl_readdata       (m_readdata),
    .avl_readdatavalid  (m_readdatavalid),
    .avl_waitrequest    (m_waitrequest),
    .avl_response       (m_response),

    .resolve_request    (ptw_resolve_request),
    .resolve_ack        (ptw_resolve_ack),
    .virtual_address    (0), // TODO

    .resolve_done       (ptw_resolve_done),
    .resolve_pagefault  (ptw_pagefault),
    .resolve_accessfault(ptw_accessfault),

    .resolve_access_bits(ptw_resolve_access_bits),
    .resolve_physical_address(ptw_resolve_phystag),

    .matp_mode          (csr_matp_mode),
    .matp_ppn           (csr_matp_ppn)

    `ifdef DEBUG
    , .state_debug_output()
    `endif
);

// Memory mux
localparam M_MUX_FLUSH = STATE_FLUSH;
localparam M_MUX_REFILL = STATE_REFILL;
localparam M_MUX_PTW = STATE_PTW;
localparam M_MUX_BYPASS = STATE_BYPASS;
logic [3:0] m_mux = state;

always @* begin
    //m_address = {ptag_readdata, };
    m_burstcount = 16;
    m_read = 0;

    m_write = 0;
    m_writedata = ;
    m_byteenable = 4'b1111;

    case(m_mux)
        M_MUX_FLUSH: begin
            m_address = ;
            m_burstcount = 16;

            m_read = 0;

            m_write = ;
            m_writedata = ;
            m_byteenable = 4'b1111;
        end
        M_MUX_REFILL: begin
            m_address = ;// TODO
            m_burstcount = 16;

            m_read = ; // TODO

            m_write = 0;
            m_writedata = 0;
            m_byteenable = 4'b1111;
        end
        M_MUX_PTW: begin
            m_address = ptw_avl_address;
            m_burstcount = 1;

            m_read = ptw_avl_read;

            m_write = 0;
            m_writedata = 0;
            m_byteenable = 4'b1111;
        end
        M_MUX_BYPASS: begin
            m_address = ;
            m_burstcount = 1;

            m_read = ;
            
            m_write = ;
            m_writedata = storegen_dataout;
            m_byteenable = storegen_mask;
        end
    endcase
end


// |------------------------------------------------|
// |                                                |
// |             always_comb                        |
// |                                                |
// |------------------------------------------------|



integer i;

always @* begin
    // TLB Requests
    tlb_resolve = 0;

    tlb_write = 0;
    tlb_write_vtag = ptw_vtag;
	tlb_accesstag_write = ptw_resolve_access_bits;
    // tlb_ptag_write = ptw_resolve_

    
    for(i = 0; i < WAYS; i = i + 1) begin
        // Storage
        storage_readmux = IDLE;

        storage_read[i] = 0;
        storage_readlane[i] = c_address_lane;
        storage_readoffset[i] = c_address_offset;

        storage_write[i] = 0;
        storage_writelane[i] = os_address_lane_r;
        storage_writeoffset[i] = os_address_offset_r;
        storage_writedata[i] = os_writedata_r;

        // PTAG Default inputs
        ptag_read[i] = 0;
        ptag_readlane[i] = c_address_lane;

        ptag_write[i] = 0;
        ptag_writelane[i] = /*target_lane*/0;
        ptag_writedata[i] = target_refill_ptag;
    end


    // Memory bus
    m_burstcount = 16;
    m_read = 0;
    m_write = 0;
    m_writedata = storage_readdata;
    m_byteenable = 4'b1111;

    // LoadGen and StoreGen
    loadgen_datain = m_readdata;
    // Or storage_readdata?


    // Core
    c_wait = 1;
    c_done = 0;
    c_pagefault = 0;
    c_accessfault = 0;
    c_flushing = 0;
    c_flush_done = 0;
    

    `ifdef DEBUG
    c_miss = 0;
    `endif


    case(state)
        STATE_IDLE: begin
            
        end
    endcase
    
end

// |------------------------------------------------|
// |                                                |
// |             always_ff                          |
// |                                                |
// |------------------------------------------------|


always @(negedge rst_n or posedge clk) begin
    if(!rst_n) begin
        for(i = 0; i < LANES; i = i + 1) begin
            valid[i]    <= 0;
        end
            current_way <= 0;
            state       <= STATE_IDLE;
            os_active   <= 0;
    end else if(clk) begin
        case(state)
        STATE_IDLE: begin
            return_state <= STATE_IDLE;
            if(c_flush) begin
                state <= STATE_FLUSH_ALL;
                os_active <= 0;
                // TODO: init variables for flush
            end else if((c_load || c_store) && !c_wait) begin
                //os_readdata <= storage[c_address_lane][c_address_offset];
                // ptag_r read

                os_active <= 1;
                os_valid <= valid[c_address_lane];
                os_dirty <= dirty[c_address_lane];

                os_current_way_valid <= valid[current_way];
                os_current_way_dirty <= dirty[current_way];
                
                // Save address composition
                
                os_address_lane             <= c_address_lane;
                os_address_offset           <= c_address_offset;
                os_address_inword_offset    <= c_address_inword_offset;

                os_load                     <= c_load;
                os_load_type                <= c_load_type;
                
                os_store                    <= c_store;
                os_store_type               <= c_store_type;
                os_store_data               <= c_store_data;
                // TODO: Careful, all registers need to be set in this cycle
            end else begin
                os_active <= 0;
            end
            if(os_active) begin
                if(tlb_done) begin
                    if(!tlb_miss) begin
                        // TLB Hit
                        if(tlb_ptag_read[19]) begin // 19th bit is 31th bit in address (counting from zero)
                            // if this bit is set, then access is not cached, bypass it
                            state <= STATE_BYPASS;
                            bypass_physaddress <= {tlb_ptag_read, os_address_lane, os_address_offset, os_address_inword_offset};
                            bypass_load <= os_load;


                        end else begin
                            // Else if cached address
                            if(os_cache_hit_any) begin
                                // Cache hit
                                if(c_load) begin
                                    // load data and pass thru load data gen
                                end else if(c_store) begin
                                    // store data
                                end
                            end else begin
                                // Cache miss
                                if(valid[current_way] && dirty[current_way]) begin
                                    state <= STATE_FLUSH;
                                    return_state <= STATE_REFILL;
                                end else begin
                                    state <= STATE_REFILL;
                                    
                                end
                            end
                        end
                    end else begin
                        // TLB Miss
                        state <= STATE_PTW;
                    end
                end else
                    $display("[Cache] TLB WTF 2");
            end
        end
        STATE_BYPASS: begin

        end
        STATE_PTW: begin
            // TODO: Map memory ports to PTW
            // TODO: Go to idle after PTW completed
        end
        STATE_FLUSH: begin
            // First cycle read data from backstorage
            // next cycle write data to backing memory and on success request next data from backstorage
        end
        STATE_FLUSH_ALL: begin
            // Go to state flush for each way and lane that is dirty, then return to state idle after all ways and sets are flushed
        end
        STATE_REFILL: begin
            // Request data from memory
            // If data from memory ready write to datastorage
            // after refilling increment current_way
        end
        default: begin
            $display("[Cache] WTF");
        end
        endcase
    end
end



// Debug outputs
`ifdef DEBUG
reg [(9*8)-1:0] state_str;
always @* begin case(state)
    STATE_IDLE: state_str <= "IDLE";
    STATE_FLUSH: state_str <= "FLUSH";
    STATE_REFILL: state_str <= "REFILL";
    STATE_FLUSH_ALL: state_str <= "FLUSH_ALL";
    STATE_PTW: state_str <= "PTW";
    endcase
end

reg [(9*8)-1:0] return_state_str;
always @* begin case(return_state)
    STATE_IDLE: return_state_str <= "IDLE";
    STATE_FLUSH: return_state_str <= "FLUSH";
    STATE_REFILL: return_state_str <= "REFILL";
    STATE_FLUSH_ALL: return_state_str <= "FLUSH_ALL";
    STATE_PTW: return_state_str <= "PTW";
    endcase
end
`endif


endmodule