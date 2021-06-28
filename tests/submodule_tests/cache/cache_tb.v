`define TIMEOUT 10000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"

`define MAXIMUM_ERRORS 1

localparam ADDR_WIDTH = 34;
// Note: If ADDR WIDTH is changed then values below need changing too
localparam DATA_STROBES = 4;
localparam DATA_WIDTH = 32;
localparam ID_WIDTH = 1;
localparam DEPTH = 1024 * 64;


// Memory Map
// 0x1000-0x3000 -> BRAM0 0x0000
// 0x80002000-0x80004000 -> BRAM1 0x0000


localparam [33:0] REGION_BRAM0_BEGIN = 34'h1000;
localparam [33:0] REGION_BRAM0_END = REGION_BRAM0_BEGIN + DEPTH;

localparam [33:0] REGION_BRAM1_BEGIN = 34'h80002000;
localparam [33:0] REGION_BRAM1_END = REGION_BRAM1_BEGIN + DEPTH;

localparam OPT_NUMBER_OF_CLIENTS = 2;
localparam OPT_NUMBER_OF_CLIENTS_CLOG2 = $clog2(OPT_NUMBER_OF_CLIENTS);
localparam REGION_COUNT = 2;
localparam [REGION_COUNT * OPT_NUMBER_OF_CLIENTS_CLOG2 - 1:0]           
                                                REGION_CLIENT_NUM       = {1'b1                 , 1'b0              };
localparam [REGION_COUNT * ADDR_WIDTH - 1:0]    REGION_BASE_ADDRS       = {REGION_BRAM1_BEGIN   , REGION_BRAM0_BEGIN};
localparam [REGION_COUNT * ADDR_WIDTH - 1:0]    REGION_END_ADDRS        = {REGION_BRAM1_END     , REGION_BRAM0_END  };
localparam [REGION_COUNT * ADDR_WIDTH - 1:0]    REGION_CLIENT_BASE_ADDRS= {REGION_BRAM1_BEGIN   , REGION_BRAM0_BEGIN};



wire [3:0] c_response;
wire c_done;

reg [3:0] c_cmd;
reg [31:0] c_address;
reg [2:0] c_load_type;
wire [31:0] c_load_data;
reg [1:0] c_store_type;
reg [31:0] c_store_data;

reg csr_satp_mode;
reg [21:0] csr_satp_ppn;

reg csr_mstatus_mprv;
reg csr_mstatus_mxr;
reg csr_mstatus_sum;
reg [1:0] csr_mstatus_mpp;

reg [1:0] csr_mcurrent_privilege;

wire axi_awvalid;
wire axi_awready;
wire [ADDR_WIDTH-1:0] axi_awaddr;
wire axi_awlock;
wire [2:0] axi_awprot;

wire axi_wvalid;
wire axi_wready;
wire [DATA_WIDTH-1:0] axi_wdata;
wire [DATA_STROBES-1:0] axi_wstrb;
wire axi_wlast;

wire axi_bvalid;
wire axi_bready;
wire [1:0] axi_bresp;

wire axi_arvalid;
wire axi_arready;
wire [ADDR_WIDTH-1:0]  axi_araddr;
wire [7:0]   axi_arlen;
wire [1:0]   axi_arburst;
wire axi_arlock;
wire [2:0] axi_arprot;

wire axi_rvalid;
wire axi_rready;
wire [1:0] axi_rresp;
wire axi_rlast;
wire [DATA_WIDTH-1:0] axi_rdata;

localparam WAYS = 2;
localparam TLB_ENTRIES_W = 2;
localparam TLB_WAYS = 2;
localparam LANES_W = 1;
localparam IS_INSTURCTION_CACHE = 0;

armleocpu_cache #(
    .WAYS           (WAYS),
    .TLB_ENTRIES_W  (TLB_ENTRIES_W),
    .TLB_WAYS       (TLB_WAYS),
    .LANES_W        (LANES_W),
    .IS_INSTURCTION_CACHE
                    (IS_INSTURCTION_CACHE)
) cache(
    .csr_satp_mode_in(csr_satp_mode),
    .csr_satp_ppn_in(csr_satp_ppn),

    .csr_mstatus_mprv_in(csr_mstatus_mprv),
    .csr_mstatus_mxr_in(csr_mstatus_mxr),
    .csr_mstatus_sum_in(csr_mstatus_sum),
    .csr_mstatus_mpp_in(csr_mstatus_mpp),
    .csr_mcurrent_privilege_in(csr_mcurrent_privilege),

    .*
);

genvar k;
genvar byte_offset;
genvar lane_offset_addr;
genvar lane_addr;
generate for(k = 0; k < WAYS; k = k + 1) begin
    initial begin
        $dumpvars(0, cache.cptag_readdata[k]);
        $dumpvars(0, cache.storage_readdata[k]);
        $dumpvars(0, cache.valid[k]);
        $dumpvars(0, cache.valid_nxt[k]);
        
    end
    
    
    for(byte_offset = 0; byte_offset < 32; byte_offset = byte_offset + 8) begin
        // 4 == OFFSET_W
        for(lane_offset_addr = 0; lane_offset_addr < 2**(LANES_W + 4); lane_offset_addr = lane_offset_addr + 1) begin
            initial $dumpvars(0, cache.mem_generate_for[k].datastorage.mem_generate_for[byte_offset].storage.storage[lane_offset_addr]);
        end
    end
    for(lane_addr = 0; lane_addr < 2**LANES_W; lane_addr = lane_addr + 1)
        initial $dumpvars(0, cache.mem_generate_for[k].ptag_storage.storage[lane_addr]);
end endgenerate

wire [7:0] axi_awlen = 0;
wire [1:0] axi_awburst = 2'b01;
wire [2:0] axi_awsize = 3'b010;
wire [ID_WIDTH-1:0] axi_awid = 0;

