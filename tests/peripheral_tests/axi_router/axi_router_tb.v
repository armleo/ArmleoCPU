`define TIMEOUT 2000000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"


localparam ADDR_WIDTH = 16;
localparam DATA_WIDTH = 32;
localparam DATA_STROBES = DATA_WIDTH/8;
localparam DEPTH = 1024;
localparam ID_WIDTH = 4;


// BRAM0 <-memory0_axi-> EXCLUSIVE_ACCESS <-downstream0_axi-> router downstream0 port + 
//                                                                                    + router <-> upstream
// BRAM1 <-memory1_axi-> EXCLUSIVE_ACCESS <-downstream1_axi-> router downstream1 port +


reg upstream_axi_awvalid;
wire upstream_axi_awready;
reg [ADDR_WIDTH-1:0] upstream_axi_awaddr;
reg [7:0] upstream_axi_awlen;
reg [2:0] upstream_axi_awsize;
reg [1:0] upstream_axi_awburst;
reg [ID_WIDTH-1:0] upstream_axi_awid;
reg upstream_axi_awlock;
reg [2:0] upstream_axi_awprot;

reg upstream_axi_wvalid;
wire upstream_axi_wready;
reg [DATA_WIDTH-1:0] upstream_axi_wdata;
reg [DATA_STROBES-1:0] upstream_axi_wstrb;
reg upstream_axi_wlast;

wire upstream_axi_bvalid;
reg upstream_axi_bready;
wire [1:0] upstream_axi_bresp;
wire [ID_WIDTH-1:0] upstream_axi_bid;


reg upstream_axi_arvalid;
wire upstream_axi_arready;
reg [ADDR_WIDTH-1:0] upstream_axi_araddr;
reg [7:0] upstream_axi_arlen;
reg [2:0] upstream_axi_arsize;
reg [1:0] upstream_axi_arburst;
reg [ID_WIDTH-1:0] upstream_axi_arid;
reg upstream_axi_arlock;
reg [2:0] upstream_axi_arprot;

wire upstream_axi_rvalid;
reg upstream_axi_rready;
wire [1:0] upstream_axi_rresp;
wire [DATA_WIDTH-1:0] upstream_axi_rdata;
wire [ID_WIDTH-1:0] upstream_axi_rid;
wire upstream_axi_rlast;


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

/*
`DECLARE_AXI_WIRES(downstream0_axi_) 

