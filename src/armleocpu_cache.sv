module armleocpu_cache(
    input                   clk,
    input                   rst_n,

    //                      CACHE <-> EXECUTE/MEMORY
    input  [31:0]           c_address,
    output logic            c_wait,
    output logic            c_pagefault,
    output logic            c_accessfault,
    output logic            c_done,

    input                   c_execute, // load is for further execution, used by fetch

    input                   c_load,
    input  [2:0]            c_load_type, // enum defined in armleocpu_defs
    output logic [31:0]     c_load_data,
    output logic            c_load_unknowntype,
    output logic            c_load_missaligned,

    input                   c_store,
    input [1:0]             c_store_type, // enum defined in armleocpu_defs
    input [31:0]            c_store_data,
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
            STATE_PTW = 4'd4;

// Used by refill to wait for ptag to read before reading memory at address depending on ptag_readdata;
reg refill_initial_ptagread_done;
// Used by flush to wait for storage to read first word before writing it to memory;
reg flush_initial_storageread_done;
reg flush_initial_ptagread_done;


logic [WAYS_W-1:0] current_way;
logic [LANES_W-1:0] current_lane;

// |------------------------------------------------|
// |                                                |
// |              Cache Ptag storage                |
// |                    Data Storage                |
// |                    Tag bits storage            |
// |                                                |
// |------------------------------------------------|

genvar way_num;

wire access = (state == STATE_IDLE) && !c_flush && (c_load || c_store) && !c_wait;
wire ptw_complete = (state == STATE_PTW) && ptw_resolve_done && !ptw_pagefault && !ptw_accessfault;
logic s_bypass;



// Valid, dirty storage
reg	[LANES-1:0]     valid               [WAYS-1:0];
reg	[LANES-1:0]     dirty               [WAYS-1:0];


// Storage read port mux
// Storage read is done in STATE_IDLE(when request is just accepted) and STATE_FLUSH (which writes data back to memory)
reg                 storage_read        [WAYS-1:0];
reg  [LANES_W-1:0]  storage_readlane    [WAYS-1:0];
reg  [OFFSET_W-1:0] storage_readoffset  [WAYS-1:0];
wire [31:0]         storage_readdata    [WAYS-1:0];

always @* begin
    for(i = 0; i < WAYS; i = i + 1) begin
        storage_read[i] = access;
        storage_readlane[i] = c_address_lane;
        storage_readoffset[i] = c_address_offset;
    end
    if(state == STATE_FLUSH) begin
        storage_read[current_way] = (!flush_initial_storageread_done) || (!m_waitrequest && m_readdatavalid);
        storage_readlane[current_way] = os_address_lane;
        storage_readoffset[current_way] = os_address_offset;
    end
end

// Storage write port
// Storage is written in idle state (when request is in output stage) and when refilling
reg                 storage_write       [WAYS-1:0];
reg  [31:0]         storage_writedata   [WAYS-1:0];

integer k;

always @* begin
    for(i = 0; i < WAYS; i = i + 1) begin
        storage_write[i] = (state == STATE_IDLE && os_active && os_store);
        for(k = 0; k < 4; k = k + 1)
            storage_writedata[i][((k+1)*8)-1:((k)*8)] = storegen_mask[k] ? storegen_dataout[k] : storage_readdata[i][((k+1)*8)-1:((k)*8)];
    end
    if(state == STATE_REFILL) begin
        storage_write[current_way] = (refill_initial_ptagread_done) && (!m_waitrequest && m_readdatavalid);
        storage_writedata[current_way] = m_readdata;
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

        .writeaddress({os_address_lane, os_address_offset}),
        .write(storage_write[way_num]),
        .writedata(storage_writedata[way_num])
    );
end


// PTAG Storage read port
// PTAG is read when access request comes
// PTAG is read when flush begins
// PTAG is read when refill begins

reg  [LANES_W-1:0]  ptag_readlane       [WAYS-1:0];
reg                 ptag_read           [WAYS-1:0];

always @* begin
    for(i = 0; i < WAYS; i = i + 1) begin
        ptag_readlane[i]                = c_address_lane;
        ptag_read[i]                    = access;
    end
    if(state == STATE_FLUSH) begin
        ptag_readlane[current_way]  = os_address_lane;
        ptag_read[current_way]      = !flush_initial_ptagread_done;
    end else if(state == STATE_REFILL) begin
        ptag_readlane[current_way]  = os_address_lane;
        ptag_read[current_way]      = !refill_initial_ptagread_done;
    end
