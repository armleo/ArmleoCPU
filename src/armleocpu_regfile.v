`timescale 1ns/1ns


module regfile_one_lane (clk, readaddress, read, readdata, writeaddress, write, writedata);
	parameter ELEMENTS_W = 7;
	localparam ELEMENTS = 2**ELEMENTS_W;
	parameter WIDTH = 32;

	input clk;

    input [ELEMENTS_W-1:0] readaddress;
    input read;
	output reg [WIDTH-1:0] readdata;


	input [ELEMENTS_W-1:0] writeaddress;
	input write;
	input [WIDTH-1:0] writedata;

reg [WIDTH-1:0] storage[ELEMENTS-1:0];

always @(posedge clk) begin
	if(write) begin
		storage[writeaddress] <= writedata;
	end
	if(read)
		readdata <= storage[readaddress];
end

endmodule


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

regfile_one_lane #(.ELEMENTS_W(5), .WIDTH(32)) lane0(
	.clk(clk),

	.readaddress(rs1_addr),
	.read(rs1_read),
	.readdata(rs1_rdata),

	.write((rd_addr != 0 && rd_write) || !rst_n),
	.writeaddress(rst_n ? rd_addr : 0),
	.writedata(rst_n ? rd_wdata : 0)
);



regfile_one_lane #(.ELEMENTS_W(5), .WIDTH(32)) lane1(
	.clk(clk),

	.readaddress(rs2_addr),
	.read(rs2_read),
	.readdata(rs2_rdata),

	.write((rd_addr != 0 && rd_write) || !rst_n),
	.writeaddress(rst_n ? rd_addr : 0),
	.writedata(rst_n ? rd_wdata : 0)
);


endmodule


