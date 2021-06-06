`timescale 1ns/1ns

module armleocpu_storegen(
    input [1:0] inword_offset,
    input [1:0] storegen_type,

    input [31:0] storegen_datain,

    output wire  [31:0] storegen_dataout,
    output wire  [3:0]  storegen_datamask,
    output wire         storegen_missaligned,
    output wire         storegen_unknowntype
);

`include "armleocpu_defines.vh"


assign storegen_datamask = 
    storegen_type == `STORE_WORD ? 4'b1111 : (
    storegen_type == `STORE_HALF ? (4'b11 << inword_offset) : (
    storegen_type == `STORE_BYTE ? (4'b1 << inword_offset) : 4'b0000
));

wire [4:0] woffset = {inword_offset, 3'b000};

assign storegen_dataout = storegen_datain << woffset;

assign storegen_missaligned = (
    ((storegen_type == `STORE_WORD) && (|inword_offset)) || 
    ((storegen_type == `STORE_HALF) && (inword_offset[0]))
);

assign storegen_unknowntype = storegen_type == 2'b11;

endmodule