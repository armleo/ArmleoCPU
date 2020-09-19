module armleocpu_muldiv(
    input               muldiv_operation_select,
    input               request_valid,
    input [31:0]        op1,
    input [31:0]        op2,


    output reg          result_ready,
    output reg          result_division_by_zero,
    output reg [31:0]   result
);


// TODO: Do MUL/DIV/REM signed/unsigned


endmodule