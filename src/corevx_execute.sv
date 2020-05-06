module corevx_execute(
    input clk,
    input rst_n,

    input [31:0] f2e_insturction,
    input [31:0] f2e_pc,
    input f2e_exc_start,
    
    output e2f_ready,
    output e2f_kill


    output [1:0] e2f_command, /*FLUSH, KILL, BRANCH*/
    output [31:0] e2f_branchtarget,
    output e2f_valid,
    input f2e_ready,
    
    
);

/*
IMMGEN
CSR
BRCOND
ALU
REGFILE -> outside
CACHE -> outside
*/

endmodule