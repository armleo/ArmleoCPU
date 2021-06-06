`timescale 1ns/1ns
`include "armleocpu_defines.vh"

module armleocpu_loadgen(
    input [1:0] inword_offset,
    input [2:0] loadgen_type,

    input [31:0] loadgen_datain,

    output reg [31:0] loadgen_dataout,
    output reg loadgen_missaligned,
    output reg loadgen_unknowntype
);

wire [4:0] roffset = {inword_offset, 3'b000};
wire [31:0] rshift  = loadgen_datain >> roffset;



always @* begin
    case(loadgen_type)
        // Word
        `LOAD_WORD:          loadgen_dataout = rshift;
        `LOAD_HALF_UNSIGNED: loadgen_dataout = {16'h0, rshift[15:0]};
        `LOAD_HALF:          loadgen_dataout = {{16{rshift[15]}}, $signed(rshift[15:0])};
        `LOAD_BYTE_UNSIGNED: loadgen_dataout = {{24{1'b0}}, rshift[7:0]};
        `LOAD_BYTE:          loadgen_dataout = {{24{rshift[7]}}, rshift[7:0]};
        default:             loadgen_dataout = rshift;
    endcase
end

always @* begin
    loadgen_unknowntype = 0;
    loadgen_missaligned = 0;
    case(loadgen_type)
        `LOAD_WORD:                      loadgen_missaligned = (|inword_offset);
        `LOAD_HALF_UNSIGNED, `LOAD_HALF: loadgen_missaligned = inword_offset[0];
        `LOAD_BYTE_UNSIGNED, `LOAD_BYTE: loadgen_missaligned = 0;
        default:
            loadgen_unknowntype = 1;
    endcase
end
endmodule