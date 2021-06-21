`timescale 1ns/1ns

module axi_router_testbench;


initial begin
    $dumpfile(`SIMRESULT);
    //$dumpvars;
    $dumpvars(0, axi_router_testbench);
    #2000000
    $display("!ERROR! End reached but test is not done");
    $fatal;
end

reg clk = 0;
reg rst_n = 1;
reg clk_enable = 0;
initial begin
    clk_enable = 1;
    rst_n = 0;
    #20 rst_n = 1;
end
always begin
    #10 clk <= clk_enable ? !clk : clk;
end

`include "assert.vh"
`include "armleocpu_defines.vh"



localparam ADDR_WIDTH = 16;
localparam DATA_WIDTH = 32;
localparam DATA_STROBES = DATA_WIDTH/8;
localparam DEPTH = 10;
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

wire downstream1_axi_bvalid;
wire downstream1_axi_bready;
wire [1:0] downstream1_axi_bresp;


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
    .cpu_axi_rdata      (downstream0_axi_rdata)

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
    .cpu_axi_rdata      (downstream1_axi_rdata)
);



// Memory Map
// 0x1000-0x2000 -> BRAM1 0x0000
// 0x2000-0x3000 -> BRAM0 0x0000
// 0x3000-0x4000 -> BRAM0 0x0000
// 0x4000-0x5000 -> BRAM1 0x0000
// Note: BRAM numbers are intentionally swapped


armleocpu_axi_router #(
    .ADDR_WIDTH(ADDR_WIDTH),
    .ID_WIDTH(ID_WIDTH),
    .DATA_WIDTH(DATA_WIDTH),

    .OPT_NUMBER_OF_CLIENTS(2),

    .REGION_COUNT               (4),
    .REGION_CLIENT_NUM          ({1'b1    , 1'b0    , 1'b0    , 1'b1   }),
    .REGION_BASE_ADDRS          ({16'h1000, 16'h2000, 16'h3000, 16'h4000}),
    .REGION_END_ADDRS           ({16'h2000, 16'h3000, 16'h4000, 16'h5000}),
    .REGION_CLIENT_BASE_ADDRS   ({16'h1000, 16'h2000, 16'h3000, 16'h4000})
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

    .downstream_axi_arvalid     ({downstream1_axi_arvalid,  downstream0_axi_arvalid}),
    .downstream_axi_arready     ({downstream1_axi_arready,  downstream0_axi_arready}),

    .downstream_axi_rvalid      ({downstream1_axi_rvalid,   downstream0_axi_rvalid}),
    .downstream_axi_rready      ({downstream1_axi_rready,   downstream0_axi_rready}),
    .downstream_axi_rresp       ({downstream1_axi_rresp,    downstream0_axi_rresp}),
    .downstream_axi_rlast       ({downstream1_axi_rlast,    downstream0_axi_rlast}),
    .downstream_axi_rdata       ({downstream1_axi_rdata,    downstream0_axi_rdata}),


    .*


);