wire [ID_WIDTH-1:0] axi_bid;

wire [2:0] axi_arsize = 3'b010;
wire [ID_WIDTH-1:0] axi_arid = 0;

wire [ID_WIDTH-1:0] axi_rid;



`define DECLARE_AXI_WIRES(prefix) \
wire ``prefix``awvalid; \
wire ``prefix``awready; \
wire [ADDR_WIDTH-1:0] ``prefix``awaddr; \
wire [7:0] ``prefix``awlen; \
wire [2:0] ``prefix``awsize; \
wire [1:0] ``prefix``awburst; \
wire [ID_WIDTH-1:0] ``prefix``awid; \
 \
wire ``prefix``wvalid; \
wire ``prefix``wready; \
wire [DATA_WIDTH-1:0] ``prefix``wdata; \
wire [DATA_STROBES-1:0] ``prefix``wstrb; \
wire ``prefix``wlast; \
\
wire ``prefix``bvalid; \
wire ``prefix``bready; \
wire [1:0] ``prefix``bresp; \
wire [ID_WIDTH-1:0] ``prefix``bid; \
 \
wire ``prefix``arvalid; \
wire ``prefix``arready; \
wire [ADDR_WIDTH-1:0] ``prefix``araddr; \
wire [7:0] ``prefix``arlen; \
wire [2:0] ``prefix``arsize; \
wire [1:0] ``prefix``arburst; \
wire [ID_WIDTH-1:0] ``prefix``arid; \
 \
wire ``prefix``rvalid; \
wire ``prefix``rready; \
wire [1:0] ``prefix``rresp; \
wire [DATA_WIDTH-1:0] ``prefix``rdata; \
wire [ID_WIDTH-1:0] ``prefix``rid; \
wire ``prefix``rlast; \



`DECLARE_AXI_WIRES(memory0_axi_)

`DECLARE_AXI_WIRES(memory1_axi_) 

wire downstream0_axi_awvalid;
wire downstream1_axi_awvalid;
wire downstream0_axi_awready;
wire downstream1_axi_awready;

wire [ADDR_WIDTH-1:0] downstream_axi_awaddr;
wire [7:0] downstream_axi_awlen;
wire [2:0] downstream_axi_awsize;
wire [1:0] downstream_axi_awburst;
wire downstream_axi_awlock;
wire [2:0] downstream_axi_awprot;
wire [ID_WIDTH-1:0] downstream_axi_awid;

wire downstream0_axi_wvalid;
wire downstream1_axi_wvalid;
wire downstream0_axi_wready;
wire downstream1_axi_wready;
wire [DATA_WIDTH-1:0] downstream_axi_wdata;
wire [DATA_STROBES-1:0] downstream_axi_wstrb;
wire downstream_axi_wlast;

wire downstream0_axi_bvalid;
wire downstream0_axi_bready;
wire [1:0] downstream0_axi_bresp;
wire [ID_WIDTH-1:0] downstream0_axi_bid;

wire downstream1_axi_bvalid;
wire downstream1_axi_bready;
wire [1:0] downstream1_axi_bresp;
wire [ID_WIDTH-1:0] downstream1_axi_bid;


wire downstream0_axi_arvalid;
wire downstream0_axi_arready;
wire downstream1_axi_arvalid;
wire downstream1_axi_arready;
wire [ADDR_WIDTH-1:0] downstream_axi_araddr;
wire [7:0] downstream_axi_arlen;
wire [2:0] downstream_axi_arsize;
wire [1:0] downstream_axi_arburst;
wire downstream_axi_arlock;
wire [2:0] downstream_axi_arprot;
wire [ID_WIDTH-1:0] downstream_axi_arid;

wire downstream0_axi_rvalid;
wire downstream1_axi_rvalid;
wire downstream0_axi_rready;
wire downstream1_axi_rready;
wire [1:0] downstream0_axi_rresp;
wire [1:0] downstream1_axi_rresp;
wire downstream0_axi_rlast;
wire downstream1_axi_rlast;
wire [DATA_WIDTH-1:0] downstream0_axi_rdata;
wire [DATA_WIDTH-1:0] downstream1_axi_rdata;
wire [ID_WIDTH-1:0] downstream0_axi_rid;
wire [ID_WIDTH-1:0] downstream1_axi_rid;


armleocpu_axi_bram #(DEPTH, ADDR_WIDTH, ID_WIDTH, DATA_WIDTH) bram0 (
    .clk(clk),
    .rst_n(rst_n),

    `CONNECT_AXI_BUS(axi_, memory0_axi_)
    
);

