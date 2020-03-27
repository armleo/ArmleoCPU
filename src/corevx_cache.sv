module corevx_cache(
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
    input  [2:0]            c_load_type, // enum defined in corevx_defs
    output logic [31:0]     c_load_data,
    output logic            c_load_unknowntype,
    output logic            c_load_missaligned,

    input                   c_store,
    input [1:0]             c_store_type, // enum defined in corevx_defs
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
);

// |------------------------------------------------|
// |                                                |
// |              Parameters and includes           |
// |                                                |
// |------------------------------------------------|

`include "corevx_defs.sv"


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
reg refill_initial_done;
reg refill_waitrequest_handshaked;
// Used by flush to wait for storage to read first word before writing it to memory;
reg flush_initial_done;
// Used by flush_all
reg flush_all_initial_done;


logic [WAYS_W-1:0]          current_way;
logic [WAYS_W-1:0]          current_way_next;

reg                         os_active;

reg [LANES_W-1:0]           os_address_lane;
logic [LANES_W-1:0]         os_address_lane_next;
reg [OFFSET_W-1:0]          os_address_offset;
reg [1:0]                   os_address_inword_offset;

reg [WAYS-1:0]              os_valid; // TODO: check if accessed correctly

reg                         os_load;
reg [2:0]                   os_load_type;
reg                         os_execute;

reg                         os_store;
reg [1:0]                   os_store_type;
reg [31:0]                  os_store_data;

reg [OFFSET_W-1:0]          os_word_counter;
wire [OFFSET_W-1:0]         os_word_counter_next = os_word_counter + 1;

logic [VIRT_W-1:0]          os_address_vtag; // used by refill, flush, ptw

logic [31:0]                os_readdata;

logic                       os_current_way_valid;
logic                       os_current_way_dirty;

logic [WAYS-1:0]            os_cache_hit;
logic [WAYS_W-1:0]          os_cache_hit_way;
logic                       os_cache_hit_any;


// Indicates that m_waitrequest went to zero => m_read can go to zero
logic                       bypass_load_handshaked;

// |------------------------------------------------|
// |                                                |
// |              Address composition               |
// |                                                |
// |------------------------------------------------|


wire [VIRT_W-1:0] 	        c_address_vtag          = c_address[31:32-VIRT_W]; // Goes to TLB/PTW only
wire [LANES_W-1:0]	        c_address_lane          = c_address[2+OFFSET_W+LANES_W-1:2+OFFSET_W];
wire [OFFSET_W-1:0]			c_address_offset        = c_address[2+OFFSET_W-1:2];
wire [1:0]			        c_address_inword_offset = c_address[1:0];


// |------------------------------------------------|
// |                                                |
// |              Cache Ptag storage                |
// |                    Data Storage                |
// |                    Tag bits storage            |
// |                                                |
// |------------------------------------------------|

genvar way_num;

wire access = (state == STATE_IDLE) && !c_flush && (c_load || c_store) && !c_wait;
// Indicates access from cpu
wire ptw_complete = (state == STATE_PTW) && ptw_resolve_done && !ptw_pagefault && !ptw_accessfault;
// inidicates that ptw completed resolve
logic s_bypass;
// wire, Indicates that bypass request is in progress


//                      PTW Vars
logic                   ptw_resolve_request;
logic                   ptw_resolve_ack;

logic                   ptw_resolve_done;
logic                   ptw_pagefault;
logic                   ptw_accessfault;


logic [7:0]             ptw_resolve_access_bits;
logic [PHYS_W-1:0]      ptw_resolve_phystag;


logic                   ptw_avl_read;
logic [33:0]            ptw_avl_address;

//                      Ptag read vars

reg  [LANES_W-1:0]      ptag_readlane       [WAYS-1:0];
reg                     ptag_read           [WAYS-1:0];


//                      Valid, dirty storage vars
reg	[WAYS-1:0]          valid               [LANES-1:0];
reg	[WAYS-1:0]          dirty               [LANES-1:0];

//                      Storage read port vars
reg                     storage_read        [WAYS-1:0];
reg  [LANES_W-1:0]      storage_readlane    [WAYS-1:0];
reg  [OFFSET_W-1:0]     storage_readoffset  [WAYS-1:0];
wire [31:0]             storage_readdata    [WAYS-1:0];

//                      Storage write port vars
reg                     storage_write       [WAYS-1:0];
reg  [31:0]             storage_writedata   [WAYS-1:0];
wire [WAYS-1:0]         storage_isWayHit;

//                      Storegen vars
logic [31:0]            storegen_dataout;
logic [3:0]             storegen_mask;

//                      TLB Vars
wire [PHYS_W-1:0]       ptag_readdata       [WAYS-1:0];
reg                     ptag_write          [WAYS-1:0];
wire                    tlb_write = ptw_complete;
wire                    tlb_done;
wire                    tlb_miss;
wire    [21:0]          tlb_ptag_read;
wire    [7:0]           tlb_accesstag_read;


// Storage read port mux
// Storage read is done in STATE_IDLE(when request is just accepted)
// and STATE_FLUSH on initial cycle and then every successful write (which writes data back to memory)
integer t;

wire flush_storage_read = (state == STATE_FLUSH) && ((!flush_initial_done) || (flush_initial_done && !m_waitrequest));

always @* begin
    for(t = 0; t < WAYS; t = t + 1) begin : storage_read_port_for
        storage_read[t] = access;
        storage_readlane[t] = c_address_lane;
        storage_readoffset[t] = c_address_offset;
    end
    if(state == STATE_FLUSH) begin
        storage_read[current_way] = flush_storage_read;
        storage_readlane[current_way] = os_address_lane_next;
        storage_readoffset[current_way] = os_word_counter_next;
    end
end

// Storage write port
// Storage is written
//      in idle state (when request is in output stage)
//      and when refilling

genvar u;
generate
    for(u = 0; u < WAYS; u = u + 1) begin : storage_isWayHit_for_block
        assign storage_isWayHit[u] = u == os_cache_hit_way;
    end
endgenerate

integer c;
always @* begin
    for(c = 0; c < WAYS; c = c + 1) begin : storage_write_port_for
        storage_write[c] = (storage_isWayHit[c] && os_cache_hit_any && (state == STATE_IDLE) && os_active && os_store);
        storage_writedata[c] = {
            storegen_mask[3] ? storegen_dataout[3] : storage_readdata[c][31:24],
            storegen_mask[2] ? storegen_dataout[2] : storage_readdata[c][23:16],
            storegen_mask[1] ? storegen_dataout[1] : storage_readdata[c][15:8],
            storegen_mask[0] ? storegen_dataout[0] : storage_readdata[c][7:0]
        };
    end
    if(state == STATE_REFILL) begin
        storage_write[current_way] = (refill_initial_done) && (!m_waitrequest && m_readdatavalid);
        storage_writedata[current_way] = m_readdata;
    end
end

`ifdef DEBUG
integer p;
always @(posedge clk) begin
    for(p = 0; p < WAYS; p = p + 1) begin
        if(storage_write[p])
            $display("[t=%d][Cache] storage_write = 1, storage_writedata = 0x%X", $time, storage_writedata[p]);
    end
