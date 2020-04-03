
module async_mem(
    input clk,
    
    input [1:0] counter,
    
    input [7:0] raddr,
    output [31:0] rdata,
    
    input [7:0] waddr,
    input write,
    input [31:0] wdata
);

parameter ELEMENTS_W = 7;
parameter WIDTH = 32;

mem_1w1r #(ELEMENTS_W, WIDTH) backmem(
    .clk(clk),
    .read(counter == 2'b00),
    
    .readaddress(raddr),
    .readdata(rdata),
    
    .write(counter == 2'b10 && write),
    .writeaddress(waddr),
    .writedata(wdata)
    
);

endmodule

module internal_clk_gen(
    input clk, // comes from ext
    output reg [1:0] counter, // goes to async mem
    output internal_clk // goes to logic
);

assign internal_clk = counter[1];
initial begin
    counter = 0;
end

always @(posedge clk) begin
    counter <= counter + 1;
end

endmodule
