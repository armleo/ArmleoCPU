`timescale 1ns/1ns

module corevx_loadgen(
    input [1:0] inwordOffset,
    input [2:0] loadType,

    input [31:0] LoadGenDataIn,

    output reg [31:0] LoadGenDataOut,
    output reg LoadMissaligned,
    output reg LoadUnknownType
);

`include "ld_type.inc"

wire [4:0] roffset = {inwordOffset, 3'b000};
wire [31:0] rshift  = LoadGenDataIn >> roffset;



always @* begin
    case(loadType)
        // Word
        `LOAD_WORD:          LoadGenDataOut = rshift;
        `LOAD_HALF_UNSIGNED: LoadGenDataOut = {16'h0, rshift[15:0]};
        `LOAD_HALF:          LoadGenDataOut = {{16{rshift[15]}}, $signed(rshift[15:0])};
        `LOAD_BYTE_UNSIGNED: LoadGenDataOut = {{24{1'b0}}, rshift[7:0]};
        `LOAD_BYTE:          LoadGenDataOut = {{24{rshift[7]}}, rshift[7:0]};
        default:            LoadGenDataOut = rshift;
    endcase
end

always @* begin
    LoadUnknownType = 0;
    LoadMissaligned = 0;
    case(loadType)
        `LOAD_WORD:                      LoadMissaligned = (|inwordOffset);
        `LOAD_HALF_UNSIGNED, `LOAD_HALF:  LoadMissaligned = inwordOffset[0];
        `LOAD_BYTE_UNSIGNED, `LOAD_BYTE:  LoadMissaligned = 0;
        default:
            LoadUnknownType = 1;
    endcase
end
endmodule