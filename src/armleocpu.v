module armleocpu(
    input clk,
    input rst_n,

    output                  d_transaction,
    output       [2:0]      d_cmd,
    input                   d_transaction_done,
    input        [2:0]      d_transaction_response,
    output       [33:0]     d_address,
    output       [3:0]      d_burstcount,
    output       [31:0]     d_wdata,
    output       [3:0]      d_wbyte_enable,
    input        [31:0]     d_rdata,

    output                  i_transaction,
    output       [2:0]      i_cmd,
    input                   i_transaction_done,
    input        [2:0]      i_transaction_response,
    output       [33:0]     i_address,
    output       [3:0]      i_burstcount,
    output       [31:0]     i_wdata,
    output       [3:0]      i_wbyte_enable,
    input        [31:0]     i_rdata,

    input                   dbg_request,
    input        [3:0]      dbg_cmd,
    input        [31:0]     dbg_arg1,
    input        [31:0]     dbg_arg2,
    output       [31:0]     dbg_result,
    output                  dbg_mode,
    output                  dbg_done
);

// Debug

// D-Cache

// I-Cache

// Execute

// Fetch

// CSR

// Regfile

endmodule