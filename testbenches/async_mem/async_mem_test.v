`timescale 1ns/1ns


module async_mem_test;

reg clk = 0;
always begin
    #10 clk = !clk;
end

reg [7:0] waddr;
reg [7:0] raddr;
reg [31:0] wdata;
reg write;
wire [31:0] rdata;


wire internal_clk;

async_mem_test_top U(
    .clk(clk),
    .internal_clk(internal_clk),
    .write(write),
    .waddr(waddr),
    .wdata(wdata),
    .raddr(raddr),
    .rdata(rdata)
);
initial begin
    @(negedge internal_clk)
    waddr <= 5;
    write <= 1;
    wdata <= 100;
    @(negedge internal_clk)
    waddr <= 6;
    write <= 1;
    wdata <= 101;
    @(negedge internal_clk)
    write <= 0;
    @(posedge internal_clk)
    @(negedge internal_clk)
    raddr <= 5;
    @(posedge internal_clk)
    $display("rdata valid?", rdata);
    @(negedge internal_clk)
    raddr <= 6;
    @(posedge internal_clk)
    $display("rdata valid?", rdata);
    @(posedge internal_clk)
    $finish;
end


endmodule