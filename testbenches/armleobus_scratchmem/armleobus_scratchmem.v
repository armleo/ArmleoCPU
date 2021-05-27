// TODO: Fix
`timescale 1ns/1ns

/* verilator lint_off MULTITOP */
module armleobus_scratchmem(
    input                           clk,

    input                           transaction,
    input        [2:0]              cmd,
    output wire                     transaction_done,
    output wire  [2:0]              transaction_response,
    input        [DEPTH_LOG2+2-1:0] address,
    input        [31:0]             wdata,
    input        [3:0]              wbyte_enable,
    output reg   [31:0]             rdata
);

parameter DEPTH_LOG2 = 16;
localparam DEPTH = (2**DEPTH_LOG2);
parameter delay = 2;

reg [31:0] mem [DEPTH-1:0];

`include "armleocpu_defines.vh"


reg [31:0] counter = 0;

assign transaction_done = counter >= delay;
wire transaction_next_done = counter + 1 >= delay;

assign transaction_response = address[1:0] != 0 ? `ARMLEOBUS_INVALID_OPERATION : `ARMLEOBUS_RESPONSE_SUCCESS;

always @(posedge clk) begin
    if(!transaction || cmd == `ARMLEOBUS_CMD_NONE || transaction_done)
        counter <= 0;
    else
        counter <= counter + 1;
    if(transaction_next_done && !transaction_done) begin
        $display("ACCESS", address[DEPTH_LOG2-1+2:2]);
        if(cmd == `ARMLEOBUS_CMD_READ) begin
            $display("READ", mem[address[DEPTH_LOG2-1+2:2]]);
            rdata <= mem[address[DEPTH_LOG2-1+2:2]];
        end else if(cmd == `ARMLEOBUS_CMD_WRITE && address[1:0] == 0) begin
            $display("WRITE", wdata);
            if(wbyte_enable[3])
                mem[address[DEPTH_LOG2-1+2:2]][31:24] <= wdata[31:24];
            if(wbyte_enable[2])
                mem[address[DEPTH_LOG2-1+2:2]][23:16] <= wdata[23:16];
            if(wbyte_enable[1])
                mem[address[DEPTH_LOG2-1+2:2]][15:8 ] <= wdata[15:8 ];
            if(wbyte_enable[0])
                mem[address[DEPTH_LOG2-1+2:2]][7 :0 ] <= wdata[7 :0 ];
        end
    end
end

endmodule
/* verilator lint_on MULTITOP */