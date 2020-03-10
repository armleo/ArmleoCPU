module armleocpu_loadgen(
    input [1:0] inwordOffset,
    input [2:0] ld_type,

    input [31:0] LoadGenDataIn,

    output logic [31:0] LoadGenDataOut,
    output logic LoadMissaligned,
    output logic unknown_type
);

`include "armleocpu_defs.sv"

wire [4:0] roffset = (inwordOffset << 3);
wire [31:0] rshift  = LoadGenDataIn >> roffset;


always @* begin
    unknown_type = 0;
    LoadMissaligned = 0;
    LoadGenDataOut = rshift;
    case(ld_type)
        // Word
        LD_LW: begin
            LoadGenDataOut = rshift;
            LoadMissaligned = (|inwordOffset);
        end
        // Half word
        LD_LHU: begin
            LoadGenDataOut = rshift[15:0];
            LoadMissaligned = inwordOffset[0];
        end
        LD_LH: begin
            LoadGenDataOut = $signed(rshift[15:0]);
            LoadMissaligned = inwordOffset[0];
        end
        // Byte
        LD_LBU: begin
            LoadGenDataOut = rshift[7:0];
            LoadMissaligned = 0;
        end
        LD_LB: begin
            LoadGenDataOut = $signed(rshift[7:0]);
            LoadMissaligned = 0;
        end
        // Else
        default:
            unknown_type = 1;
    endcase
end
endmodule