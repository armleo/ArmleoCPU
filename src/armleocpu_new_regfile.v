`timescale 1ns/1ns

module armleocpu_regfile(
	input clk,
	input rst_n,

	input				rs1_read,
	input		[4:0]	rs1_addr,
	output reg [31:0]	rs1_rdata,

	input				rs2_read,
	input		[4:0]	rs2_addr,
	output reg [31:0]	rs2_rdata,
	
	
	input		[4:0]		rd_addr,
	input	   [31:0]		rd_wdata,
	input					rd_write
);

reg [31:0] storage0 [31:0];
reg [31:0] storage1 [31:0];


always @(posedge clk) begin : regfile_always_clk
	if(!rst_n) begin : rst_block
		storage0[0] <= 0;
		storage1[0] <= 0;
	end else begin
		if(rd_write && (rd_addr != 5'd0)) begin 
			storage0[rd_addr] <= rd_wdata;
			storage1[rd_addr] <= rd_wdata;
		end
		if(rs1_read)
			rs1_rdata <= storage0[rs1_addr];
		if(rs2_read)
			rs2_rdata <= storage1[rs2_addr];
	end
end


endmodule