`DECLARE_AXI_WIRES(downstream1_axi_) 

wire downstream0_axi_arlock, downstream0_axi_awlock;

wire downstream1_axi_arlock, downstream1_axi_awlock;
*/

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



// Memory Map
// 0x1000-0x2000 -> BRAM1 0x0000
// 0x2000-0x3000 -> BRAM0 0x0000
// 0x3000-0x4000 -> BRAM0 0x0000
// 0x4000-0x5000 -> BRAM1 0x0000
// Note: BRAM numbers are intentionally swapped


localparam OPT_NUMBER_OF_CLIENTS = 2;
localparam OPT_NUMBER_OF_CLIENTS_CLOG2 = $clog2(OPT_NUMBER_OF_CLIENTS);
localparam REGION_COUNT = 4;
localparam [REGION_COUNT * OPT_NUMBER_OF_CLIENTS_CLOG2 - 1:0]           
                                                REGION_CLIENT_NUM       = {1'b1    , 1'b0    , 1'b0    , 1'b1    };
localparam [REGION_COUNT * ADDR_WIDTH - 1:0]    REGION_BASE_ADDRS       = {16'h1000, 16'h2000, 16'h3000, 16'h4000};
localparam [REGION_COUNT * ADDR_WIDTH - 1:0]    REGION_END_ADDRS        = {16'h2000, 16'h3000, 16'h4000, 16'h5000};
localparam [REGION_COUNT * ADDR_WIDTH - 1:0]    REGION_CLIENT_BASE_ADDRS= {16'h1000, 16'h2000, 16'h3000, 16'h4000};


function addr_in_range;
input [ADDR_WIDTH-1:0] addr;
begin
    addr_in_range = (addr >= 16'h1000 && addr < 16'h5000);
    if(addr >= 16'h1000 && addr < 16'h2000)
        addr_in_range = (addr - 16'h1000) < (DEPTH << 2); // BRAM1, starting @ mem[DEPTH]
    else if(addr >= 16'h2000 && addr < 16'h3000) begin
        addr_in_range = (addr - 16'h2000) < (DEPTH << 2); // BRAM0, starting @ mem[0x0000]
    end if(addr >= 16'h3000 && addr < 16'h4000) begin
        addr_in_range = (addr - 16'h3000) < (DEPTH << 2); // BRAM0, starting @ mem[0x0000]
    end if(addr >= 16'h4000 && addr < 16'h5000) begin
        addr_in_range = (addr - 16'h4000) < (DEPTH << 2); // BRAM1, starting @ mem[DEPTH]
    end
end endfunction

function [ADDR_WIDTH-1:0] convert_addr;
input [ADDR_WIDTH-1:0] addr;
begin
    if(addr >= 16'h1000 && addr < 16'h2000)
        convert_addr = addr - 16'h1000 + (DEPTH << 2); // BRAM1, starting @ mem[DEPTH]
    else if(addr >= 16'h2000 && addr < 16'h3000) begin
        convert_addr = addr - 16'h2000 + 0; // BRAM0, starting @ mem[0x0000]
    end if(addr >= 16'h3000 && addr < 16'h4000) begin
        convert_addr = addr - 16'h3000 + 0; // BRAM0, starting @ mem[0x0000]
    end if(addr >= 16'h4000 && addr < 16'h5000) begin
        convert_addr = addr - 16'h4000 + (DEPTH << 2); // BRAM1, starting @ mem[DEPTH]
    end
end endfunction

task debug_log_addr_region;
input [ADDR_WIDTH-1:0] addr;
begin
    integer i;
    reg [OPT_NUMBER_OF_CLIENTS_CLOG2-1:0] w_client_select_nxt;
    reg [OPT_NUMBER_OF_CLIENTS_CLOG2-1:0] client_num;
    reg wstate_nxt;
    reg [ADDR_WIDTH-1:0] waddr_nxt;
    reg [ADDR_WIDTH-1:0] base_addr;
    reg [ADDR_WIDTH-1:0] end_addr;
    reg [ADDR_WIDTH-1:0] client_base_addr;

    wstate_nxt = 0;
    for(i = 0; i < REGION_COUNT; i = i + 1) begin
        
        base_addr = REGION_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)];
        end_addr = REGION_END_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)];
        client_num = REGION_CLIENT_NUM[`ACCESS_PACKED(i, OPT_NUMBER_OF_CLIENTS_CLOG2)];
        client_base_addr = REGION_CLIENT_BASE_ADDRS[`ACCESS_PACKED(i, ADDR_WIDTH)];
        //$display("Analyzing region num = %d, base=0x%x, end=0x%x, clientnum=0x%x, clientbaseaddr=0x%x",
        //    i, base_addr, end_addr, client_num, client_base_addr);
        if((addr >= base_addr)
            && 
                (addr < end_addr)) begin
            w_client_select_nxt = client_num;
                
            wstate_nxt = 1;
            waddr_nxt = addr - client_base_addr;
        end
    end
    if(wstate_nxt) begin
        $display("[REGIONS] Region matched: addr = 0x%x, w_client_select=%d, waddr_nxt = 0x%x",
                addr, w_client_select_nxt, waddr_nxt);
    end else begin
        $display("[REGIONS] No region matched: addr = 0x%x", addr);
    end
end endtask

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

    `CONNECT_AXI_BUS(upstream_axi_, upstream_axi_),
    .upstream_axi_arlock(upstream_axi_arlock),
    .upstream_axi_arprot(upstream_axi_arprot),
    .upstream_axi_awlock(upstream_axi_awlock),
    .upstream_axi_awprot(upstream_axi_awprot),



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

reg [31:0] mem [DEPTH*2-1:0]; // One for DEPTH BRAM0 and one DEPTH BRAM1

//-------------AW---------------
task aw_noop; begin
    upstream_axi_awvalid = 0;
end endtask

task aw_op;
input [ADDR_WIDTH-1:0] addr;
input [2:0] id;
input lock;
begin
    upstream_axi_awvalid = 1;
    upstream_axi_awaddr = addr;
    upstream_axi_awlen = 0;
    upstream_axi_awsize = 2; // 4 bytes
    upstream_axi_awburst = 2'b01; // Increment
    upstream_axi_awlock = lock;
    upstream_axi_awid = id;
    upstream_axi_awprot = 3'b111;
end endtask

task aw_expect;
input awready;
begin
    `assert_equal(upstream_axi_awready, awready);
end endtask

//-------------W---------------
task w_noop; begin
    upstream_axi_wvalid = 0;
end endtask

task w_op;
input [DATA_WIDTH-1:0] wdata;
input [DATA_STROBES-1:0] wstrb;
begin
    upstream_axi_wvalid = 1;
    upstream_axi_wdata = wdata;
    upstream_axi_wstrb = wstrb;
    upstream_axi_wlast = 1;
end endtask

task w_expect;
input wready;
begin
    `assert_equal(upstream_axi_wready, wready)
end endtask

//-------------B---------------
task b_noop; begin
    upstream_axi_bready = 0;
end endtask

task b_expect;
input valid;
input [1:0] resp;
input [3:0] id;
begin
    `assert_equal(upstream_axi_bvalid, valid)
    if(valid) begin
        `assert_equal(upstream_axi_bresp, resp)
    end
end endtask

//-------------AR---------------
task ar_noop; begin
    upstream_axi_arvalid = 0;
end endtask

task ar_op; 
input [ADDR_WIDTH-1:0] addr;
input [3:0] id;
input [1:0] burst;
input [7:0] len;
input lock;
begin
    upstream_axi_arvalid = 1;
    upstream_axi_araddr = addr;
    upstream_axi_arlen = len;
    upstream_axi_arsize = 2; // 4 bytes
    upstream_axi_arburst = burst; // Increment
    upstream_axi_arid = id;
    upstream_axi_arlock = lock;
    upstream_axi_arprot = 3'b111;
end endtask

task ar_expect;
input ready;
begin
    `assert_equal(upstream_axi_arready, ready)
end endtask

//-------------R---------------
task r_noop; begin
    upstream_axi_rready = 0;
end endtask

task r_expect;
input valid;
input [1:0] resp;
input [31:0] data;
input [3:0] id;
input last;
begin
    `assert_equal(upstream_axi_rvalid, valid)
    if(valid) begin
        `assert_equal(upstream_axi_rresp, resp)
        if(resp <= 2'b01)
            `assert_equal(upstream_axi_rdata, data)
        `assert_equal(upstream_axi_rid, id)
        `assert_equal(upstream_axi_rlast, last)
    end
end endtask


//-------------Others---------------
task poke_all;
input aw;
input w;
input b;

input ar;
input r; begin
    if(aw === 1)
        aw_noop();
    if(w === 1)
        w_noop();
    if(b === 1)
        b_noop();
    if(ar === 1)
        ar_noop();
    if(r === 1)
        r_noop();
end endtask

task expect_all;
input aw;
input w;
input b;

input ar;
input r; begin
    if(aw === 1)
        aw_expect(0);
    if(w === 1)
        w_expect(0);
    if(b === 1)
        b_expect(0, 2'bZZ, 4'bZZZZ);
    if(ar === 1)
        ar_expect(0);
    if(r === 1)
        r_expect(0, 2'bZZ, 32'hZZZZ_ZZZZ, 2'bZZ, 1'bZ);
end endtask


integer k;


integer timeout = 0;


reg [ADDR_WIDTH-1:0] mask;
reg [ADDR_WIDTH-1:0] addr_reg;
reg [1:0] resp_expected;
reg reservation_valid;
reg [ADDR_WIDTH-1:0] reservation_addr;

task write;
input [ADDR_WIDTH-1:0] addr;
input [3:0] id;
input [DATA_WIDTH-1:0] wdata;
input [DATA_STROBES-1:0] wstrb;
input lock;
begin
    
    if(lock) begin
        if(reservation_valid && reservation_addr == addr) begin
            resp_expected = addr_in_range(addr) ? 2'b01 : 2'b11;
            // EXOKAY or SLVERR
            reservation_valid = 0;
        end else begin
            // OKAY or SLVERR
            resp_expected = addr_in_range(addr) ? 2'b00 : 2'b11;
        end
    end else begin
        if(reservation_valid && reservation_addr == addr) begin
            reservation_valid = 0;
        end
        // OKAY or SLVERR
        resp_expected = addr_in_range(addr) ? 2'b00 : 2'b11;
    end

    $display("[AXI WRITE] Write requested addr=0x%x, id=0x%x, wdata=0x%x, wstrb=0b%b, lock=%d, resp_expected=0b%b",
        addr, id, wdata, wstrb, lock, resp_expected
    );

    debug_log_addr_region(addr);
    // AW request
    @(negedge clk)
    poke_all(1,1,1, 1,1);
    aw_op(addr, id, lock); // Access word = 9, last word in storage

    $display("[AXI WRITE] Waiting for AW READY");
    timeout = 0;
    while(!upstream_axi_awready) begin
        @(posedge clk);
        timeout = timeout + 1;
        if(timeout == 10) begin
            `assert_equal(0, 1)
        end
    end
    
    @(posedge clk);
    aw_noop();
    expect_all(0, 0, 1, 1, 1);

    // W request
    @(negedge clk);
    w_op(wdata, wstrb);
    @(posedge clk)
    w_expect(1);
    expect_all(1, 0, 1, 1, 1);

    // B stalled
    @(negedge clk);
    upstream_axi_bready = 0;
    w_noop();
    @(posedge clk);
    b_expect(1, resp_expected, id);
    expect_all(1, 1, 0, 1, 1);

    // B done
    @(negedge clk);
    upstream_axi_bready = 1;
    w_noop();
    @(posedge clk);
    b_expect(1, resp_expected, id);
    expect_all(1, 1, 0, 1, 1);
    
    if((lock && resp_expected == `AXI_RESP_EXOKAY) || (!lock && resp_expected == `AXI_RESP_OKAY)) begin
        for(k = 0; k < DATA_STROBES; k = k + 1)
            if(wstrb[k])
                mem[convert_addr(addr) >> 2][k*8 +: 8] = wdata[k*8 +: 8];
    end
    @(negedge clk);
    poke_all(1,1,1, 1,1);
end
endtask

task read;
input [ADDR_WIDTH-1:0] addr;
input [1:0] burst;
input [7:0] len;
input [3:0] id;
input lock;
begin
    integer i;

    if(lock) begin
        reservation_valid = 1;
        reservation_addr = addr;
    end
    $display("[AXI READ] Read requested addr=0x%x, burst=0x%x, len=0x%x, id=0x%x, lock=%d",
        addr, burst, len, id, lock);
    mask = (len << 2);
    // AR request
    @(negedge clk)
    poke_all(1,1,1, 1,1);
    ar_op(addr, id, burst, len, lock); // Access word = 9, last word in storage
    $display("[AXI READ] Waiting for ARREADY");
    timeout = 0;
    while(!upstream_axi_arready) begin
        @(posedge clk);
        timeout = timeout + 1;
        if(timeout == 10) begin
            `assert_equal(0, 1)
        end
    end
    ar_expect(1);
    


    
    addr_reg = addr;

    for(i = 0; i < len+1; i = i + 1) begin
        // R response stalled
        @(negedge clk);
        upstream_axi_rready = 0;
        ar_noop();
        @(posedge clk);
        if(lock)
            resp_expected = (addr_in_range(addr_reg)) ? 2'b01 : 2'b11;
        else
            resp_expected = (addr_in_range(addr_reg)) ? 2'b00 : 2'b11;
        r_expect(1,
            resp_expected,
            mem[convert_addr(addr_reg) >> 2],
            id,
            i == len);
        expect_all(1, 1, 1, 1, 0);

        // R response accepted
        @(negedge clk);
        upstream_axi_rready = 1;
        @(posedge clk)
        r_expect(1,
            resp_expected,
            mem[convert_addr(addr_reg) >> 2],
            id,
            i == len);
        expect_all(1, 1, 1, 1, 0);

        $display("[AXI READ] mem addr = %d, read expected = 0x%x",
                    convert_addr(addr_reg) >> 2, mem[convert_addr(addr_reg) >> 2]);
        debug_log_addr_region(addr_reg);
        if(burst == 2'b10) // wrap
            addr_reg = (addr_reg & ~mask) | ((addr_reg + 4) & mask);
        else // incr
            addr_reg = addr_reg + 4;
    end
    @(negedge clk);
    poke_all(1,1,1, 1,1);
    $display("[AXI READ] Read done addr = 0x%x", addr);
end
endtask


integer i;
integer word;

initial begin
    
    @(posedge rst_n)

    @(negedge clk)
    poke_all(1,1,1, 1,1);
    
    $display("[TB] Writing begin");
    write(16'h1000, 3, 32'hFF00FF00, 4'hF, 0);
    write(16'h0000, 3, 32'hFF00FF00, 4'hF, 0);

    for(i = 0; i < DEPTH*2; i = i + 1)
        $display("[TB] mem[%d] = 0x%x;", i, mem[i]); // BRAM1;

    read(16'h0000, 
        2'b01, //incr
        8'b01, // two words,
        4'b0001, // id
        1'b0 // lock
    );

    read(16'h1000, 
        2'b01, //incr
        8'b01, // two words,
        4'b0001, // id
        1'b0 // lock
    );

    write(16'h2000, 3, 32'hFFFEFFFF, 4'hF, 0);
    read(16'h2000, 
        2'b01, //incr
        8'b01, // two words,
        4'b0001, // id
        1'b0 // lock
    );

    for(i = 0; i < DEPTH*2; i = i + 1)
        $display("[TB] mem[%d] = 0x%x;", i, mem[i]); // BRAM1;
    read(16'h2000, 
        2'b01, //incr
        8'b01, // two words,
        4'b0001, // id
        1'b0 // lock
    );
    // Read hit
    // Read not hit

    // Read and write parallel with one cycle difference

    // Read and write stress test

    
    $display("[TB] Writing done");
    
    // Test cases:
    
    $display("[TB] AR start w/ len = 0");
    read(16'h1000 + (9 << 2), //addr
        2'b01, //burst
        0, //len
        4, //id
        0); //lock
    

    $display("[TB] AR start w/ lock, len = 0");
    read(16'h1000 + (8 << 2), //addr
        2'b01, //burst
        0, //len
        4, //id
        1); //lock

    
    $display("[TB] AW start w/ len = 0");
    write(16'h1000 + (9 << 2), //addr
                4'hF, //id
                32'hFFFF_FFFF, // wdata
                4'b1111, // wstrb
                0); // lock
    $display("[TB] AW start w/ lock, len = 0");
    write(16'h1000 + (8 << 2), //addr
                4'hF, //id
                32'hFFFF_FFFF, // wdata
                4'b1111, // wstrb
                1); // lock

    $display("[TB] AW start w/ lock, len = 0, failing, because no lock");
    write(16'h1000 + (8 << 2), //addr
                4'hF, //id
                32'hFFFF_FFFF, // wdata
                4'b1111, // wstrb
                1); // lock
    
    $display("[TB] AR start w/ lock, len = 0");
    read(16'h1000 + (8 << 2), //addr
        2'b01, //burst
        0, //len
        4, //id
        1); //lock
    $display("[TB] AR start w/ lock, len = 0");
    read(16'h1000 + (9 << 2), //addr
        2'b01, //burst
        0, //len
        4, //id
        1); //lock
    $display("[TB] AW start w/ lock, len = 0");
    write(16'h1000 + (9 << 2), //addr
                4'hF, //id
                32'hFFFF_FFFF, // wdata
                4'b1111, // wstrb
                1); // lock


    // Memory test, no locks at all
    write(16'h1000 + (9 << 2), 4, 32'hFF00FF00, 4'b0111, 0);
    write(16'h1000 + (9 << 2), 4, 32'hFF00FF00, 4'b1111, 0);
    write(16'h1000 + (9 << 2), 4, 32'hFE00FF00, 4'b0111, 0);
    

    read(16'h1000 + (9 << 2), 2'b01, 0, 4, 0); //INCR test

    
    for(i = 0; i < DEPTH; i = i + 1) begin
        write(16'h1000 + (i << 2), $urandom(), 32'h0000_0000, 4'b1111, 0);
    end
    $display("[TB] Full write done");
    
    for(i = 0; i < 100; i = i + 1) begin
        word = $urandom() % (DEPTH * 2);
        
        write(16'h1000 + (word << 2), //addr
            $urandom() & 4'hF, //id
            $urandom() & 32'hFFFF_FFFF, // data
            4'b1111, 0);
    end
    $display("[TB] Test write done");
    
    $display("[TB] Data dump:");
    for(i = 0; i < DEPTH; i = i + 1) begin
        $display("[TB] mem[%d] = 0x%x or %d", i, mem[i], mem[i]);
    end


    for(i = 0; i < DEPTH - (16 * 4); i = i + 1) begin
        read(16'h1000 + (i << 2), //addr
            ($urandom() & 1) ? 2'b10 : 2'b01, // burst
            (1 << ($urandom() % 5)) - 1, // len
            $urandom() & 4'hF // id
            , 0);
    end
    $display("[TB] Test Read done");

    $display("[TB] Random read/write test started");
    for(i = 0; i < 1000; i = i + 1) begin
        word = $urandom() % (DEPTH - (16 * 4)) + (($urandom() & 1) * DEPTH);
            // Dont allow 4K crossing, because maximum 16 words can be requested

        if($urandom() & 1) begin
            write(16'h1000 + (word << 2), //addr
                $urandom() & 4'hF, //id
                $urandom() & 32'hFFFF_FFFF, // data
                $urandom() & 4'b1111, 0);
        end else begin
            read(16'h1000 + (word << 2), //addr
                ($urandom() & 1) ? 2'b10 : 2'b01, // burst
                (1 << ($urandom() % 5)) - 1, // len
                $urandom() & 4'hF // id
                , 0);
        end
    end



    @(negedge clk);
    upstream_axi_bready = 0;
    

    @(posedge clk);
    
    @(negedge clk)
    @(negedge clk)
    $finish;
end

endmodule