armleocpu_axi_bram #(DEPTH, ADDR_WIDTH, ID_WIDTH, DATA_WIDTH) bram1 (
    .clk(clk),
    .rst_n(rst_n),

    `CONNECT_AXI_BUS(axi_, memory1_axi_)
    
);

armleocpu_axi_exclusive_monitor #(ADDR_WIDTH, ID_WIDTH, DATA_WIDTH) exclusive_monitor0 (
    .clk(clk),
    .rst_n(rst_n),

    `CONNECT_AXI_BUS(memory_axi_, memory0_axi_),

    .cpu_axi_awvalid    (downstream0_axi_awvalid),
    .cpu_axi_awready    (downstream0_axi_awready),
    .cpu_axi_awaddr     (downstream_axi_awaddr),
    .cpu_axi_awlen      (downstream_axi_awlen),
    .cpu_axi_awsize     (downstream_axi_awsize),
    .cpu_axi_awburst    (downstream_axi_awburst),
    .cpu_axi_awlock     (downstream_axi_awlock),
    .cpu_axi_awid       (downstream_axi_awid),

    .cpu_axi_wvalid     (downstream0_axi_wvalid),
    .cpu_axi_wready     (downstream0_axi_wready),
    .cpu_axi_wdata      (downstream_axi_wdata),
    .cpu_axi_wstrb      (downstream_axi_wstrb),
    .cpu_axi_wlast      (downstream_axi_wlast),

    .cpu_axi_bvalid     (downstream0_axi_bvalid),
    .cpu_axi_bready     (downstream0_axi_bready),
    .cpu_axi_bresp      (downstream0_axi_bresp),
    .cpu_axi_bid        (downstream0_axi_bid),

    .cpu_axi_arvalid    (downstream0_axi_arvalid),
    .cpu_axi_arready    (downstream0_axi_arready),
    .cpu_axi_araddr     (downstream_axi_araddr),
    .cpu_axi_arlen      (downstream_axi_arlen),
    .cpu_axi_arsize     (downstream_axi_arsize),
    .cpu_axi_arburst    (downstream_axi_arburst),
    .cpu_axi_arlock     (downstream_axi_arlock),
    .cpu_axi_arid       (downstream_axi_arid),

    .cpu_axi_rvalid     (downstream0_axi_rvalid),
    .cpu_axi_rready     (downstream0_axi_rready),
    .cpu_axi_rresp      (downstream0_axi_rresp),
    .cpu_axi_rlast      (downstream0_axi_rlast),
    .cpu_axi_rdata      (downstream0_axi_rdata),
    .cpu_axi_rid        (downstream0_axi_rid),
    .*

);


armleocpu_axi_exclusive_monitor #(ADDR_WIDTH, ID_WIDTH, DATA_WIDTH) exclusive_monitor1 (
    .clk(clk),
    .rst_n(rst_n),

    `CONNECT_AXI_BUS(memory_axi_, memory1_axi_),
    
    .cpu_axi_awvalid    (downstream1_axi_awvalid),
    .cpu_axi_awready    (downstream1_axi_awready),
    .cpu_axi_awaddr     (downstream_axi_awaddr),
    .cpu_axi_awlen      (downstream_axi_awlen),
    .cpu_axi_awsize     (downstream_axi_awsize),
    .cpu_axi_awburst    (downstream_axi_awburst),
    .cpu_axi_awlock     (downstream_axi_awlock),
    .cpu_axi_awid       (downstream_axi_awid),

    .cpu_axi_wvalid     (downstream1_axi_wvalid),
    .cpu_axi_wready     (downstream1_axi_wready),
    .cpu_axi_wdata      (downstream_axi_wdata),
    .cpu_axi_wstrb      (downstream_axi_wstrb),
    .cpu_axi_wlast      (downstream_axi_wlast),

    .cpu_axi_bvalid     (downstream1_axi_bvalid),
    .cpu_axi_bready     (downstream1_axi_bready),
    .cpu_axi_bresp      (downstream1_axi_bresp),
    .cpu_axi_bid        (downstream1_axi_bid),

    .cpu_axi_arvalid    (downstream1_axi_arvalid),
    .cpu_axi_arready    (downstream1_axi_arready),
    .cpu_axi_araddr     (downstream_axi_araddr),
    .cpu_axi_arlen      (downstream_axi_arlen),
    .cpu_axi_arsize     (downstream_axi_arsize),
    .cpu_axi_arburst    (downstream_axi_arburst),
    .cpu_axi_arlock     (downstream_axi_arlock),
    .cpu_axi_arid       (downstream_axi_arid),

    .cpu_axi_rvalid     (downstream1_axi_rvalid),
    .cpu_axi_rready     (downstream1_axi_rready),
    .cpu_axi_rresp      (downstream1_axi_rresp),
    .cpu_axi_rlast      (downstream1_axi_rlast),
    .cpu_axi_rdata      (downstream1_axi_rdata),
    .cpu_axi_rid        (downstream1_axi_rid),
    .*
);



armleocpu_axi_router #(
    .ADDR_WIDTH(ADDR_WIDTH),
    .ID_WIDTH(ID_WIDTH),
    .DATA_WIDTH(DATA_WIDTH),

    .OPT_NUMBER_OF_CLIENTS(OPT_NUMBER_OF_CLIENTS),

    .REGION_COUNT               (REGION_COUNT),
    .REGION_CLIENT_NUM          (REGION_CLIENT_NUM),
    .REGION_BASE_ADDRS          (REGION_BASE_ADDRS),
    .REGION_END_ADDRS           (REGION_END_ADDRS),
    .REGION_CLIENT_BASE_ADDRS   (REGION_CLIENT_BASE_ADDRS)
) router (
    .clk(clk),
    .rst_n(rst_n),

    `CONNECT_AXI_BUS(upstream_axi_, axi_),
    .upstream_axi_arlock(axi_arlock),
    .upstream_axi_arprot(axi_arprot),
    .upstream_axi_awlock(axi_awlock),
    .upstream_axi_awprot(axi_awprot),



    .downstream_axi_awvalid     ({downstream1_axi_awvalid,  downstream0_axi_awvalid}),
    .downstream_axi_awready     ({downstream1_axi_awready,  downstream0_axi_awready}),

    .downstream_axi_wvalid      ({downstream1_axi_wvalid,   downstream0_axi_wvalid}),
    .downstream_axi_wready      ({downstream1_axi_wready,   downstream0_axi_wready}),

    .downstream_axi_bvalid      ({downstream1_axi_bvalid,   downstream0_axi_bvalid}),
    .downstream_axi_bready      ({downstream1_axi_bready,   downstream0_axi_bready}),
    .downstream_axi_bresp       ({downstream1_axi_bresp,    downstream0_axi_bresp}),
    .downstream_axi_bid         ({downstream1_axi_bid,      downstream0_axi_bid}),

    .downstream_axi_arvalid     ({downstream1_axi_arvalid,  downstream0_axi_arvalid}),
    .downstream_axi_arready     ({downstream1_axi_arready,  downstream0_axi_arready}),

    .downstream_axi_rvalid      ({downstream1_axi_rvalid,   downstream0_axi_rvalid}),
    .downstream_axi_rready      ({downstream1_axi_rready,   downstream0_axi_rready}),
    .downstream_axi_rresp       ({downstream1_axi_rresp,    downstream0_axi_rresp}),
    .downstream_axi_rlast       ({downstream1_axi_rlast,    downstream0_axi_rlast}),
    .downstream_axi_rdata       ({downstream1_axi_rdata,    downstream0_axi_rdata}),
    .downstream_axi_rid         ({downstream1_axi_rid,      downstream0_axi_rid}),

    .*


);

