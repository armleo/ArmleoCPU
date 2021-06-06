`timescale 1ns/1ns


module armleocpu_regfile(
	input clk,
	input rst_n,

	input				rs1_read,
	input		[4:0]	rs1_addr,
	output     [31:0]	rs1_rdata,

	input				rs2_read,
	input		[4:0]	rs2_addr,
	output     [31:0]	rs2_rdata,
	
	
	input		[4:0]		rd_addr,
	input	   [31:0]		rd_wdata,
	input					rd_write
);

wire write = !rst_n || (rd_write && (rd_addr != 0));
wire [4:0] writeaddress = rst_n ? rd_addr : 0;
wire [31:0] writedata = rst_n ? rd_wdata : 0;


armleocpu_regfile_one_lane #(.ELEMENTS_W(5), .WIDTH(32)) lane0(
	.clk(clk),

	.readaddress(rs1_addr),
	.read(rs1_read),
	.readdata(rs1_rdata),

	.write(write),
	.writeaddress(writeaddress),
	.writedata(writedata)
);



armleocpu_regfile_one_lane #(.ELEMENTS_W(5), .WIDTH(32)) lane1(
	.clk(clk),

	.readaddress(rs2_addr),
	.read(rs2_read),
	.readdata(rs2_rdata),

	.write(write),
	.writeaddress(writeaddress),
	.writedata(writedata)
);


endmodule
