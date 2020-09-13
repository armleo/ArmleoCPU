`timescale 1ns/1ns

module armleocpu_storegen(
    input [1:0] inwordOffset,
    input [2:0] storegenType,

    input [31:0] storegenDataIn,

    output wire  [31:0] storegenDataOut,
    output wire  [3:0]  storegenDataMask,
    output wire         storegenMissAligned,
    output wire         storegenUnknownType
);

`include "armleocpu_includes.vh"

assign storegenDataMask = 
    storegenType == `STORE_WORD ? 4'b1111 : (
    storegenType == `STORE_HALF ? (4'b11 << inwordOffset) : (
    storegenType == `STORE_BYTE ? (4'b1 << inwordOffset) : 4'b0000
));

wire [4:0] woffset = {inwordOffset, 3'b000};

assign storegenDataOut = storegenDataIn << woffset;

assign storegenMissAligned = (
    ((storegenType == `STORE_WORD) && (|inwordOffset)) || 
    ((storegenType == `STORE_HALF) && (inwordOffset[0]))
);

assign storegenUnknownType = storegenType == 2'b11;

endmodule