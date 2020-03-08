module armleocpu_storegen(
    input [1:0] inwordOffset,
    input [1:0] st_type,

    input [31:0] storeDataIn,

    output logic [31:0] storeDataOut,
    output logic [3:0]  storeDataMask,
    output logic        storeMissAligned
);

`include "armleocpu_defs.sv"

assign storeDataMask = 
    st_type == ST_SW ? 4'b1111 : (
    st_type == ST_SH ? (4'b11 << inwordOffset) : (
    st_type == ST_SB ? (4'b1 << inwordOffset) : 4'b0000
));

wire [4:0] woffset = inwordOffset << 3

assign storeDataOut = storeDataIn << woffset;

assign storeMissAligned = (
    ((st_type == ST_SW) && (|inwordOffset)) || 
    ((st_type == ST_SH) && (inwordOffset[0]))
)

endmodule