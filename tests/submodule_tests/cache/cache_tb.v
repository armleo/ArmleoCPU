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
	`assert_equal(0, 1)
end

`include "armleocpu_defines.vh"

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
wire [33:0] axi_awaddr;
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
wire [33:0]  axi_araddr;
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
wire [2:0] axi_awsize = 3'd010;
wire [0:0] axi_awid = 0;

wire [0:0] axi_bid; // ignored

wire [2:0] axi_arsize = 3'd010;
wire [0:0] axi_arid = 0;

wire [0:0] axi_rid; // ignored

armleocpu_axi_bram #(
	.ADDR_WIDTH(34),
	.ID_WIDTH(1)
) bram(
	.*
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
	`assert_equal(c_response, `CACHE_RESPONSE_DONE)
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

	flush();

	write(0, `STORE_WORD, 32'hFF00FF00);

	@(negedge clk)
	@(negedge clk)
	
	// TODO: Write tests
	$finish;
end


endmodule