parameter RESET_VECTOR = 32'h0000_0000;

localparam ACCESSTAG_W = 8;

localparam OPCODE_LUI    = 7'b0110111;
localparam OPCODE_AUIPC  = 7'b0010111;
localparam OPCODE_JAL    = 7'b1101111;
localparam OPCODE_JALR   = 7'b1100111;
localparam OPCODE_BRANCH = 7'b1100011;
localparam OPCODE_LOAD   = 7'b0000011;
localparam OPCODE_STORE  = 7'b0100011;
localparam OPCODE_ALUI   = 7'b0010011;
localparam OPCODE_ALU    = 7'b0110011;
localparam OPCODE_MISCMEM= 7'b0001111;
localparam OPCODE_SYSTEM = 7'b1110011;

localparam ST_SB = 2'b00;
localparam ST_SH = 2'b01;
localparam ST_SW = 2'b10;



localparam LD_LB = 3'b000;
localparam LD_LBU = 3'b100;

localparam LD_LH = 3'b001;
localparam LD_LHU = 3'b101;

localparam LD_LW = 3'b010;



localparam MEM_WIDTH_BYTE = 3'b000;
localparam MEM_WIDTH_UNSIGNED_BYTE = 3'b100;

localparam MEM_WIDTH_HALF = 3'b001;
localparam MEM_WIDTH_UNSIGNED_HALF = 3'b101;

localparam MEM_WIDTH_WORD = 3'b010;

localparam ACCESSTAG_VALID_BITS = 0;
localparam ACCESSTAG_READ_BITS = 1;
localparam ACCESSTAG_WRITE_BITS = 2;
localparam ACCESSTAG_EXECUTE_BITS = 3;
localparam ACCESSTAG_USER_BITS = 4;
localparam ACCESSTAG_ACCESS_BITS = 6;
localparam ACCESSTAG_DIRTY_BITS = 7;