end
wire [PHYS_W-1:0]   ptag_readdata       [WAYS-1:0];


// PTAG Write port
// PTAG is written only when PTW is done and no pagefault or accessfault
reg                 ptag_write          [WAYS-1:0];

for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin
    always @* begin
        ptag_write[way_num] = 0;
        if(way_num == current_way) begin
            ptag_write[way_num] = ptw_complete;
        end
    end
    mem_1w1r #(
        .ELEMENTS_W(LANES_W),
        .WIDTH(PHYS_W)
    ) ptag_storage (
        .clk(clk),
        
        .readaddress(ptag_readlane[way_num]),
        .read(ptag_read[way_num]),
        .readdata(ptag_readdata[way_num]),

        .writeaddress(os_address_lane),
        .write(ptag_write[way_num]),
        .writedata(ptw_resolve_phystag)
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
reg                         os_execute;

reg                         os_store;
reg [1:0]                   os_store_type;
reg [31:0]                  os_store_data;

reg [OFFSET_W-1:0]          os_word_counter;

logic [VIRT_W-1:0]          os_address_vtag; // used by refill, flush, ptw

logic [31:0]                os_readdata;

logic [WAYS-1:0]            os_cache_hit;
logic [WAYS_W-1:0]          os_cache_hit_way;
logic                       os_cache_hit_any;

always @* begin
    integer way_num;
    os_cache_hit_any = 0;
    for(way_num = WAYS-1; way_num >= 0; way_num = way_num - 1) begin
        os_cache_hit[way_num] = os_valid[way_num] && ptag_readdata[way_num] == tlb_ptag_read;
        if(os_cache_hit[way_num]) begin
            os_cache_hit_way = way_num;
            os_readdata = storage_readdata[way_num];
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

    .LoadGenDataIn      (s_bypass ? m_readdata : os_readdata),

    .LoadGenDataOut     (c_load_data),
    .LoadMissaligned    (c_load_missaligned),
    .LoadUnknownType    (c_load_unknowntype)
);

// |------------------------------------------------|
// |                                                |
// |                 StoreGen                       |
// |                                                |
// |------------------------------------------------|

// Outputs
logic [31:0]    storegen_dataout;
logic [3:0]     storegen_mask;

