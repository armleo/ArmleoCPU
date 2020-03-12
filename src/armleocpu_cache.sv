module armleocpu_cache(
    input                  clk,
    input                  rst_n,


// Core request and response
    // address for read or write (virtual or physical depending on csr_stap_mode
    input  [31:0]          c_address,
    // Cache wait request (when asserted, then inputs should not change)
    output logic           c_wait,
    output logic           c_pagefault,
    output logic           c_accessfault,

    // c_pagefault and c_load => load page fault (instruction fetch page fault in case of fetch unit)
    // c_accessfault and c_load => load access fault (instruction fetch access fault in case of fetch unit)
    // same rules apply to c_store


    // Request completed
    output logic           c_done,

    `ifdef DEBUG
    output logic           c_miss,
    `endif
    
    // request is load
    input                  c_load,
    input  [2:0]           c_load_type, // enum defined in armleocpu_defs
    output logic [31:0]    c_load_data,
    // request is store
    input                  c_store,
    input        [1:0]     c_store_type, // enum defined in armleocpu_defs
    input         [31:0]   c_store_data,
    
    input                  c_flush,
    output logic           c_flushing,
    output logic           c_flush_done,


    // CACHE <-> CSR
    input                  csr_satp_mode, // Mode = 0 -> physical access, 1 -> ppn valid
    input        [21:0]    csr_satp_ppn,
    
    // CACHE <-> MEMORY
    output logic [33:0]    m_address,
    output logic [3:0]     m_burstcount,
    input                  m_waitrequest,
    
    output logic           m_read,
    input        [31:0]    m_readdata,
    input                  m_readdatavalid,
    
    output    reg          m_write,
    output    reg [31:0]   m_writedata,
    output  reg [3:0]      m_byteenable
    
    `ifdef DEBUG
    , output trace_error

    `endif
);
`include "armleocpu_defs.sv"

parameter WAYS_W = 2;
localparam WAYS = 2**WAYS_W;

parameter LANES_W = 6;
localparam LANES = 2**LANES_W;

localparam PHYS_W = 22;
localparam VIRT_W = 20;

// 4 = 16 words each 32 bit = 64 byte
localparam OFFSET_W = 4;


// Current state
    reg [3:0] state;
    reg [3:0] return_state;
    localparam 	STATE_IDLE = 4'd0,
                STATE_FLUSH = 4'd1,
                STATE_REFILL = 4'd2,
                STATE_FLUSH_ALL = 4'd3,
                STATE_PTW = 4'd4;


// Valid, dirty storage
reg	[LANES-1:0] valid [WAYS-1:0];
reg	[LANES-1:0] dirty [WAYS-1:0];



// Data storage

reg                 storage_read        [WAYS-1:0];
reg  [LANES_W-1:0]  storage_readlane    [WAYS-1:0];
reg  [OFFSET_W-1:0] storage_readoffset  [WAYS-1:0];
wire [31:0]         storage_readdata    [WAYS-1:0];

reg                 storage_write       [WAYS-1:0];
reg  [LANES_W-1:0]  storage_writelane   [WAYS-1:0];
reg  [OFFSET_W-1:0] storage_writeoffset [WAYS-1:0];
reg  [31:0]         storage_writedata   [WAYS-1:0];

reg  [LANES_W-1:0]  ptag_readlane       [WAYS-1:0];
reg                 ptag_read           [WAYS-1:0];
wire [PHYS_W-1:0]   ptag_readdata       [WAYS-1:0];

reg  [LANES_W-1:0]  ptag_writelane      [WAYS-1:0];
reg                 ptag_write          [WAYS-1:0];
reg  [PHYS_W-1:0]   ptag_writedata      [WAYS-1:0];


genvar way_num;

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

// Goes to TLB
wire [VIRT_W-1:0] 	        c_address_vtag           = c_address[31:32-VIRT_W];

// address composition
// used for requests to storage
wire [LANES_W-1:0]	        c_address_lane          = c_address[2+OFFSET_W:2+OFFSET_W];
wire [3:0]			        c_address_offset        = c_address[2+OFFSET_W-1:2];
wire [1:0]			        c_address_inword_offset = c_address[1:0];


// Output stage (see schematic view in docs/Cache.png)
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

logic [WAYS-1:0]            os_cache_hit;
logic [WAYS-1:0]            os_ptag;
logic [WAYS_W-1:0]          os_cache_hit_way;
always @* begin
    integer way_num;
    for(way_num = WAYS-1; way_num >= 0; way_num = way_num - 1) begin
        os_ptag[way_num] = ptag_readdata[way_num];
        os_cache_hit[way_num] = os_valid[way_num] && os_ptag[way_num] == tlb_ptag_read;
        os_cache_hit_way = way_num;
    end
end


// TLB
reg tlb_resolve, tlb_write;
wire tlb_done, tlb_miss;
wire [21:0] tlb_ptag_read;
reg [7:0] tlb_accesstag_write;
wire [7:0] tlb_accesstag_read;
reg [21:0] tlb_ptag_write;
reg [19:0] tlb_vtag;


armleocpu_tlb tlb(
    .rst_n(rst_n),
    .clk(clk),
    
    .enable(csr_satp_mode),
    .virtual_address(c_address_vtag),
    // For flush request it's safe
    // to invalidate all tlb because
    // cache keeps track of access validity
    // and uses physical tagging
    .invalidate(c_flush),
    .resolve(tlb_resolve),
    
    .miss(tlb_miss),
    .done(tlb_done),
    
    // resolve result for virt
    .accesstag_r(tlb_accesstag_read),
    .phys_r(tlb_ptag_read),
    
    // write for for entry virt
    .write(tlb_write),
    // where to write
    .virtual_address_w(tlb_vtag),
    // access tag
    .accesstag_w(tlb_accesstag_write),
    // and phys
    .phys_w(tlb_ptag_write)
);


integer i;

always @(negedge rst_n or posedge clk) begin
    if(!rst_n) begin
        for(i = 0; i < LANES; i = i + 1) begin
            valid[i]  <= 0;
        end
            state     <= STATE_IDLE;
            os_active <= 0;
    end else if(clk) begin
        if(c_flush)
            flush_pending <= 1;
        case(state)
        STATE_IDLE: begin 
                if(c_flush || flush_pending) begin
                    state <= STATE_FLUSH_ALL;
                    // TODO: init variables for flush
                end else if(c_load || c_store) begin
                    //os_readdata <= storage[c_address_lane][c_address_offset];
                    // ptag_r read

                    os_active <= 1;
                    os_valid <= valid[c_address_lane];
                    os_dirty <= dirty[c_address_lane];
                    
                    // Save address composition
                    os_address_inword_offset    <= c_address_inword_offset;
                    os_address_lane             <= c_address_lane;
                    os_address_offset           <= c_address_offset;

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
                    if(!tlb_done)
                        $display("[Cache] TLB WTF");
                    else
                        
                end
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