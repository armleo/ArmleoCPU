`timescale 1ns/1ns



module cache_tb();

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

initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
	#1000
	$display("!ERROR!: Simulation timeout");
	`assert_equal(0, 1)
end

`include "armleocpu_defines.vh"

localparam ADDR_WIDTH = 34;
localparam DATA_STROBES = 4;
localparam DATA_WIDTH = 32;
localparam ID_WIDTH = 1;

wire [3:0] c_response;

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
wire [31:0] axi_wdata;
wire [3:0] axi_wstrb;
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
wire [31:0] axi_rdata;


armleocpu_cache cache(
	.*
);

wire [7:0] axi_awlen = 0;
wire [1:0] axi_awburst = 2'b01; // INCR
wire [2:0] axi_awsize = 3'b010;
wire [0:0] axi_awid = 0;

wire [0:0] axi_bid; // ignored

wire [2:0] axi_arsize = 3'b010;
wire [0:0] axi_arid = 0;

wire [0:0] axi_rid; // ignored



wire memory_axi_awvalid;
wire memory_axi_awready;
wire [ADDR_WIDTH-1:0] memory_axi_awaddr;
wire [7:0] memory_axi_awlen;
wire [2:0] memory_axi_awsize;
wire [1:0] memory_axi_awburst;
wire [ID_WIDTH-1:0] memory_axi_awid;

wire memory_axi_wvalid;
wire memory_axi_wready;
wire [DATA_WIDTH-1:0] memory_axi_wdata;
wire [DATA_STROBES-1:0] memory_axi_wstrb;
wire memory_axi_wlast;

wire memory_axi_bvalid;
wire memory_axi_bready;
wire [1:0] memory_axi_bresp;
wire [ID_WIDTH-1:0] memory_axi_bid;


wire memory_axi_arvalid;
wire memory_axi_arready;
wire [ADDR_WIDTH-1:0] memory_axi_araddr;
wire [7:0] memory_axi_arlen;
wire [2:0] memory_axi_arsize;
wire [1:0] memory_axi_arburst;
wire [ID_WIDTH-1:0] memory_axi_arid;

wire memory_axi_rvalid;
wire memory_axi_rready;
wire [1:0] memory_axi_rresp;
wire [DATA_WIDTH-1:0] memory_axi_rdata;
wire [ID_WIDTH-1:0] memory_axi_rid;
wire memory_axi_rlast;


armleocpu_axi_bram #(
	.ADDR_WIDTH(34),
	.ID_WIDTH(1),
	.DEPTH(1025), // Use such value so we can test the  error in the middle of read burst
) bram(
	.clk(clk),
	.rst_n(rst_n),

	`CONNECT_AXI_BUS(axi_, memory_axi_)
);

// TODO: Add Interconnect to test "cache bypassed" memory section

armleocpu_axi_exclusive_monitor #(
	.ADDR_WIDTH(34),
	.ID_WIDTH(1)
) monitor(
	.clk(clk),
	.rst_n(rst_n),

	`CONNECT_AXI_BUS(memory_axi_, memory_axi_),

	`CONNECT_AXI_BUS(cpu_axi_, axi_),
	.cpu_axi_arlock(axi_arlock),
	.cpu_axi_awlock(axi_awlock)
);

task flush;
begin
	c_cmd = `CACHE_CMD_FLUSH_ALL;
	@(negedge clk)
	`assert_equal(cache.os_active, 1)
	`assert_equal(cache.os_cmd_flush, 1)
	`assert_equal(cache.os_cmd, `CACHE_CMD_FLUSH_ALL)
	`assert_equal(c_response, `CACHE_RESPONSE_DONE)
	c_cmd = `CACHE_CMD_NONE;
end
endtask

task write;
input [31:0] addr;
input [1:0] store_type;
input [31:0] store_data;
begin
	c_cmd = `CACHE_CMD_STORE;
	c_address = addr;
	c_store_type = store_type;
	c_store_data = store_data;
	@(negedge clk);
	while(c_response == `CACHE_RESPONSE_WAIT) begin
		@(negedge clk);
	end
	// Leave checks to caller
end
endtask

task read;
input execute;
input lock;
input [31:0] addr;
input [2:0] load_type;
begin
	c_cmd = lock ? `CACHE_CMD_LOAD_RESERVE : (execute ? `CACHE_CMD_EXECUTE : `CACHE_CMD_LOAD);
	c_address = addr;
	c_load_type = load_type;
	@(negedge clk);
	while(c_response == `CACHE_RESPONSE_WAIT) begin
		@(negedge clk);
	end
	// Leave checks to caller
end
endtask

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
	write(0, `STORE_WORD, 32'hFF00FF00);
	`assert_equal(c_response, `CACHE_RESPONSE_DONE)
	
	@(negedge clk) // After write skip one cycle
	$display("Testbench: Read Reserve test");
	read(0, // execute?
		1, // atomic?
		0, // addr?
		`LOAD_WORD // type?
		);
	`assert_equal(c_response, `CACHE_RESPONSE_DONE)

	@(negedge clk)
	@(negedge clk)
	
	// TODO: Write tests
	$finish;
end


endmodule