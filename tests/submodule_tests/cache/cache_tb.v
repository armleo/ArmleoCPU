`define TIMEOUT 10000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"


localparam ADDR_WIDTH = 34;
// Note: If ADDR WIDTH is changed then values below need changing too
localparam DATA_STROBES = 4;
localparam DATA_WIDTH = 32;
localparam ID_WIDTH = 1;
localparam DEPTH = 2048;


// Memory Map
// 0x1000-0x3000 -> BRAM0 0x0000
// 0x80002000-0x80004000 -> BRAM1 0x0000

localparam OPT_NUMBER_OF_CLIENTS = 2;
localparam OPT_NUMBER_OF_CLIENTS_CLOG2 = $clog2(OPT_NUMBER_OF_CLIENTS);
localparam REGION_COUNT = 2;
localparam [REGION_COUNT * OPT_NUMBER_OF_CLIENTS_CLOG2 - 1:0]           
                                                REGION_CLIENT_NUM       = {1'b1         , 1'b0    };
localparam [REGION_COUNT * ADDR_WIDTH - 1:0]    REGION_BASE_ADDRS       = {34'h80002000 , 34'h1000};
localparam [REGION_COUNT * ADDR_WIDTH - 1:0]    REGION_END_ADDRS        = {34'h80004000 , 34'h3000};
localparam [REGION_COUNT * ADDR_WIDTH - 1:0]    REGION_CLIENT_BASE_ADDRS= {34'h80002000 , 34'h1000};



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

    // TODO: One by one connection to downstream_axi_ signals
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

task flush;
begin
    c_cmd = `CACHE_CMD_FLUSH_ALL;
    @(negedge clk)
    `assert_equal(c_done, 1)
    `assert_equal(cache.os_active, 1)
    `assert_equal(cache.os_cmd_flush, 1)
    `assert_equal(cache.os_cmd, `CACHE_CMD_FLUSH_ALL)
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    c_cmd = `CACHE_CMD_NONE;
end
endtask

task write;
input [31:0] addr;
input [1:0] store_type;
input [31:0] store_data;
begin
    integer timeout;
    c_cmd = `CACHE_CMD_STORE;
    c_address = addr;
    c_store_type = store_type;
    c_store_data = store_data;
    @(negedge clk);
    timeout = 0;
    while(!c_done) begin
        @(negedge clk);
        timeout = timeout + 1;
        if(timeout == 1000) begin
            `assert_equal(0, 1)
        end
    end
    c_cmd = `CACHE_CMD_NONE;
    // Leave checks to caller
end
endtask

task read;
input execute;
input lock;
input [31:0] addr;
input [2:0] load_type;
begin
    integer timeout;
    c_cmd = lock ? `CACHE_CMD_LOAD_RESERVE : (execute ? `CACHE_CMD_EXECUTE : `CACHE_CMD_LOAD);
    c_address = addr;
    c_load_type = load_type;
    @(negedge clk);
    timeout = 0;
    while(!c_done) begin
        @(negedge clk);
        timeout = timeout + 1;
        if(timeout == 1000) begin
            `assert_equal(0, 1)
        end
    end
    c_cmd = `CACHE_CMD_NONE;
    // Leave checks to caller
end
endtask


integer n;

initial begin
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

    @(negedge clk)

    
    $display("Testbench: Flush test");
    flush();

    $display("Testbench: Write test");
    @(negedge clk) // After flush skip one cycle
    write(34'h1000, `STORE_WORD, 32'hFF00FF00);
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    // TODO: Implement below as check mem
    $display(bram0.backstorage.mem_generate_for[0].storage.storage[0]);
    $display(bram0.backstorage.mem_generate_for[8].storage.storage[0]);
    $display(bram0.backstorage.mem_generate_for[16].storage.storage[0]);
    $display(bram0.backstorage.mem_generate_for[24].storage.storage[0]);
    

    @(negedge clk) // After write skip one cycle
    $display("Testbench: Read Reserve test");
    read(0, // execute?
        0, // atomic?
        34'h1000, // addr?
        `LOAD_WORD // type?
        );
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    `assert_equal(c_load_data, 32'hFF00FF00)

    @(negedge clk) // After write skip one cycle
    $display("Testbench: Read Reserve test");
    read(0, // execute?
        1, // atomic?
        34'h1000, // addr?
        `LOAD_WORD // type?
        );
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    `assert_equal(c_load_data, 32'hFF00FF00)


    @(negedge clk) // After write skip one cycle
    $display("Testbench: Write to cached location");
    write(
        34'h80002000, // addr?
        `STORE_WORD, // type?
        32'h12345678);
    
    $display("0x%x", {bram1.backstorage.mem_generate_for[24].storage.storage[0],
        bram1.backstorage.mem_generate_for[16].storage.storage[0],
        bram1.backstorage.mem_generate_for[8].storage.storage[0],
        bram1.backstorage.mem_generate_for[0].storage.storage[0]});
    

    $display("Testbench: Read from cached location");
    @(negedge clk) // After write skip one cycle
    read(0, // execute?
        0, // atomic?
        34'h80002000, // addr?
        `LOAD_WORD // type?
        );
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    `assert_equal(c_load_data, 32'h12345678)

    write(
        34'h80002000, // addr?
        `STORE_WORD, // type?
        32'h12345678);
    @(negedge clk)
    write(
        34'h80002004, // addr?
        `STORE_WORD, // type?
        32'h56781234);
    
    read(0, // execute?
        0, // atomic?
        34'h80002000, // addr?
        `LOAD_WORD // type?
        );
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    `assert_equal(c_load_data, 32'h12345678)

    read(0, // execute?
        0, // atomic?
        34'h80002004, // addr?
        `LOAD_WORD // type?
        );
    `assert_equal(c_response, `CACHE_RESPONSE_SUCCESS)
    `assert_equal(c_load_data, 32'h56781234)

    n = 0;
    for(n = 0; n < 16 + 2; n = n + 1) begin
        @(negedge clk);
    end
    
    // TODO: Write tests
    $finish;
end


endmodule