reg [31:0] mem [DEPTH * 2  -1:0];

// -------------- UTILS ------------------

task bus_align;
input [1:0] inword_offset;
input [31:0] store_data;
input [1:0] store_type;
output [31:0] data;
output [3:0] strb;
begin
    if(store_type == `STORE_WORD) begin
        strb = 4'hF;
        data = store_data;
    end else if(store_type == `STORE_HALF) begin
        strb = 4'b0011 << inword_offset;
        data = store_data << (8*inword_offset);
    end else if(store_type == `STORE_BYTE) begin
        strb = 4'b0001 << inword_offset;
        data = store_data << (8*inword_offset);
    end
end
endtask

// This task accepts physical address and returns mem[]-s according address
//      its existance in variable mem[]


task convert_addr_to_mem_location;
input [33:0] phys_address;
output [31:0] mem_location;
output mem_location_exists;
begin
    if((phys_address >= REGION_BRAM0_BEGIN) && (phys_address < REGION_BRAM0_END)) begin
        mem_location_exists = 1;
        mem_location = phys_address - REGION_BRAM0_BEGIN;
    end else if((phys_address >= REGION_BRAM1_BEGIN) && (phys_address < REGION_BRAM1_END)) begin
        mem_location_exists = 1;
        // Find offset relative to second BRAM mem[] locations base
        // then add BRAM1's base addr;
        mem_location = phys_address - REGION_BRAM1_BEGIN + DEPTH;
    end else begin
        mem_location_exists = 0;
        mem_location = 2*DEPTH + 1; // Somewhere outside
    end
end
endtask

// Task that accepts command and virtual address
// Does PTW and returns physical address

task read_physical_addr;
input [33:0] addr;
output [31:0] readdata;
output accessfault;
begin
    reg [31:0] mem_location;
    reg mem_location_exists;

    convert_addr_to_mem_location(addr, mem_location, mem_location_exists);
    if(mem_location_exists) begin
        readdata = mem[mem_location];
    end
    accessfault = !mem_location_exists;
end
endtask

task convert_virtual_to_physical;
input [3:0] cmd;
input [31:0] address;
output pagefault;
output accessfault;
output [33:0] phys_addr;
begin
    reg vm_enabled;
    reg [1:0] vm_privilege;
    reg [21:0] current_table_base;
    integer current_level;
    
    reg [31:0] readdata;

    if(csr_mcurrent_privilege == 3) begin
        if(csr_mstatus_mprv == 0) begin
            vm_privilege = csr_mcurrent_privilege;
        end else begin
            vm_privilege = csr_mstatus_mpp;
        end
    end else begin
        vm_privilege = csr_mcurrent_privilege;
    end

    if(vm_privilege != 3) begin
        vm_enabled = csr_satp_mode;
    end else begin
        vm_enabled = 0;
    end

    if(!vm_enabled) begin
        pagefault = 0;
        accessfault = 0;
        phys_addr = address;
    end else begin
        accessfault = 0;
        pagefault = 0;

        current_table_base = csr_satp_ppn;
        current_level = 1;

        while(current_level > 0) begin
            read_physical_addr(
                {current_table_base, (current_level == 1) ? address[19:10] : address[9:0], 2'b00},
                readdata, accessfault
            );

            if(accessfault) begin
                accessfault = 1;
                current_level = -1;
            end else if(!readdata[0] || (!readdata[1] && readdata[2])) begin // pte invalid
                pagefault = 1;
                current_level = -1;
            end else if(readdata[1] || readdata[2]) begin // pte is leaf
                if((current_level == 1) && (readdata[19:10] != 0)) begin // pte missaligned
                    pagefault = 1;
                    current_level = -1;
                end else begin // done
                    phys_addr = {readdata[31:20], current_level ? address[21:12] : readdata[19:10], address[11:0]};
                    current_level = -1;
                end
            end else if(readdata[3:0] == 4'b0001) begin // pte pointer
                if(current_level == 0) begin
                    pagefault = 1;
                    current_level = -1;
                end else begin
                    current_level = current_level - 1;
                    current_table_base = readdata[31:10];
                end
            end
        end
        // TODO: Properly implement
        /*
        if(!pagefault && !accessfault) begin // If no pagefault and no accessfault
            if((!readdata[1] || !readdata[6]) && (c_cmd == `CACHE_CMD_LOAD || c_cmd == `CACHE_CMD_LOAD_RESERVE)) begin
                
            end
            if((!readdata[2] || !readdata[6] || !readdata[7]) && (c_cmd == `CACHE_CMD_STORE || c_cmd == `CACHE_CMD_STORE_CONDITIONAL)) begin
                
            end
            if((!readdata[3] || !readdata[6]) && (c_cmd == `CACHE_CMD_EXECUTE)) begin
                
            end
            if(vm_privilege == 1) begin
                if(csr_mstatus_sum) begin
                end
            end
        end*/
    end
    

