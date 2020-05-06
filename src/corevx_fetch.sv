module corevx_fetch(
    input                   clk,
    input                   rst_n,

    // Base address
    input [31:0]            mtvec,

    // Cache IF
    input      [3:0]        c_response,
    input                   c_reset_done,

    output reg [3:0]        c_cmd,
    output reg [31:0]       c_address,
    input      [31:0]       c_load_data,

    input                   irq_timer,
    input                   irq_exti,
    

    output reg [31:0]       f2e_instr,
    output reg [31:0]       f2e_pc,
    output reg              f2e_exc_start,
    output reg [3:0]        f2e_cause, // cause [3:0]
    output reg              f2e_interrupt, // cause[31]

    input                   e2f_ready,
    input                   e2f_exc_start,
    input                   e2f_flush,
    input                   e2f_branchtaken,
    input      [31:0]       e2f_branchtarget
);

parameter RESET_VECTOR = 32'h0000_0000;

`include "corevx_cache.svh"
`include "ld_type.svh"
`include "corevx_exception.svh"

`define INSTRUCTION_NOP ({12'h0, 5'h0, 3'b000, 5'h0, 7'b00_100_11});
// state
reg reseted;
reg flushing;
reg [31:0] pc;
reg [31:0] saved_instr;

wire cache_done = c_response == `CACHE_RESPONSE_DONE;
reg exception;
reg [31:0] next_pc;

always @* begin
    f2e_interrupt = 1'b0;
    exception = 1'b0;
    if(c_response == `CACHE_RESPONSE_MISSALIGNED) begin
        f2e_cause = EXCEPTION_CODE_INSTRUCTION_ADDRESS_MISALIGNED;
        exception = 1'b1;
    end else if(c_response == `CACHE_RESPONSE_ACCESSFAULT) begin
        f2e_cause = EXCEPTION_CODE_INSTRUCTION_ACCESS_FAULT;
        exception = 1'b1;
    end else if(c_response == `CACHE_RESPONSE_PAGEFAULT) begin
        f2e_cause = EXCEPTION_CODE_INSTRUCTION_PAGE_FAULT;
        exception = 1'b1;
    end else if(irq_exti) begin
        f2e_cause = EXCEPTION_CODE_EXTERNAL_INTERRUPT[3:0];
        f2e_interrupt = 1'b1;
    end else if(irq_timer) begin
        f2e_cause = EXCEPTION_CODE_TIMER_INTERRUPT[3:0];
        f2e_interrupt = 1'b1;
    end
end

always @* begin
    f2e_exc_start = 1'b0;
    next_pc = pc + 4;
    if(reseted) begin
        next_pc = RESET_VECTOR;
    end else if(exception || irq_timer || irq_exti || e2f_exc_start) begin
        next_pc = mtvec;
        f2e_exc_start = 1'b1;
    end else if(e2f_branchtaken) begin
        next_pc = e2f_branchtarget;
    end
end

always @* begin
    f2e_instr = `INSTRUCTION_NOP;
    f2e_pc = 0;
    c_cmd = `CACHE_CMD_NONE;
    c_address = pc;
    if(!c_reset_done) begin
        
    end else begin
        if(e2f_ready) begin
            c_cmd = e2f_flush ? `CACHE_CMD_FLUSH_ALL : `CACHE_CMD_EXECUTE;
            c_address = (c_response == `CACHE_RESPONSE_WAIT) ? pc : next_pc;
        end
        if(reseted) begin
            // Output nop
        end else if(c_response == `CACHE_RESPONSE_DONE && !flushing) begin
            f2e_instr = c_load_data;
            f2e_pc = pc;
        end else if(c_response == `CACHE_RESPONSE_IDLE || flushing) begin
            f2e_instr = saved_instr;
            f2e_pc = pc;
        end
    end
end


always @(posedge clk) begin
    if(!rst_n) begin
        reseted <= 1'b1;
        flushing <= 1'b0;
    end else begin
        if(!c_reset_done) begin
            // nothing to do
        end else begin
            if(c_response != `CACHE_RESPONSE_WAIT)
                pc <= next_pc;
            if(reseted) begin
                reseted <= 1'b0;
            end else if(e2f_ready) begin
                if(e2f_flush) begin
                    flushing <= 1'b1;
                end else if(c_response == `CACHE_RESPONSE_DONE) begin
                    flushing <= 1'b0;
                end
            end
            if(c_response == `CACHE_RESPONSE_DONE && !flushing) begin
                saved_instr <= c_load_data;
            end
        end
    end
end


endmodule