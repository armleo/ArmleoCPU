`define TIMEOUT 2000000
`define SYNC_RST
`define CLK_HALF_PERIOD 10

`include "template.vh"

localparam ADDR_WIDTH = 32;
localparam DATA_WIDTH = 32;
localparam DATA_STROBES = DATA_WIDTH/8;
localparam DEPTH = 10;


reg cpu_axi_awvalid;
wire cpu_axi_awready;
reg [ADDR_WIDTH-1:0] cpu_axi_awaddr;
reg [7:0] cpu_axi_awlen;
reg [2:0] cpu_axi_awsize;
reg [1:0] cpu_axi_awburst;
reg [3:0] cpu_axi_awid;
reg cpu_axi_awlock;

reg cpu_axi_wvalid;
wire cpu_axi_wready;
reg [DATA_WIDTH-1:0] cpu_axi_wdata;
reg [DATA_STROBES-1:0] cpu_axi_wstrb;
reg cpu_axi_wlast;

wire cpu_axi_bvalid;
reg cpu_axi_bready;
wire [1:0] cpu_axi_bresp;
wire [3:0] cpu_axi_bid;


reg cpu_axi_arvalid;
wire cpu_axi_arready;
reg [ADDR_WIDTH-1:0] cpu_axi_araddr;
reg [7:0] cpu_axi_arlen;
reg [2:0] cpu_axi_arsize;
reg [1:0] cpu_axi_arburst;
reg [3:0] cpu_axi_arid;
reg cpu_axi_arlock;

wire cpu_axi_rvalid;
reg cpu_axi_rready;
wire [1:0] cpu_axi_rresp;
wire [DATA_WIDTH-1:0] cpu_axi_rdata;
wire [3:0] cpu_axi_rid;
wire cpu_axi_rlast;








wire memory_axi_awvalid;
wire memory_axi_awready;
wire [ADDR_WIDTH-1:0] memory_axi_awaddr;
wire [7:0] memory_axi_awlen;
wire [2:0] memory_axi_awsize;
wire [1:0] memory_axi_awburst;
wire [3:0] memory_axi_awid;

wire memory_axi_wvalid;
wire memory_axi_wready;
wire [DATA_WIDTH-1:0] memory_axi_wdata;
wire [DATA_STROBES-1:0] memory_axi_wstrb;
wire memory_axi_wlast;

wire memory_axi_bvalid;
wire memory_axi_bready;
wire [1:0] memory_axi_bresp;
wire [3:0] memory_axi_bid;


wire memory_axi_arvalid;
wire memory_axi_arready;
wire [ADDR_WIDTH-1:0] memory_axi_araddr;
wire [7:0] memory_axi_arlen;
wire [2:0] memory_axi_arsize;
wire [1:0] memory_axi_arburst;
wire [3:0] memory_axi_arid;

wire memory_axi_rvalid;
wire memory_axi_rready;
wire [1:0] memory_axi_rresp;
wire [DATA_WIDTH-1:0] memory_axi_rdata;
wire [3:0] memory_axi_rid;
wire memory_axi_rlast;


armleocpu_axi_bram #(DEPTH) bram (
	.clk(clk),
	.rst_n(rst_n),

	`CONNECT_AXI_BUS(axi_, memory_axi_)
	
);

armleocpu_axi_exclusive_monitor exclusive_monitor (
	.clk(clk),
	.rst_n(rst_n),

	`CONNECT_AXI_BUS(memory_axi_, memory_axi_),

	`CONNECT_AXI_BUS(cpu_axi_, cpu_axi_),
	.cpu_axi_arlock(cpu_axi_arlock),
	.cpu_axi_awlock(cpu_axi_awlock)
);




reg [31:0] mem [DEPTH-1:0];

//-------------AW---------------
task aw_noop; begin
	cpu_axi_awvalid = 0;
end endtask

task aw_op;
input [ADDR_WIDTH-1:0] addr;
input [2:0] id;
input lock;
begin
	cpu_axi_awvalid = 1;
	cpu_axi_awaddr = addr;
	cpu_axi_awlen = 0;
	cpu_axi_awsize = 2; // 4 bytes
	cpu_axi_awburst = 2'b01; // Increment
	cpu_axi_awid = id;
	cpu_axi_awlock = lock;
end endtask

task aw_expect;
input awready;
begin
	`assert_equal(cpu_axi_awready, awready);
end endtask

//-------------W---------------
task w_noop; begin
	cpu_axi_wvalid = 0;
end endtask

task w_op;
input [DATA_WIDTH-1:0] wdata;
input [DATA_STROBES-1:0] wstrb;
begin
	cpu_axi_wvalid = 1;
	cpu_axi_wdata = wdata;
	cpu_axi_wstrb = wstrb;
	cpu_axi_wlast = 1;
end endtask

task w_expect;
input wready;
begin
	`assert_equal(cpu_axi_wready, wready)
end endtask

//-------------B---------------
task b_noop; begin
	cpu_axi_bready = 0;
end endtask

task b_expect;
input valid;
input [1:0] resp;
input [3:0] id;
begin
	`assert_equal(cpu_axi_bvalid, valid)
	if(valid) begin
		`assert_equal(cpu_axi_bresp, resp)
	end
end endtask

//-------------AR---------------
task ar_noop; begin
	cpu_axi_arvalid = 0;
end endtask

task ar_op; 
input [ADDR_WIDTH-1:0] addr;
input [3:0] id;
input [1:0] burst;
input [7:0] len;
input lock;
begin
	cpu_axi_arvalid = 1;
	cpu_axi_araddr = addr;
	cpu_axi_arlen = len;
	cpu_axi_arsize = 2; // 4 bytes
	cpu_axi_arburst = burst; // Increment
	cpu_axi_arid = id;
	cpu_axi_arlock = lock;
end endtask

task ar_expect;
input ready;
begin
	`assert_equal(cpu_axi_arready, ready)
end endtask

//-------------R---------------
task r_noop; begin
	cpu_axi_rready = 0;
end endtask

task r_expect;
input valid;
input [1:0] resp;
input [31:0] data;
input [3:0] id;
input last;
begin
	`assert_equal(cpu_axi_rvalid, valid)
	if(valid) begin
		`assert_equal(cpu_axi_rresp, resp)
		if(resp <= 2'b01)
			`assert_equal(cpu_axi_rdata, data)
		`assert_equal(cpu_axi_rid, id)
		`assert_equal(cpu_axi_rlast, last)
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
	cpu_axi_bready = 0;
	@(posedge clk);
	b_expect(1, resp_expected, id);
	expect_all(1, 1, 0, 1, 1);

	// B done
	@(negedge clk);
	cpu_axi_bready = 1;
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
		cpu_axi_rready = 0;
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
		cpu_axi_rready = 1;
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

initial begin
	
	@(posedge rst_n)

	@(negedge clk)
	poke_all(1,1,1, 1,1);
	/*
	$display("Writing begin");

	
	

	$display("Writing done");
	*/
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
	cpu_axi_bready = 0;
	

	@(posedge clk);

	@(negedge clk)
	@(negedge clk)
	$finish;
end


endmodule