`timescale 1ns/1ns

module armleocpu_regfile(
	input clk,
	input rst_n,
	
	
	input	[4:0]	rs1_addr,
	output	[31:0]	rs1_rdata,
	
	input	[4:0]	rs2_addr,
	output	[31:0]	rs2_rdata,
	
	
	input	[4:0]	rd_addr,
	input	[31:0]	rd_wdata,
	input			rd_write
);

reg [31:0] regs [31:0];



always @(posedge clk) begin : regfile_clk
	if(!rst_n) begin
		integer i = 0;
		for(i = 0; i < 32; i = i + 1)
			regs[i] <= 0;
	end else if(clk) begin
		if(rd_write && (rd_addr != 5'd0))
			regs[rd_addr] <= rd_wdata;
	end
end

assign rs1_rdata = regs[rs1_addr];
assign rs2_rdata = regs[rs2_addr];

endmodule