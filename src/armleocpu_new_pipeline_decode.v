

input                               e2d_ready,

output reg                          d2e_instr_valid,
output reg [31:0]                   d2e_instr,
output reg [DECODE_IS_WIDTH-1:0]    d2e_instr_decode,
output reg                          d2e_instr_illegal,




`define DECODE_IS_OP_IMM 0
`define DECODE_IS_OP 1
`define DECODE_IS_JALR 2
`define DECODE_IS_JAL 3
`define DECODE_IS_LUI 4
`define DECODE_IS_AUIPC 5
`define DECODE_IS_BRANCH 6
`define DECODE_IS_STORE 7
`define DECODE_IS_LOAD 8


`define DECODE_IS_MUL 9
`define DECODE_IS_MULH 10
`define DECODE_IS_MULHSU 11
`define DECODE_IS_MULHU 12

`define DECODE_IS_DIV 13
`define DECODE_IS_DIVU 14

`define DECODE_IS_REM 15
`define DECODE_IS_REMU 16

`define DECODE_IS_EBREAK 17

`define DECODE_IS_ECALL 18
`define DECODE_IS_WFI 19
`define DECODE_IS_MRET 20
`define DECODE_IS_SRET 21

`define DECODE_IS_SFENCE_VMA 22
`define DECODE_IS_IFENCEI 23

`define DECODE_IS_FENCE_NORMAL 24

`define DECODE_IS_CSRRW_CSRRWI 25
`define DECODE_IS_CSRS_CSRSI 26
`define DECODE_IS_CSRC_CSRCI 27

`define DECODE_IS_CSR 28

// contains decode's output length
`define DECODE_IS_WIDTH 29