end
endtask

task calculate_addr_request;
input [3:0] cmd;
input [31:0] address;
output pagefault;
output accessfault;
output [31:0] mem_location;
output mem_location_exists;
output [33:0] phys_addr;
begin
    pagefault = 0;
    accessfault = 0;
    mem_location_exists = 0;
    convert_virtual_to_physical(cmd, address, pagefault, accessfault, phys_addr);
    if(pagefault) begin
        pagefault = 1;
        accessfault = 0;
        mem_location_exists = 0;
    end else if(accessfault) begin
        accessfault = 1;
    end else begin
        convert_addr_to_mem_location(phys_addr, mem_location, mem_location_exists);
    end
end
endtask

// TODO: Calculate atomic access

task calculate_read_resp;
input [3:0] cmd;
input [31:0] address;
input [2:0] load_type;
output [3:0] expected_response;
output [31:0] expected_readdata;
begin
    reg [33:0] phys_addr;
    reg pagefault;
    reg accessfault;
    reg mem_location_exists;
    reg [31:0] mem_location;

    calculate_addr_request(cmd, address, pagefault, accessfault, mem_location, mem_location_exists, phys_addr);
    
    if(load_type == `LOAD_WORD && |address[1:0]) begin
        expected_response = `CACHE_RESPONSE_MISSALIGNED;
    end else if((load_type == `LOAD_HALF || load_type == `LOAD_HALF_UNSIGNED) && address[0]) begin
        expected_response = `CACHE_RESPONSE_MISSALIGNED;
    end else if(
        (load_type != `LOAD_WORD) &&
        (load_type != `LOAD_HALF) &&
        (load_type != `LOAD_HALF_UNSIGNED) &&
        (load_type != `LOAD_BYTE) &&
        (load_type != `LOAD_BYTE_UNSIGNED)
    ) begin
        expected_response = `CACHE_RESPONSE_UNKNOWNTYPE;
    end else if(accessfault) begin
        expected_response = `CACHE_RESPONSE_ACCESSFAULT;
    end else if(pagefault) begin
        expected_response = `CACHE_RESPONSE_PAGEFAULT;
    end else if(!mem_location_exists) begin
            expected_response = `CACHE_RESPONSE_ACCESSFAULT;
    end else begin
        expected_response = `CACHE_RESPONSE_SUCCESS;
    end
    if(mem_location_exists)
        expected_readdata = mem[mem_location];
end
endtask


task do_wait_for_done;
input [31:0] timeout;
begin
    integer timeout_counter;
    timeout_counter = 0;
    while(!c_done) begin
        @(negedge clk);
        timeout_counter = timeout_counter + 1;
        if(timeout_counter == timeout) begin
            $display("Error: !ERROR!: Timeout reached");
            `assert_equal(0, 1)
        end
    end
end
endtask