armleocpu_storegen storegen(
    .inwordOffset           (os_address_inword_offset),
    .storegenType           (os_store_type),

    .storegenDataIn         (os_store_data),

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

logic                   tlb_write = ptw_complete;
wire                    tlb_done;
wire                    tlb_miss;
wire    [21:0]          tlb_ptag_read;
wire    [7:0]           tlb_accesstag_read;

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
    .resolve            (access),
    
    .miss               (tlb_miss),
    .done               (tlb_done),
    
    // resolve result for virt
    .accesstag_r        (tlb_accesstag_read),
    .phys_r             (tlb_ptag_read),
    
    // write for for entry virt
    .write              (tlb_write),
    // where to write
    .virtual_address_w  (os_address_vtag),
    // access tag
    .accesstag_w        (ptw_resolve_access_bits),
    // and phys
    .phys_w             (ptw_resolve_phystag)
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
    .virtual_address    (os_address_vtag),

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
// Flush (write port)
// Refill (read port)
// PTW (read port)
// Bypass (read and write port)

always @* begin
    // TODO:
    m_address = {ptag_readdata, }; // default: flush write address
    m_burstcount = 16;
    m_read = 0;

    m_write = 0;
    m_writedata = storage_readdata[current_way];// flush write data
    m_byteenable = 4'b1111;

    case(state)
        STATE_IDLE: begin
            m_address = {2'b00, tlb_ptag_read, os_address_lane, os_address_offset, 2'b00};
            m_burstcount = 1;

            m_read = s_bypass && os_load && !bypass_load_handshaked;
            
            m_write = s_bypass && os_store;
            m_writedata = storegen_dataout;
            m_byteenable = storegen_mask;
        end

        STATE_FLUSH: begin

            m_write = ;
        end
        STATE_REFILL: begin
            m_address = {ptag_readdata[current_way], os_address_lane, os_word_counter, 2'b00};// TODO: Same as flush write address

            m_read = refill_initial_ptagread_done; // TODO

            m_write = 0;
        end
        STATE_PTW: begin
            m_address = ptw_avl_address;
            m_burstcount = 1;

            m_read = ptw_avl_read;
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

    // tlb_ptag_write = ptw_resolve_

    
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

    s_bypass = 0;

    case(state)
        STATE_IDLE: begin
            if(c_flush) begin

            end else if(access) begin
                c_wait = 0;
            end
            if(os_active) begin
                if(tlb_done) begin
                    if(!tlb_miss) begin
                        // TLB Hit
                        if(tlb_ptag_read[19]) begin
                            s_bypass = 1;
                        end else begin
                            if(os_cache_hit_any) begin
                                if(c_load) begin

                                end else if(c_store) begin

                                end
                                // Cache hit
                            end else begin
                                // Cache miss
                                c_wait = 1;
                                if(valid[current_way] && dirty[current_way]) begin
                                    
                                end else begin
                                    
                                end
                            end
                        end
                    end else begin
                        // TLB Miss
                        c_wait = 1;
                    end
                end else begin
                    // impossible
                end
            end
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
        // Initial state
        for(i = 0; i < LANES; i = i + 1) begin
            valid[i]    <= 0;
        end
            // Counters
            current_way <= 0;
            current_lane <= 0;
            os_word_counter <= 0;
            initial_flush_all_done <= 0;
            flush_initial_ptagread_done <= 0;
            flush_initial_storageread_done <=0;
            refill_initial_ptagread_done <= 0;
            // State machine
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
            end else if(access) begin
                
                //storage_readdata <= storage[c_address_lane][c_address_offset];
                //ptagstorage_readdata <= ptagstorage[c_address_lane][c_address_offset];

                os_active <= 1;
                os_valid <= valid[c_address_lane];
                os_dirty <= dirty[c_address_lane];

                os_current_way_valid <= valid[current_way];
                os_current_way_dirty <= dirty[current_way];
                
                // Save address composition
                os_address_vtag             <= c_address_vtag;
                os_address_lane             <= c_address_lane;
                os_address_offset           <= c_address_offset;
                os_address_inword_offset    <= c_address_inword_offset;

                os_load                     <= c_load;
                os_load_type                <= c_load_type;
                
                os_store                    <= c_store;
                os_store_type               <= c_store_type;
                os_store_data               <= c_store_data;
                // TODO: Careful, all registers need to be set in this cycle
            end else if(!c_wait) begin
                os_active <= 0;
            end
            if(os_active) begin
                if(tlb_done) begin
                    if(!tlb_miss) begin
                        // TLB Hit
                        if(tlb_ptag_read[19]) begin // 19th bit is 31th bit in address (counting from zero)
                            // if this bit is set, then access is not cached, bypass it
                            // s_bypass = 1; TODO
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
        STATE_PTW: begin
            if(ptw_resolve_done) begin
                state <= STATE_IDLE;
            end
            // TODO: Map memory ports to PTW
            // TODO: Go to idle after PTW completed
        end
        STATE_FLUSH: begin
            if(flush_initial_ptagread_done && flush_initial_storageread_done) begin
                if(!m_waitrequest) begin
                    if(os_word_counter != (2**OFFSET_W)-1) begin
                        os_word_counter <= os_word_counter + 1;
                    end else begin
                        state <= return_state;
                    end
                end
            end else begin
                flush_initial_ptagread_done <= 1;
                flush_initial_storageread_done <= 1;
            end
            
            // First cycle read data from backstorage
            // next cycle write data to backing memory and on success request next data from backstorage
        end
        STATE_FLUSH_ALL: begin
            // TODO: Flush done
            if(!initial_flush_all_done) begin
                current_way <= -1;
                current_lane <= -1;
                initial_flush_all_done <= 1;
                return_state <= STATE_FLUSH_ALL;
            end else begin
                if(current_lane != 2**LANES_W-1) begin
                    current_lane <= current_lane + 1;
                end else begin
                    if(current_way != WAYS-1) begin
                        current_way <= current_way + 1;
                    end else begin
                        current_way <= current_way + 1;
                        state <= STATE_IDLE;
                    end
                    current_lane <= current_lane + 1;
                end
                state <= STATE_FLUSH;
            end
            // Go to state flush for each way and lane that is dirty, then return to state idle after all ways and sets are flushed
        end
        STATE_REFILL: begin
            if(!refill_initial_ptagread_done) begin
                refill_initial_ptagread_done <= 1;
            end else begin
                if(!m_waitrequest)
                    refill_waitrequest_handshaked <= 1;
                if(!m_waitrequest && m_readdatavalid) begin
                    if(os_word_counter != (2**OFFSET_W)-1)
                        os_word_counter <= os_word_counter + 1;
                    else begin
                        state <= STATE_IDLE;
                        os_word_counter <= os_word_counter + 1;
                    end
                end
            end
            // Request ptag
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