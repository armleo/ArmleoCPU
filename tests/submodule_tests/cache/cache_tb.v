`timescale 1ns/1ns

module cache_tb;

reg clk = 0;
reg rst_n = 1;
reg clk_enable = 0;
initial begin
	clk_enable = 1;
	rst_n = 0;
	#2 rst_n = 1;
end
always begin
	#1 clk <= clk_enable ? !clk : clk;
end

`include "assert.vh"

initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
end

localparam MEM_SIZE = 16*1024*1024; // In words


reg [MEM_SIZE-1:0] pma_error;
reg [MEM_SIZE-1:0] mem [31:0];

reg axi_awvalid;
reg axi_awlock;
reg [33:0] axi_awaddr;

reg axi_wvalid;
reg axi_wdata;
reg [3:0] axi_wstrb;
reg [31:0] axi_wdata;
wire axi_wlast = 1;

reg axi_




reg axi_awready;
reg axi_wready;
reg axi_bvalid;
reg [1:0] axi_bresp;

reg write_cycle;
reg [3:0] burst_remainig;


always @(posedge clk) begin
	integer k;
	if(!rst_n) begin
		write_cycle <= 0;
		axi_awready <= 0;
		axi_wready <= 0;
		axi_bvalid <= 0;
	end else if(axi_awvalid) begin
		axi_awready <= 1;
		lock <= axi_awlock;
		addr <= axi_awaddr;
	end else if(axi_awvalid && axi_awready) begin
		axi_awready <= 0;
		write_cycle <= 1;
	end else if(axi_wvalid && write_cycle) begin
		`assert(axi_wlast, 1)
		if(!pma_error[addr >> 2]) begin
			for(k = 0; k < 4; k = k + 1) begin
				if(axi_wstrb[k]) begin
					mem[addr >> 2][(k+1)*8-1:k*8] <= axi_wdata;
				end
			end
		end
		axi_wready <= 1;
		write_cycle <= 0;
	end else if(axi_wvalid && axi_wready) begin
		axi_wready <= 0;
		axi_bvalid <= 1;
		if(!pma_error[addr >> 2] && atomic_locked && atomic_lock_address == addr) begin
			axi_bresp <= `AXI_RESP_EXOKAY;
			atomic_locked <= 0;
		end else if(pma_error[addr >> 2]) begin
			axi_bresp <= `AXI_RESP_SLVERR;
		end else begin
			axi_bresp <= `AXI_RESP_OKAY;
		end
	end else if(axi_bvalid) begin
		if(axi_bready) begin
			axi_bvalid <= 0;
		end
	end else if(axi_arvalid) begin
		lock <= axi_arlock;
		addr <= axi_araddr;
		len <= axi_arlen;
		if(axi_arburst == `AXI_BURST_WRAP) begin
			`assert(axi_arlen, 7)
		end else if(axi_arburst == `AXI_BURST_INCR) begin
			`assert(axi_arlen, 0)
		end
		burst_remainig <= axi_arlen;
		if(axi_arlock) begin
			atomic_locked <= axi_arlock;
			atomic_lock_address <= axi_araddr >> 2;
		end
	end

end

// TODO: Add the axi4 ram

initial begin
	// TODO: Write tests
	$finish;
end


endmodule