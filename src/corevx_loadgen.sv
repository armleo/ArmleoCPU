module corevx_loadgen(
    input [1:0] inwordOffset,
    input [2:0] loadType,

    input [31:0] LoadGenDataIn,

    output logic [31:0] LoadGenDataOut,
    output logic LoadMissaligned,
    output logic LoadUnknownType
);

`include "ld_type.svh"

wire [4:0] roffset = (inwordOffset << 3);
wire [31:0] rshift  = LoadGenDataIn >> roffset;


always @* begin
    LoadUnknownType = 0;
    LoadMissaligned = 0;
    LoadGenDataOut = rshift;
    case(loadType)
        // Word
        LOAD_WORD: begin
            LoadGenDataOut = rshift;
            LoadMissaligned = (|inwordOffset);
        end
        // Half word
        LOAD_HALF_UNSIGNED: begin
            LoadGenDataOut = rshift[15:0];
            LoadMissaligned = inwordOffset[0];
        end
        LOAD_HALF: begin
            LoadGenDataOut = $signed(rshift[15:0]);
            LoadMissaligned = inwordOffset[0];
        end
        // Byte
        LOAD_BYTE_UNSIGNED: begin
            LoadGenDataOut = rshift[7:0];
            LoadMissaligned = 0;
        end
        LOAD_BYTE: begin
            LoadGenDataOut = $signed(rshift[7:0]);
            LoadMissaligned = 0;
        end
        // Else
        default:
            LoadUnknownType = 1;
    endcase
end
endmodule