end
`endif
genvar datastorage_way_counter;

generate
for(datastorage_way_counter = 0; datastorage_way_counter < WAYS; datastorage_way_counter = datastorage_way_counter + 1) begin : datastorage_for
    mem_1w1r #(
        .ELEMENTS_W(LANES_W+OFFSET_W),
        .WIDTH(32)
    ) datastorage (
        .clk(clk),
        
        .readaddress({storage_readlane[datastorage_way_counter], storage_readoffset[datastorage_way_counter]}),
        .read(storage_read[datastorage_way_counter]),
        .readdata(storage_readdata[datastorage_way_counter]),

        .writeaddress({os_address_lane, state == STATE_REFILL ? os_word_counter : os_address_offset}),
        .write(storage_write[datastorage_way_counter]),
        .writedata(storage_writedata[datastorage_way_counter])
    );
end
endgenerate

// PTAG Storage read port
// PTAG is read when access request comes
// PTAG is read when flush begins
// PTAG is read when refill begins

integer o;
logic flush_ptag_read;

always @* begin
    for(o = 0; o < WAYS; o = o + 1) begin
        ptag_readlane[o]                = c_address_lane;
        ptag_read[o]                    = access;
    end
    if(state == STATE_FLUSH) begin
        ptag_readlane[current_way]  = os_address_lane;
        ptag_read[current_way]      = !flush_initial_done;
    end
end


// PTAG Write port
// PTAG is written when PTW is done and no pagefault or accessfault
// PTAG is also written when Refill is done, so if PTW is disabled, ptag will still be valid
generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : ptagstorage_for
    always @* begin
        ptag_write[way_num] = 0;
        if(way_num == current_way) begin
            ptag_write[way_num] = ptw_complete || (!refill_initial_done && state == STATE_REFILL);
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
        .writedata(ptw_complete ? ptw_resolve_phystag : tlb_ptag_read)
    );
end
endgenerate
// |------------------------------------------------|
// |                                                |
// |              Output stage                      |
// |                                                |
// |------------------------------------------------|
// (see schematic view in docs/Cache.png)

always @* begin
    integer way_idx;
    os_cache_hit_any = 0;
    os_readdata = 0;
    os_cache_hit_way = 0;
    for(way_idx = WAYS-1; way_idx >= 0; way_idx = way_idx - 1) begin
        os_cache_hit[way_idx] = os_valid[way_idx] && ptag_readdata[way_idx] == tlb_ptag_read;
        if(os_valid[way_idx] && ptag_readdata[way_idx] == tlb_ptag_read) begin
            os_cache_hit_way = way_idx;
            os_readdata = storage_readdata[way_idx];
            os_cache_hit_any = 1;
        end
    end
end

// |------------------------------------------------|
// |                                                |
// |                   LoadGen                      |
// |                                                |
// |------------------------------------------------|

corevx_loadgen loadgen(
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

corevx_storegen storegen(
    .inwordOffset           (os_address_inword_offset),
    .storegenType           (os_store_type),

    .storegenDataIn         (os_store_data),

    .storegenDataOut        (storegen_dataout),
    .storegenDataMask       (storegen_mask),
    .storegenMissAligned    (c_store_missaligned),
    .storegenUnknownType    (c_store_unknowntype)
);


// Page table walker instance
corevx_ptw ptw(
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
// |------------------------------------------------|
// |                                                |
// |         Translation Lookaside buffer           |
// |                                                |
// |------------------------------------------------|



corevx_tlb tlb(
    .rst_n              (rst_n),
    .clk                (clk),
    
    .enable             (csr_matp_mode),
    .virtual_address    (c_address_vtag),
    // For flush request it's safe
    // to invalidate all tlb because
    // cache keeps track of access validity
    // and uses physical tagging
    .invalidate         (state == STATE_FLUSH_ALL && !flush_all_initial_done),
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



// Memory mux
// Flush (write port)
// Refill (read port)
// PTW (read port)
// Bypass (read and write port)

always @* begin
    // TODO:
    m_address = {ptag_readdata[current_way], os_address_lane, os_word_counter, 2'b00}; // default: flush write address
    m_burstcount = 16;
    m_read = 0;

    m_write = 0;
    m_writedata = storage_readdata[current_way];// flush write data
    m_byteenable = 4'b1111;

    case(state)
        STATE_IDLE: begin
            m_address = {tlb_ptag_read, os_address_lane, os_address_offset, 2'b00};
            m_burstcount = 1;

            m_read = s_bypass && os_load && !bypass_load_handshaked;
            
            m_write = s_bypass && os_store;
            m_writedata = storegen_dataout;
            m_byteenable = storegen_mask;
        end

        STATE_FLUSH: begin
            m_write = flush_initial_done;
            m_writedata = storage_readdata[current_way];
        end
        STATE_REFILL: begin
            m_address = {tlb_ptag_read, os_address_lane, os_word_counter, 2'b00};// TODO: Same as flush write address

            m_read = refill_initial_done && !refill_waitrequest_handshaked; // TODO

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



always @* begin
    // Core
    c_wait = 0;
    c_done = 0;
    c_pagefault = 0;
    c_accessfault = 0;
    //c_flushing = 0;
    c_flush_done = 0;
    

    `ifdef DEBUG
    c_miss = 0;
    `endif

    s_bypass = 0;
    current_way_next = current_way;
    os_address_lane_next = os_address_lane + 1;

    case(state)
        STATE_IDLE: begin
            if(os_active) begin
                if(!tlb_miss) begin
                    if(tlb_ptag_read[19]) begin
                        s_bypass = 1;
                        c_wait = 1;
                        
                        if(os_store) begin
                            if(!m_waitrequest) begin
                                c_wait = 0;
                                c_done = 1;
                            end
                        end else if(os_load) begin
                            if(!m_waitrequest) begin
                                if(m_response != 2'b00) begin
                                    c_done = 1;
                                end
                            end
                            if(!m_waitrequest && m_readdatavalid) begin
                                c_wait = 0;
                                c_done = 1;
                            end
                        end
                        c_accessfault = c_done && m_response != 2'b00;
                    end else begin
                        if(os_cache_hit_any) begin
                            if(os_load) begin

                            end else if(os_store) begin

                            end
                            c_done = 1;
                            // Cache hit
                        end else begin
                            // Cache miss
                            c_wait = 1;
                            if(os_current_way_valid && os_current_way_dirty) begin
                                
                            end else begin
                                
                            end
                        end
                    end
                end else if(tlb_miss) begin
                    c_wait = 1;
                end
                if(c_flush && !c_wait) begin

                end else if(access) begin
                    
                end
            end
        end
        /*
        STATE_FLUSH_ALL: begin
            
            c_wait = 1;
            {current_way_next, os_address_lane_next} = {current_way, os_address_lane} + 1;
            if(os_address_lane == 2**LANES_W-1) begin
                if(current_way == WAYS-1) begin
                    c_flush_done = flush_all_initial_done;
                end
            end
            if(valid[os_address_lane_next][current_way_next] && dirty[os_address_lane_next][current_way_next]) begin
                // Goto state_flush
                
            end else if(valid[os_address_lane_next][current_way_next]) begin
                // invalidate
                if(current_way_next == WAYS-1 && os_address_lane_next == LANES-1) begin
                    c_flush_done = flush_all_initial_done;
                end
            end else begin
                // Nothing to do
                if(current_way_next == WAYS-1 && os_address_lane_next == LANES-1) begin
                    c_flush_done = flush_all_initial_done;
                end
            end
            // Go to state flush for each way and lane that is dirty, then return to state idle after all ways and sets are flushed
        end
        STATE_FLUSH: begin
            c_wait = 1;
        end
        STATE_PTW: begin
            c_wait = 1;
            ptw_resolve_request = 1;
        end
        STATE_REFILL: begin
            c_wait = 1;
        end*/
        default: begin
            c_wait = 1;
        end
    endcase
    
end

// |------------------------------------------------|
// |                                                |
// |             always_ff                          |
// |                                                |
// |------------------------------------------------|
`ifdef DEBUG
task debug_print_request;
begin
    $display("[t=%d][Cache] %s request", $time, c_load ? "load" : "store");
    $display("[t=%d][Cache] c_address_vtag = 0x%X, c_address_lane = 0x%X, c_address_offset = 0x%X", $time, c_address_vtag, c_address_lane, c_address_offset);
    $display("[t=%d][Cache] c_address_inword_offset = 0x%X, type = %s", $time, c_address_inword_offset, 
        c_load && c_load_type == LOAD_BYTE          ? "LOAD_BYTE" : (
        c_load && c_load_type == LOAD_BYTE_UNSIGNED ? "LOAD_BYTE_UNSIGNED" : (
        c_load && c_load_type == LOAD_HALF          ? "LOAD_HALF" : (
        c_load && c_load_type == LOAD_HALF_UNSIGNED ? "LOAD_HALF_UNSIGNED" : (
        c_load && c_load_type == LOAD_WORD          ? "LOAD_WORD" : (
        c_load                                      ? "unknown load" : (
        c_store && c_store_type == STORE_BYTE ? "STORE_BYTE": (
        c_store && c_store_type == STORE_HALF ? "STORE_HALF": (
        c_store && c_store_type == STORE_WORD ? "STORE_WORD": (
        c_store ? "unknown store": (
            "unknown"
        )))))))))));
    //$display("[t=%d][Cache] TLB Request", $time);// TODO:
    //$display("[t=%d][Cache] access read request", $time);// TODO:
    