task calculate_write_resp;
input lock;
input [31:0] addr;
input [1:0] store_type;
output [3:0] expected_response;
begin
    reg [3:0] cmd;
    reg pagefault;
    reg accessfault;
    reg [31:0] mem_location;
    reg mem_location_exists;
    reg [33:0] phys_addr; // Ignored
    // TODO: Add atomics
    
    cmd = lock ? `CACHE_CMD_STORE_CONDITIONAL : `CACHE_CMD_STORE;
    calculate_addr_request(cmd, addr, pagefault, accessfault, mem_location, mem_location_exists, phys_addr);
    if(store_type == `STORE_WORD && |(addr[1:0])) begin
        expected_response = `CACHE_RESPONSE_MISSALIGNED;
    end else if(store_type == `STORE_HALF && addr[0]) begin
        expected_response = `CACHE_RESPONSE_MISSALIGNED;
    end else if(
        (store_type != `STORE_WORD) && 
        (store_type != `STORE_HALF) && 
        (store_type != `STORE_BYTE)
    ) begin
        expected_response = `CACHE_RESPONSE_UNKNOWNTYPE;
    end else if(accessfault) begin
        expected_response = `CACHE_RESPONSE_ACCESSFAULT;
    end else if(pagefault) begin
        expected_response = `CACHE_RESPONSE_PAGEFAULT;
    end else if(!mem_location_exists) begin
        expected_response = `CACHE_RESPONSE_ACCESSFAULT;
    end else begin
        expected_response = `CACHE_RESPONSE_SUCCESS;
    end

end
endtask

task do_write;
input lock;
input [31:0] addr;
input [1:0] store_type;
input [31:0] store_data;
begin
    integer bs;
    reg [3:0] strb;
    reg [31:0] data;
    reg [3:0] expected_response;
    reg pagefault; // ignored
    reg mem_location_exists; // ignored
    reg [31:0] mem_location;
    reg [33:0] phys_addr; // ignored
    reg accessfault; // ignored

    c_cmd = lock ? `CACHE_CMD_STORE_CONDITIONAL : `CACHE_CMD_STORE;

    bus_align(addr[1:0], store_data, store_type, data, strb);
    calculate_write_resp(lock, addr, store_type, expected_response);
    calculate_addr_request(c_cmd, addr, pagefault, accessfault, mem_location, mem_location_exists, phys_addr);
    
    for(bs = 0; bs < 4; bs = bs + 1)
        if(strb[bs] && expected_response == `CACHE_RESPONSE_SUCCESS)
            mem[mem_location][bs*8+:8] = data[bs*8+:8];

    c_address = addr;
    c_store_type = store_type;
    c_store_data = store_data;
    @(negedge clk);
    do_wait_for_done(1000);
    `assert_equal(c_response, expected_response)

    c_cmd = `CACHE_CMD_NONE;
    // TODO: Do checks
end
endtask

task do_read;
input execute;
input lock;
input [31:0] addr;
input [2:0] load_type;
begin
    reg [3:0] expected_response;
    reg [31:0] expected_readdata;

    c_cmd = lock ? `CACHE_CMD_LOAD_RESERVE : (execute ? `CACHE_CMD_EXECUTE : `CACHE_CMD_LOAD);
    calculate_read_resp(c_cmd, addr, load_type, expected_response, expected_readdata);
    
    c_address = addr;
    c_load_type = load_type;
    @(negedge clk);
    do_wait_for_done(100);
    
    c_cmd = `CACHE_CMD_NONE;
    `assert_equal(c_response, expected_response)
    if(c_response == `CACHE_RESPONSE_SUCCESS) begin
        `assert_equal(c_load_data, expected_readdata)
    end
end
endtask

// -------------- USER FRIENDLY FUNCTIONS ------------------

task flush;
begin
    c_cmd = `CACHE_CMD_FLUSH_ALL;
    @(negedge clk);
    do_wait_for_done(100);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    c_cmd = `CACHE_CMD_NONE;
end
endtask

task store;
input [31:0] addr;
input [1:0] store_type;
input [31:0] store_data;
begin
    do_write(0, addr, store_type, store_data);
end
endtask

task store_conditional;
input [31:0] addr;
input [31:0] store_data;
begin
    do_write(0, addr, `STORE_WORD, store_data);
end
endtask

task load_reserve;
input [31:0] addr;
begin
    do_read(0, 1, addr, `LOAD_WORD);
end
endtask


task load;
input [31:0] addr;
input [2:0] load_type;
begin
    do_read(0, 0, addr, load_type);
end
endtask

task execute;
input [31:0] addr;
begin
    do_read(1, 0, addr, `LOAD_WORD);
end
endtask

localparam [11:0] PTE_VALID      = 12'b00000001;
localparam [11:0] PTE_READ       = 12'b00000010;
localparam [11:0] PTE_WRITE      = 12'b00000100;
localparam [11:0] PTE_EXECUTE    = 12'b00001000;
localparam [11:0] PTE_USER       = 12'b00010000;
localparam [11:0] PTE_ACCESS     = 12'b01000000;
localparam [11:0] PTE_DIRTY      = 12'b10000000;


localparam [11:0] RWX = PTE_ACCESS | PTE_DIRTY | PTE_VALID | PTE_READ | PTE_WRITE | PTE_EXECUTE;

localparam [11:0] NEXT_LEVEL_POINTER = PTE_VALID;

integer n;

initial begin
    reg mem_location_exists;
    reg [31:0] mem_location;
    reg [31:0] expected_readdata;
    reg [3:0] expected_response;
    reg pagefault, accessfault;
    reg [33:0] phys_addr;
    reg [31:0] readdata;
    reg is_load, is_bypassed;
    reg [31:0] addr;
    reg [31:0] word;

    @(posedge rst_n)
    csr_satp_mode = 0;
    csr_satp_ppn = 0;

    csr_mstatus_mprv = 0;
    csr_mstatus_mxr = 0;
    csr_mstatus_sum = 0;
    csr_mstatus_mpp = 0;

    csr_mcurrent_privilege = 0;

    c_address = 0;
    c_load_type = 0;
    c_store_type = 0;
    c_store_data = 32'hDEADBEEF;

    // Test convert_addr_to_mem_location
    $display("Testing testbench utils");
    convert_addr_to_mem_location(REGION_BRAM0_BEGIN, mem_location, mem_location_exists);
    `assert_equal(mem_location, 0);
    `assert_equal(mem_location_exists, 1);

    convert_addr_to_mem_location(REGION_BRAM1_BEGIN, mem_location, mem_location_exists);
    `assert_equal(mem_location, DEPTH);
    `assert_equal(mem_location_exists, 1);

    convert_addr_to_mem_location(REGION_BRAM1_END, mem_location, mem_location_exists);
    `assert_equal(mem_location_exists, 0);


    mem[0] = 32'hFFFFFFFF;
    read_physical_addr(REGION_BRAM0_BEGIN, readdata, accessfault);
    `assert_equal(accessfault, 0)
    `assert_equal(readdata, 32'hFFFFFFFF)



    @(negedge clk)

    
    // Note: c_response is checked twice
    // This is intentionally done to make sure that
    //  testbench works properly for all tests cases
    // And this is not case of double error on both TB and DuT

    $display("Testbench: Flush test");
    flush();
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)

    $display("Testbench: Bypassed load/store test");
    store(REGION_BRAM0_BEGIN, `STORE_WORD, 32'hFF00FF00);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    store(REGION_BRAM0_BEGIN + 4, `STORE_WORD, 32'hFF00FF01);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    load(REGION_BRAM0_BEGIN, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    load(REGION_BRAM0_BEGIN + 4, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)

    $display("Testbench: Cached load/store test");
    store(REGION_BRAM1_BEGIN, `STORE_WORD, 32'hFF00FF04);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    store(REGION_BRAM1_BEGIN + 4, `STORE_WORD, 32'hFF00FF05);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    load(REGION_BRAM1_BEGIN, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    load(REGION_BRAM1_BEGIN + 4, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)


    // TODO: Add atomic loads too
    // TODO: Add tests for writes
    $display("Testbench: Missaligned cached load/execute for word");
    // Cached
    load(34'h80002001, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    execute(34'h80002001);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)

    load(34'h80002002, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    execute(34'h80002002);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)

    load(34'h80002003, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    execute(34'h80002003);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)

    $display("Testbench: Missaligned bypassed load/execute for word");
    // Bypassed
    load(34'h00002001, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    execute(34'h00002001);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)

    load(34'h00002002, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    execute(34'h00002002);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)

    load(34'h00002003, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    execute(34'h00002003);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    

    $display("Testbench: Missaligned cached load for half");
    load(34'h80002001, `LOAD_HALF);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    load(34'h80002003, `LOAD_HALF);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    

    $display("Testbench: Missaligned cached load for half unsigned");
    load(34'h80002001, `LOAD_HALF_UNSIGNED);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    load(34'h80002003, `LOAD_HALF_UNSIGNED);
    `assert_equal(c_response, `CACHE_RESPONSE_MISSALIGNED)
    


    // TODO: Cached/Bypassed load reserve and store conditionals

    $display("Testbench: Unknown type load");
    load(REGION_BRAM1_BEGIN, 3'b011);
    `assert_equal(c_response, `CACHE_RESPONSE_UNKNOWNTYPE)
    load(REGION_BRAM1_BEGIN, 3'b110);
    `assert_equal(c_response, `CACHE_RESPONSE_UNKNOWNTYPE)
    load(REGION_BRAM1_BEGIN, 3'b111);
    `assert_equal(c_response, `CACHE_RESPONSE_UNKNOWNTYPE)
    // TODO: Add for other types

    $display("Testbench: Unknown type store");
    store(REGION_BRAM1_BEGIN, `STORE_WORD, 32'hFFFFFFFF); // Store something
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    store(REGION_BRAM1_BEGIN, 2'b11, 32'h0000FF00); // Errornous store
    `assert_equal(c_response, `CACHE_RESPONSE_UNKNOWNTYPE)
    load(REGION_BRAM1_BEGIN, `LOAD_WORD); // Check to be correct
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    convert_addr_to_mem_location(REGION_BRAM1_BEGIN, mem_location, mem_location_exists);

    `assert_equal(mem_location_exists, 1)
    `assert_equal(mem[mem_location], 32'hFFFFFFFF)

    $display("Testbench: Accessfault load ouside BRAM");
    load(REGION_BRAM1_END, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_ACCESSFAULT)

    $display("Testbench: Accessfault execute outside BRAM");
    execute(REGION_BRAM1_END);
    `assert_equal(c_response, `CACHE_RESPONSE_ACCESSFAULT)
    // TODO: load_conditional


    // TODO: $display("Testbench: Accessfault store/store_conditional outside Router");

    $display("Testbench: Accessfault store ouside BRAM");
    store(REGION_BRAM1_END, `STORE_WORD, 32'h00FF01FF);
    `assert_equal(c_response, `CACHE_RESPONSE_ACCESSFAULT)


    // TODO: Add tests below
    // TODO: $display("Testbench: ");



    // TODO: $display("Testbench: Basic flush test");

    $display("Testbench: Testing MMU satp should not apply to machine");
    
    

    csr_mcurrent_privilege = 3;
    csr_mstatus_mprv = 0;

    csr_mstatus_mxr = 0;
    csr_mstatus_sum = 0;
    csr_mstatus_mpp = 0;

    csr_satp_mode = 1;
    csr_satp_ppn = (REGION_BRAM0_BEGIN[33:12]);


    // Set BRAM1 tree:
    //  First element Missaligned
    //  Second element the base of 3 level deep leaf @ second tree location -> pagefault
    //  4K Page towards Accessfault
    //  Megapage readable, dirty, access
    //  Megapage writable, readable, dirty, access
    //  Megapage Readable, writable, executable, dirty
    //  Megapage Readable, writable, executable, access
    //  Megapage executable only
    //  Megapage all set, USER
    //  Ponter to leaf @ third tree location


    // Set missaligned megapage
    store(REGION_BRAM0_BEGIN, `STORE_WORD, (REGION_BRAM1_BEGIN));
    
    // Check for tree to be updated
    load(REGION_BRAM0_BEGIN, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS);

    $display("Testbench: Testing MMU satp should apply to supervisor");
    csr_mcurrent_privilege = 1;
    
    load(0, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_PAGEFAULT)
    
    
    
    $display("Testbench: Testing MMU satp should apply to machine with mprv (pp = supervisor, user)");
    csr_mstatus_mprv = 1;
    csr_mstatus_mpp = 1;
    load(0, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_PAGEFAULT);
    
    csr_mstatus_mpp = 0;
    load(0, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_PAGEFAULT);
    
    $display("Testbench: Testing MMU satp should apply to user");
    csr_mcurrent_privilege = 0;
    load(0, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_PAGEFAULT);


    $display("Testbench: PTW towards out of memory");
    csr_mcurrent_privilege = 3;
    csr_mstatus_mprv = 0;
    store(REGION_BRAM0_BEGIN, `STORE_WORD, 32'h1000_0000 | PTE_VALID | PTE_READ | PTE_ACCESS);
    
    csr_mcurrent_privilege = 1;
    load(0, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_ACCESSFAULT);


    $display("Testbench: Leaf");
    csr_mcurrent_privilege = 3;
    csr_mstatus_mprv = 0;

    flush();
    store(REGION_BRAM0_BEGIN, `STORE_WORD, ((REGION_BRAM1_BEGIN + 4096) >> 2) | NEXT_LEVEL_POINTER);
    store(REGION_BRAM1_BEGIN + 4096, `STORE_WORD, ((REGION_BRAM1_BEGIN) >> 2) | RWX);
    
    
    csr_mcurrent_privilege = 1;
    store(0, `STORE_WORD, 32'hFFFFFFFF);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS);

    load(0, `LOAD_WORD);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS);
    
    // $display("User can access user memory");
    // csr_mcurrent_privilege = 0;

    // Supervisor can't access user memory
    // csr_mstatus_sum = 0;
    // csr_mcurrent_privilege = 1;

    // Supervisor can access user memory with sum=1
    // armleocpu_cache->csr_mcurrent_privilege = 3;
    // csr_mstatus_mprv = 1;
    // csr_mstatus_mpp = 1;
    // csr_mstatus_sum = 1;

    // PTW Access 4k leaf out of memory

    // PTW Access 3 level leaf pagefault

    // Test leaf readable
    // mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_READ;

    // Test leaf unreadable
    //mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_EXECUTE;

    // Test leaf unreadable, execute, mxr
    // csr_mstatus_mxr = 1;
    

    // TODO: Add even more tests
    
    // TODO: $display("Testbench: Stress test");


    /*
    
    test_begin(4, "Testing MMU satp should apply to supervisor");
    armleocpu_cache->csr_mcurrent_privilege = 1;
    load_checked(0, LOAD_WORD, CACHE_RESPONSE_DONE);
    test_end();

    test_begin(5, "Testing MMU satp should apply to user");
    armleocpu_cache->csr_mcurrent_privilege = 0;
    load(0, LOAD_WORD);
    load_checked(0, LOAD_WORD, CACHE_RESPONSE_PAGEFAULT);
    test_end();

    test_begin(6, "User can access user memory");
    armleocpu_cache->csr_mcurrent_privilege = 0;
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_DONE);
    test_end();
    
    test_begin(7, "Supervisor can't access user memory");
    armleocpu_cache->csr_mstatus_sum = 0;
    armleocpu_cache->csr_mcurrent_privilege = 1;
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_PAGEFAULT);
    test_end();


    test_begin(8, "Supervisor can access user memory with sum=1");
    armleocpu_cache->csr_mcurrent_privilege = 1;
    armleocpu_cache->csr_mstatus_sum = 1;
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_DONE);
    dummy_cycle();
    armleocpu_cache->csr_mcurrent_privilege = 3;
    armleocpu_cache->csr_mstatus_mprv = 1;
    armleocpu_cache->csr_mstatus_mpp = 1;
    armleocpu_cache->csr_mstatus_sum = 1;
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_DONE);
    test_end();
    
    test_begin(9, "PTW Access out of memory");
    set_satp(1, MEMORY_WORDS*4 >> 12);
    
    load_checked(1 << 22, LOAD_WORD, CACHE_RESPONSE_ACCESSFAULT);
    test_end();
    


    test_begin(10, "PTW Access 4k leaf out of memory");
    set_satp(1, 4);
    load(2 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_ACCESSFAULT);
    dummy_cycle();
    cout << "10 - PTW Access 4k leaf out of memory done" << endl;
    

    
    test_begin(11, "PTW Access 3 level leaf pagefault");
    set_satp(1, 4);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();
    
    test_begin(12, "Test leaf readable");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_READ;
    set_satp(1, 4);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    test_end();


    test_begin(13, "Test leaf unreadable");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_EXECUTE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();

    test_begin(14, "Test leaf unreadable, execute, mxr");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_EXECUTE;
    armleocpu_cache->csr_mstatus_mxr = 1;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    test_end();

    test_begin(15, "Test leaf access bit");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_DIRTY | PTE_ACCESS | PTE_READ | PTE_EXECUTE | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_DONE);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);

    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_DIRTY | PTE_READ | PTE_EXECUTE | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    dummy_cycle();
    test_end();

    
    test_begin(16, "Test leaf dirty bit");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_DIRTY | PTE_ACCESS | PTE_READ | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);

    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_READ | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();
    
    
    // Test writable bit
    test_begin(17, "Test leaf write bit");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ | PTE_WRITE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_DONE);

    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();

    // Test executable bit
    test_begin(17, "Test leaf executable bit");
    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ | PTE_EXECUTE;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_DONE);

    mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ;
    flush();
    response_check(CACHE_RESPONSE_DONE);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();

    test_begin(18, "Test invalid pte");
    mem[(5 << 10)] = (100 << 10) | PTE_ACCESS | PTE_DIRTY | PTE_READ | PTE_EXECUTE | PTE_WRITE;
    flush();
    load(3 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    execute(3 << 22);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    store(3 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();


    test_begin(18, "Test Missaligned pte");
    //mem[(5 << 10)] = (100 << 10) | PTE_VALID | PTE_ACCESS | PTE_DIRTY | PTE_READ | PTE_EXECUTE | PTE_WRITE;
    flush();
    load(4 << 22, LOAD_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    execute(4 << 22);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    store(4 << 22, 0xFF, STORE_WORD);
    response_check(CACHE_RESPONSE_PAGEFAULT);
    test_end();
    */

    /*
    csr_mcurrent_privilege = 3;
    csr_mstatus_mprv = 0;

    for(n = REGION_BRAM1_BEGIN; n < REGION_BRAM1_END; n = n + 4) begin
        store(n, `STORE_WORD, 32'h0000_0000);
    end

    for(n = REGION_BRAM0_BEGIN; n < REGION_BRAM0_END; n = n + 4) begin
        store(n, `STORE_WORD, 32'h0000_0000);
    end

    for(n = 0; n < 10000; n = n + 1) begin
        is_bypassed = $urandom() & 1;
        addr = ($urandom() % DEPTH) << 2;
        is_load = $urandom() & 1;
        word = $urandom();

        if(is_load) begin
            load(addr + (is_bypassed ? REGION_BRAM0_BEGIN : REGION_BRAM1_BEGIN), `LOAD_WORD);
        end else begin
            store(addr + (is_bypassed ? REGION_BRAM0_BEGIN : REGION_BRAM1_BEGIN), `STORE_WORD, word);
        end
        @(negedge clk);
    end
    */
    n = 0;
    for(n = 0; n < 16 + 2; n = n + 1) begin
        @(negedge clk);
    end
    
    // TODO: Write tests
    $finish;
end


endmodule