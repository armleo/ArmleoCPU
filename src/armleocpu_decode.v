
module armleocpu_decode (
    input clk,
    input rst_n,

    // Fetch to decode
    input      [31:0]       f2d_instr,
    input                   f2d_instr_valid,
    input      [31:0]       f2d_pc,
    input      [1:0]        f2d_fetch_error,

    // Decode to fetch => output
    output reg              d2f_ready,
    output reg [`ARMLEOCPU_D2F_CMD_WIDTH-1:0]
                            d2f_cmd,
    output reg [31:0]       d2f_branchtarget,



    // Decode to execute => output
    output                  d2e_valid,
    output reg [31:0]       d2e_instr,
    output reg [31:0]       d2e_pc,
    output reg  [1:0]       d2e_fetch_error,

    output reg              rs1_read,
    output reg              rs2_read,


    // Execute to decode => input
    input                   e2d_ready,
    input [`ARMLEOCPU_E2D_CMD_WIDTH-1:0]
                            e2d_cmd,
    input      [31:0]       e2d_branchtarget,
    input       [5:0]       rs1_addr,
    input       [5:0]       rs2_addr


);



endmodule