end
endtask


task debug_print_way_selector;
begin
    integer way_idx;
    $display("[t=%d][Cache/OS] way_selector_debug: ", $time);
    $display("[t=%d][Cache/OS] os_cache_hit_any = 0x%X, os_cache_hit_way = 0x%X, os_readdata = 0x%X, tlb_ptag_read = 0x%X",
               $time,          os_cache_hit_any,        os_cache_hit_way,        os_readdata,        tlb_ptag_read);
    for(way_idx = WAYS-1; way_idx >= 0; way_idx = way_idx - 1) begin
        $display("[t=%d][Cache/OS] way_idx = 0x%X, os_valid[way_idx] = 0x%X, ptag_readdata[way_idx] = 0x%X, os_cache_hit[way_idx] = 0x%X",
                   $time,          way_idx,        os_valid[way_idx],        ptag_readdata[way_idx],        os_cache_hit[way_idx]);
    end
end
endtask
`endif


integer i;

integer way_counter;
always @(posedge clk or negedge rst_n) begin
    if(!rst_n) begin
        // Initial state
        for(i = 0; i < 2**LANES_W; i = i + 1) begin
            valid[i] <= 0;
        end
        // Counters
        current_way <= 0;
        os_word_counter <= 0;
        flush_all_initial_done <= 0;
        flush_initial_done <= 0;
        refill_initial_done <= 0;
        refill_waitrequest_handshaked <= 0;
        bypass_load_handshaked <= 0;
        // State machine
        state       <= STATE_IDLE;
        os_active   <= 0;
    end else if(clk) begin
        case(state)
        STATE_IDLE: begin
            return_state <= STATE_IDLE;
            
            if(c_flush && !c_wait) begin
                state <= STATE_FLUSH_ALL;
                current_way <= -1;
                os_address_lane <= -1;
                c_flushing <= 1;
                os_active <= 0;
                `ifdef DEBUG
                $display("[t=%d][Cache] Going to flush_all", $time);
                `endif
                // TODO: init variables for flush
            end else if(access) begin
                `ifdef DEBUG
                $display("[t=%d][Cache] Access request", $time);
                debug_print_request;
                `endif
                //storage_readdata <= storage[c_address_lane][c_address_offset];
                //ptagstorage_readdata <= ptagstorage[c_address_lane][c_address_offset];

                os_active <= 1;
                os_valid <= valid[c_address_lane];
                //os_dirty <= dirty[c_address_lane];

                os_current_way_valid <= valid[c_address_lane][current_way];
                os_current_way_dirty <= dirty[c_address_lane][current_way];
                
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

                bypass_load_handshaked      <= 0;
                // TODO: Careful, all registers need to be set in this cycle
            end else if(!c_wait) begin
                os_active <= 0;
            end

            if(os_active) begin
                `ifdef DEBUG
                $display("[t=%d][Cache/OS] Output stage active", $time);
                `endif
                    
                if(!tlb_miss) begin
                    // TLB Hit
                    if(tlb_ptag_read[19]) begin // 19th bit is 31th bit in address (counting from zero)
                        
                        // if this bit is set, then access is not cached, bypass it
                        // s_bypass = 1; TODO
                        if(os_store) begin
                            if(!m_waitrequest) begin
                                if(m_response != 2'b00) begin
                                    `ifdef DEBUG
                                    $display("[t=%d][Cache/OS] TLB Hit, bypass store failed because of m_response = 0b%b, m_address = 0x%X", $time, m_response, m_address);
                                    `endif
                                end else begin
                                    `ifdef DEBUG
                                    $display("[t=%d][Cache/OS] TLB Hit, bypass stored m_writedata = 0x%X, m_address = 0x%X", $time, m_writedata, m_address);
                                    `endif
                                end
                            end
                        end else if(os_load) begin
                            if(!m_waitrequest) begin
                                if(m_response != 2'b00) begin
                                    `ifdef DEBUG
                                    $display("[t=%d][Cache/OS] TLB Hit, bypass load failed because of m_response = 0b%b, m_address = 0x%X", $time, m_response, m_address);
                                    `endif
                                end else begin
                                    bypass_load_handshaked <= 1;
                                    `ifdef DEBUG
                                    $display("[t=%d][Cache/OS] TLB Hit, bypass load handshaked m_address = 0x%X", $time, m_address);
                                    `endif
                                end
                            end
                            if(!m_waitrequest && m_readdatavalid) begin
                                if(m_response != 2'b00) begin
                                    `ifdef DEBUG
                                    $display("[t=%d][Cache/OS] TLB Hit, bypass load failed because of m_response = 0b%b, m_address = 0x%X", $time, m_response, m_address);
                                    `endif
                                end else begin
                                    `ifdef DEBUG
                                    $display("[t=%d][Cache/OS] TLB Hit, bypass load m_readdata = 0x%X, m_address = 0x%X", $time, m_readdata, m_address);
                                    `endif
                                end
                                bypass_load_handshaked <= 0;
                            end
                        end
                    end else if(!tlb_ptag_read[19]) begin
                        `ifdef DEBUG
                        debug_print_way_selector;
                        `endif
                        // Else if cached address
                        if(os_cache_hit_any) begin
                            
                            // Cache hit
                            if(os_load) begin
                                `ifdef DEBUG
                                // TODO: write what data was loaded
                                // load data and pass thru load data gen
                                $display("[t=%d][Cache/OS] TLB Hit, Cache hit, load", $time);
                                `endif
                            end else if(os_store) begin
                                // store data
                                // TODO: write what data was stored
                                `ifdef DEBUG
                                $display("[t=%d][Cache/OS] TLB Hit, Cache hit, store", $time);
                                $display("[t=%d][Cache/OS] store done pt1 os_cache_hit_way = 0x%X", 
                                                $time,                       os_cache_hit_way);
                                $display("[t=%d][Cache/OS] store done pt2 storage_write[os_cache_hit_way] = 0x%X, storage_writedata[os_cache_hit_way] = 0x%X", 
                                                $time,                       storage_write[os_cache_hit_way],        storage_writedata[os_cache_hit_way]);
                                
                                $display("[t=%d][Cache/OS] store done pt3 storage_readdata[os_cache_hit_way] = 0x%X",
                                                $time,                       storage_readdata[os_cache_hit_way]);
                                for(way_counter = 0; way_counter < WAYS; way_counter = way_counter + 1)
                                    $display("[t=%d][Cache/OS] store done pt4 way_counter = 0x%X, storage_write[way_counter] = 0x%X, storage_writedata[way_counter] = 0x%X",
                                                    $time,                       way_counter,        storage_write[way_counter],        storage_writedata[way_counter]);
                                $display("[t=%d][Cache/OS] store done pt5 os_address_inword_offset = 0x%X, os_store_type = 0x%X, os_store_data/storegenDataIn = 0x%X, storegen_dataout = 0x%X, storegen_mask = 0x%X, c_store_missaligned = 0x%d, c_store_unknowntype = 0x%d", 
                                                $time,                       os_address_inword_offset,        os_store_type,        os_store_data,                       storegen_dataout,        storegen_mask,        c_store_missaligned,        c_store_unknowntype);
                                `endif
                                // TODO: set dirty bit
                                dirty[os_address_lane][os_cache_hit_way] <= 1;
                            end
                        end else begin
                            // Cache miss
                            if(os_current_way_valid && os_current_way_dirty) begin
                                // Flush and refill on lane = os_address_lane, way = current_way
                                state <= STATE_FLUSH;
                                return_state <= STATE_REFILL;
                                `ifdef DEBUG
                                $display("[t=%d][Cache/OS] TLB Hit, Cache miss, dirty => flush lane=0x%X, current_way=0x%X", $time, os_address_lane, current_way);
                                `endif
                            end else begin
                                // Refill on lane = os_address_lane, way = current_way
                                state <= STATE_REFILL;
                                `ifdef DEBUG
                                $display("[t=%d][Cache/OS] TLB Hit, Cache miss, refill lane=0x%X, current_way=0x%X", $time, os_address_lane, current_way);
                                `endif
                            end
                        end
                    end
                end else if(tlb_miss) begin
                    state <= STATE_PTW;
                end
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
            if(flush_initial_done) begin
                if(!m_waitrequest) begin
                    os_word_counter <= os_word_counter_next;
                    if(os_word_counter == (2**OFFSET_W)-1) begin
                        state <= return_state;
                        dirty[os_address_lane][current_way] <= 0;
                        flush_initial_done <= 0;
                    end
                end
            end else begin
                if(valid[os_address_lane][current_way] && dirty[os_address_lane][current_way]) begin
                    flush_initial_done <= 1;
                    os_word_counter <= 0;
                    `ifdef DEBUG
                    $display("[t=%d][Cache] Flushing os_address_lane = 0x%X, current_way = 0x%X", $time, os_address_lane, current_way);
                    `endif
                end else begin
                    `ifdef DEBUG
                    $display("[t=%d][Cache] Not Flushing, because not valid and dirty os_address_lane = 0x%X, current_way = 0x%X", $time, os_address_lane, current_way);
                    `endif
                    state <= return_state;
                    flush_initial_done <= 0;
                    os_word_counter <= 0;
                end
            end
        end
        STATE_FLUSH_ALL: begin
            flush_all_initial_done <= 1;
            if(current_way_next == WAYS-1 && os_address_lane_next == LANES-1) begin
                return_state <= STATE_IDLE;
            end else begin
                return_state <= STATE_FLUSH_ALL;
            end
            if(valid[os_address_lane_next][current_way_next] && dirty[os_address_lane_next][current_way_next]) begin
                // Goto state_flush
                state <= STATE_FLUSH;
                $display("[t=%d][Cache] Flush_all: Flushing os_address_lane_next = 0x%X, current_way_next = 0x%X, return_state = %d", $time, os_address_lane_next, current_way_next, return_state);
            end else if(valid[os_address_lane_next][current_way_next]) begin
                valid[os_address_lane_next][current_way_next] <= 0;
                $display("[t=%d][Cache] Flush_all: invalidated: os_address_lane_next = 0x%X, current_way_next = 0x%X", $time, os_address_lane_next, current_way_next);
                // invalidate
                if(current_way_next == WAYS-1 && os_address_lane_next == LANES-1) begin
                    state <= STATE_IDLE;
                    $display("[t=%d][Cache] Flush_all: Done", $time);
                end
            end else begin
                // Nothing to do
                if(current_way_next == WAYS-1 && os_address_lane_next == LANES-1) begin
                    state <= STATE_IDLE;
                    $display("[t=%d][Cache] Flush_all: Done", $time);
                end
            end
            os_address_lane <= os_address_lane_next;
            current_way <= current_way_next;
            
        end
        STATE_REFILL: begin
            if(!refill_initial_done) begin
                refill_initial_done <= 1;
                `ifdef DEBUG
                $display("[t=%d][Cache] Refill initial cycle", $time);
                $display("[t=%d][Cache] tlb_ptag_read = 0x%X, os_address_lane = 0x%X, os_word_counter = 0x%X", $time, tlb_ptag_read, os_address_lane, os_word_counter);
                `endif
            end else begin
                
                if(!m_waitrequest) begin
                    refill_waitrequest_handshaked <= 1;
                    `ifdef DEBUG
                    if(!refill_waitrequest_handshaked)
                        $display("[t=%d][Cache] Refill waitrequest handshaked", $time);
                    `endif
                end
                if(!m_waitrequest && m_readdatavalid) begin
                    `ifdef DEBUG
                    $display("[t=%d][Cache] Refill read request from avalon done os_word_counter = 0x%X, current_way = 0x%X", $time, os_word_counter, current_way);
                    `endif
                    if(os_word_counter != (2**OFFSET_W)-1)
                        os_word_counter <= os_word_counter + 1;
                    else begin
                        valid[os_address_lane][current_way] <= 1;
                        state <= STATE_IDLE;
                        os_word_counter <= os_word_counter + 1;
                        current_way <= current_way + 1;
                        refill_initial_done <= 0;
                         `ifdef DEBUG
                        $display("[t=%d][Cache] Refill done os_word_counter = 0x%X, current_way = 0x%X", $time, os_word_counter, current_way);
                        `endif
                    end
                    refill_waitrequest_handshaked <= 0;
                    
                end
            end
            // TODO: Set valid flag
            // Request ptag
            // Request data from memory
            // If data from memory ready write to datastorage
            // after refilling increment current_way
        end
        default: begin
            $display("[%d][Cache] Unknown state = 0x%X", $time, state);
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