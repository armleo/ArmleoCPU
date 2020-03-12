module armleocpu_storegen(
    input [1:0] inwordOffset,
    input [1:0] storeType,

    input [31:0] storeDataIn,

    output logic [31:0] storeDataOut,
    output logic [3:0]  storeDataMask,
    output logic        storeMissAligned
);

`include "armleocpu_defs.sv"

assign storeDataMask = 
    storeType == STORE_WORD ? 4'b1111 : (
    storeType == STORE_HALF ? (4'b11 << inwordOffset) : (
    storeType == STORE_BYTE ? (4'b1 << inwordOffset) : 4'b0000
));

wire [4:0] woffset = inwordOffset << 3;

assign storeDataOut = storeDataIn << woffset;

assign storeMissAligned = (
    ((storeType == STORE_WORD) && (|inwordOffset)) || 
    ((storeType == STORE_HALF) && (inwordOffset[0]))
);

endmodule