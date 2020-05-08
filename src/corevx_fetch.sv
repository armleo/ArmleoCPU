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
    output reg              f2e_cause_interrupt, // cause[31]

    input                   e2f_ready,
    input                   e2f_exc_start,
    input                   e2f_flush,
    input                   e2f_branchtaken,
    input      [31:0]       e2f_branchtarget
);

parameter RESET_VECTOR = 32'h0000_2000;

`include "corevx_cache.svh"
`include "ld_type.svh"
`include "corevx_exception.svh"

`define INSTRUCTION_NOP ({12'h0, 5'h0, 3'b000, 5'h0, 7'b00_100_11});

/*
Output instr logic
if reseted && c_response == IDLE -> NOP
else if c_response == IDLE && !after_flush -> saved_instr, saved_pc
else if c_response == done -> output data from cache
else if c_response == WAIT -> NOP
else if c_response == ACCESFAULT || PAGEFAULT || MISSALIGNED -> NOP
else if c_response == IDLE && after_flush -> NOP


Command logic
    if flushing && c_response != DONE -> send flush; flushing <= 1;
    else if flushing && c_response == DONE -> after_flush <= 1; flushing <= 0; send NONE
    else if c_response == WAIT -> fetch from pc
    else if reseted || c_response == ERROR || (e2f_ready && (c_response == DONE || c_response == IDLE)) ->
        if reseted -> fetch from reset vector
        else if irq || exception
                -> exc_start = 1
                -> fetch mtvec
        else if e2f_exc_start -> fetch mtvec, no need for exc_start
        else if branch -> fetch branch target
        else if e2f_ready && e2f_flush -> FLUSH
        else if c_response == error -> fetch mtvec, exc_start = 1
        else -> fetch from pc + 4

        after flush <= 0;
        reseted <= 0;
*/
/*SIGNALS*/
reg [31:0] next_pc;

wire cache_done = c_response == `CACHE_RESPONSE_DONE;
wire cache_error =  (c_response == `CACHE_RESPONSE_ACCESSFAULT) ||
                    (c_response == `CACHE_RESPONSE_MISSALIGNED) ||
                    (c_response == `CACHE_RESPONSE_PAGEFAULT);
wire cache_idle =   (c_response == `CACHE_RESPONSE_IDLE);
wire cache_wait =   (c_response == `CACHE_RESPONSE_WAIT);


// state
reg reseted;
reg flushing;
reg after_flush;


reg [31:0] pc;
reg [31:0] saved_instr;

always @(posedge clk) begin
    saved_instr <= f2e_instr;
    pc <= next_pc;
end

always @* begin
    f2e_cause = 0;
    f2e_cause_interrupt = 1'b0;
    if(e2f_exc_start) begin
        
    end else if(c_response == `CACHE_RESPONSE_MISSALIGNED) begin
        f2e_cause = EXCEPTION_CODE_INSTRUCTION_ADDRESS_MISALIGNED;
    end else if(c_response == `CACHE_RESPONSE_ACCESSFAULT) begin
        f2e_cause = EXCEPTION_CODE_INSTRUCTION_ACCESS_FAULT;
    end else if(c_response == `CACHE_RESPONSE_PAGEFAULT) begin
        f2e_cause = EXCEPTION_CODE_INSTRUCTION_PAGE_FAULT;
    end else if(irq_exti) begin
        f2e_cause = EXCEPTION_CODE_EXTERNAL_INTERRUPT[3:0];
        f2e_cause_interrupt = 1'b1;
    end else if(irq_timer) begin
        f2e_cause = EXCEPTION_CODE_TIMER_INTERRUPT[3:0];
        f2e_cause_interrupt = 1'b1;
    end
end

always @* begin
    f2e_instr = `INSTRUCTION_NOP;
    f2e_pc = pc;
    next_pc = pc;
    c_cmd = `CACHE_CMD_NONE;
    f2e_exc_start = 1'b0;
    if(!c_reset_done) begin
        
    end else begin
        // Output instr logic
        if (reseted && cache_idle) begin
            // NOP
        end else if(cache_idle && !after_flush) begin
            f2e_instr = saved_instr;
        end else if(cache_done) begin
            f2e_instr = c_load_data;
        end else if(cache_wait) begin
            // NOP
        end else if(cache_error) begin
            // NOP
        end else if(cache_idle && after_flush) begin
            // NOP
        end
        // Command logic
        if(flushing) begin
            if(!cache_done) begin
                c_cmd = `CACHE_CMD_FLUSH_ALL;
            end else begin
                // cmd = NONE
            end
        end else if(cache_wait) begin
            c_cmd = `CACHE_CMD_EXECUTE;
            //next_pc = pc;
        end else if(reseted || cache_error || (e2f_ready && (cache_done || cache_idle))) begin
            c_cmd = `CACHE_CMD_EXECUTE;
            if (reseted) begin
                next_pc = RESET_VECTOR;
            end else if(irq_exti || irq_timer) begin
                f2e_exc_start = 1'b1;
                next_pc = mtvec;
            end else if(e2f_branchtaken) begin
                next_pc = e2f_branchtarget;
            end else if(e2f_ready && e2f_flush) begin
                c_cmd = `CACHE_CMD_FLUSH_ALL;
            end else if(cache_error) begin
                f2e_exc_start = 1'b1;
                next_pc = mtvec;
            end else begin
                next_pc = pc + 4;
            end
        end
    end
    c_address = next_pc;
end


always @(posedge clk) begin
    if(!rst_n) begin
        reseted <= 1'b1;
        flushing <= 1'b0;
        after_flush <= 1'b0;
    end else begin
        if(!c_reset_done) begin
            // nothing to do
        end else begin
            if(flushing) begin
                if(!cache_done) begin
                    
                end else begin
                    `ifdef DEBUG_FETCH
                        $display("[%d][Fetch] Flush done", $time);
                    `endif
                    flushing <= 1'b0;
                    after_flush <= 1'b1;
                end
            end else if(cache_wait) begin
                
            end else if(reseted || cache_error || (e2f_ready && (cache_done || cache_idle))) begin
                after_flush <= 1'b0;
                reseted <= 1'b0;
                if (reseted) begin
                    `ifdef DEBUG_FETCH
                    $display("[%d][Fetch] Starting fetch from reset vector", $time);
                    `endif
                end else if(irq_exti || irq_timer) begin
                    `ifdef DEBUG_FETCH
                    $display("[%d][Fetch] Starting interrupt: %s", $time, irq_timer ? "timer" : "exti");
                    `endif
                end else if(e2f_branchtaken) begin
                    `ifdef DEBUG_FETCH
                    $display("[%d][Fetch] Branch taken: 0x%X, next_pc = 0x%X", $time, e2f_branchtarget, next_pc);
                    `endif
                end else if(e2f_ready && e2f_flush) begin
                    `ifdef DEBUG_FETCH
                    $display("[%d][Fetch] Starting flush", $time);
                    `endif
                    flushing <= 1'b1;
                end else if(cache_error) begin
                    `ifdef DEBUG_FETCH
                    $display("[%d][Fetch] Starting fetch error, next_pc = 0x%X", $time, next_pc);
                    `endif
                end else begin
                    `ifdef DEBUG_FETCH
                    $display("[%d][Fetch] Starting fetch pc+4; pc = 0x%X, next_pc=0x%X", $time, pc, next_pc);
                    `endif
                end
            end
        end
    end
end


endmodule