reg [31:0] mem [DEPTH-1:0];

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
    upstream_axi_arprot = 2'b111;
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
            resp_expected = (addr < (DEPTH << 2)) ? 2'b01 : 2'b11;
            // EXOKAY or SLVERR
            reservation_valid = 0;
        end else begin
            // OKAY or SLVERR
            resp_expected = (addr < (DEPTH << 2)) ? 2'b00 : 2'b11;
        end
    end else begin
        if(reservation_valid && reservation_addr == addr) begin
            reservation_valid = 0;
        end
        // OKAY or SLVERR
        resp_expected = (addr < (DEPTH << 2)) ? 2'b00 : 2'b11;
    end

    // AW request
    @(negedge clk)
    poke_all(1,1,1, 1,1);
    aw_op(addr, id, lock); // Access word = 9, last word in storage
    @(posedge clk)
    aw_expect(1);
    expect_all(0, 1, 1, 1, 1);

    // W request stalled
    @(negedge clk);
    aw_noop();
    @(posedge clk);
    expect_all(0, 0, 1, 1, 1);

    // W request
    @(negedge clk);
    w_op(wdata, wstrb);
    @(posedge clk)
    w_expect(1);
    if(lock && resp_expected == `AXI_RESP_OKAY) begin
        `assert_equal(memory_axi_wstrb, 0)
    end
    expect_all(1, 0, 1, 1, 1);

    // B stalled
    @(negedge clk);
    upstream_axi_bready = 0;
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
        if(wstrb[3])
            mem[addr >> 2][31:24] = wdata[31:24];
        if(wstrb[2])
            mem[addr >> 2][23:16] = wdata[23:16];
        if(wstrb[1])
            mem[addr >> 2][15:8] = wdata[15:8];
        if(wstrb[0])
            mem[addr >> 2][7:0] = wdata[7:0];
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

    mask = (len << 2);
    // AR request
    @(negedge clk)
    poke_all(1,1,1, 1,1);
    ar_op(addr, id, burst, len, lock); // Access word = 9, last word in storage
    @(posedge clk)
    ar_expect(1);
    expect_all(1, 1, 1, 0, 1);


    
    addr_reg = addr;

    for(i = 0; i < len+1; i = i + 1) begin
        // R response stalled
        @(negedge clk);
        upstream_axi_rready = 0;
        ar_noop();
        @(posedge clk);
        if(lock)
            resp_expected = (addr_reg < (DEPTH << 2)) ? 2'b01 : 2'b11;
        else
            resp_expected = (addr_reg < (DEPTH << 2)) ? 2'b00 : 2'b11;
        r_expect(1,
            resp_expected,
            mem[addr_reg >> 2],
            id,
            i == len);
        expect_all(1, 1, 1, 1, 0);

        // R response accepted
        @(negedge clk);
        upstream_axi_rready = 1;
        @(posedge clk)
        r_expect(1,
            resp_expected,
            mem[addr_reg >> 2],
            id,
            i == len);
        expect_all(1, 1, 1, 1, 0);

        if(burst == 2'b10) // wrap
            addr_reg = (addr_reg & ~mask) | ((addr_reg + 4) & mask);
        else // incr
            addr_reg = addr_reg + 4;
    end
    @(negedge clk);
    poke_all(1,1,1, 1,1);
    $display("Read done addr = 0x%x", addr);
end
endtask



integer i;
integer word;
*/
initial begin
    /*
    @(posedge rst_n)

    @(negedge clk)
    poke_all(1,1,1, 1,1);
    
    $display("Writing begin");

    
    

    $display("Writing done");
    
    // Test cases:
    
    $display("AR start w/ len = 0");
    read(9 << 2, //addr
        2'b01, //burst
        0, //len
        4, //id
        0); //lock
    

    $display("AR start w/ lock, len = 0");
    read(8 << 2, //addr
        2'b01, //burst
        0, //len
        4, //id
        1); //lock

    
    $display("AW start w/ len = 0");
    write(9 << 2, //addr
                4'hF, //id
                32'hFFFF_FFFF, // wdata
                4'b1111, // wstrb
                0); // lock
    $display("AW start w/ lock, len = 0");
    write(8 << 2, //addr
                4'hF, //id
                32'hFFFF_FFFF, // wdata
                4'b1111, // wstrb
                1); // lock

    $display("AW start w/ lock, len = 0, failing, because no lock");
    write(8 << 2, //addr
                4'hF, //id
                32'hFFFF_FFFF, // wdata
                4'b1111, // wstrb
                1); // lock
    
    $display("AR start w/ lock, len = 0");
    read(8 << 2, //addr
        2'b01, //burst
        0, //len
        4, //id
        1); //lock
    $display("AR start w/ lock, len = 0");
    read(9 << 2, //addr
        2'b01, //burst
        0, //len
        4, //id
        1); //lock
    write(9 << 2, //addr
                4'hF, //id
                32'hFFFF_FFFF, // wdata
                4'b1111, // wstrb
                1); // lock

    // TODO: Add more tests
    // Read locking, EXOKAY
    // Write to same address locking, EXOKAY
    
    // Write to same address locking, OKAY, make sure no WSTRB

    // Read, OKAY
    // Write locking, OKAY, make sure no WSTRB

    // Read locking, EXOKAY
    // Read somewhere else
    // Write somewhere else
    // Write locking, EXOKAY

    // Read locking, EXOKAY
    // Read locking, EXOKAY
    // Write not locking, OKAY

    // Read locking, EXOKAY
    // Read locking, EXOKAY
    // Write locking, EXOKAY
    

    // Memory test, no locks at all
    write(9 << 2, 4, 32'hFF00FF00, 4'b0111, 0);
    write(9 << 2, 4, 32'hFF00FF00, 4'b1111, 0);
    write(9 << 2, 4, 32'hFE00FF00, 4'b0111, 0);
    

    read(9 << 2, 2'b01, 0, 4, 0); //INCR test

    
    for(i = 0; i < DEPTH; i = i + 1) begin
        write(i << 2, $urandom(), 32'h0000_0000, 4'b1111, 0);
    end
    $display("Full write done");
    
    for(i = 0; i < 100; i = i + 1) begin
        word = $urandom() % (DEPTH * 2);
        
        write(word << 2, //addr
            $urandom() & 4'hF, //id
            $urandom() & 32'hFFFF_FFFF, // data
            4'b1111, 0);
    end
    $display("Test write done");
    
    $display("Data dump:");
    for(i = 0; i < DEPTH; i = i + 1) begin
        $display("mem[%d] = 0x%x or %d", i, mem[i], mem[i]);
    end


    for(i = 0; i < DEPTH; i = i + 1) begin
        read(i << 2, //addr
            ($urandom() & 1) ? 2'b10 : 2'b01, // burst
            (1 << ($urandom() % 8)) - 1, // len
            $urandom() & 4'hF // id
            , 0);
    end
    $display("Test Read done");

    $display("Random read/write test started");
    for(i = 0; i < 1000; i = i + 1) begin
        word = $urandom() % (DEPTH * 2);

        if($urandom() & 1) begin
            write(word << 2, //addr
                $urandom() & 4'hF, //id
                $urandom() & 32'hFFFF_FFFF, // data
                $urandom() & 4'b1111, 0);
        end else begin
            read(word << 2, //addr
                ($urandom() & 1) ? 2'b10 : 2'b01, // burst
                (1 << ($urandom() % 5)) - 1, // len
                $urandom() & 4'hF // id
                , 0);
        end
    end



    @(negedge clk);
    upstream_axi_bready = 0;
    

    @(posedge clk);
    */
    @(negedge clk)
    @(negedge clk)
    $finish;
end


endmodule