

module armleocpu_fetch(
    input                   clk,
    input                   rst_n,

    // To Debug
    output reg              dbg_mode,
    // async signal:
    output reg              dbg_done,

    // towards execute
    output reg              f2d_instr_valid,
    output reg [31:0]       f2d_instr,
    output reg [31:0]       f2d_pc,
    output reg              f2d_instr_fetch_exception,
    output reg [31:0]       f2d_instr_fetch_exception_cause,
    output reg              f2d_interrupt_pending,
    
    
    // Interrupts
    input                   interrupt_pending,
    // from execute
    input                                               d2f_ready,
    input      [`ARMLEOCPU_E2F_CMD_WIDTH-1:0]           d2f_cmd,
    input      [31:0]                                   d2f_jump_target,

    // From debug
    input                   dbg_request,
    input                   dbg_set_pc,
    input                   dbg_exit_request,
    input [31:0]            dbg_pc,
);

parameter RESET_VECTOR = 32'h0000_2000;

`include "armleocpu_includes.vh"



/*STATE*/
reg [31:0] pc;
reg flushing;
reg bubble;
reg wait_for_d2f_cmd;
reg [31:0] saved_instr;

/*SIGNALS*/
reg [31:0] pc_nxt;
reg flushing_nxt;
reg bubble_nxt;
reg dbg_mode_nxt;
reg wait_for_d2f_cmd_nxt;

wire cache_done = c_response == `CACHE_RESPONSE_DONE;
wire cache_error =  (c_response == `CACHE_RESPONSE_ACCESSFAULT) ||
                    (c_response == `CACHE_RESPONSE_MISSALIGNED) ||
                    (c_response == `CACHE_RESPONSE_PAGEFAULT);
wire cache_idle =   (c_response == `CACHE_RESPONSE_IDLE);
wire cache_wait =   (c_response == `CACHE_RESPONSE_WAIT);

wire new_fetch_begin =
                    (dbg_mode && dbg_exit_request && (cache_idle || cache_done)) ||
                    (d2f_ready && (cache_done || cache_idle || cache_error));

wire [31:0] pc_plus_4 = pc + 4;


assign c_address = pc_nxt;

always @(posedge clk)
    flushing <= flushing_nxt;

always @(posedge clk)
    bubble <= bubble_nxt;

always @(posedge clk)
    wait_for_d2f_cmd <= wait_for_d2f_cmd_nxt;

always @(posedge clk)
    pc <= pc_nxt;

always @(posedge clk)
    saved_instr <= f2e_instr;

// reg dbg_mode;
always @(posedge clk)
    dbg_mode <= dbg_mode_nxt;

/*
if dbg_mode ->
    output NOP
else if cache_wait -> NOP
else if cache_done ->
    if flushing -> NOP
    else -> output data from cache
else if idle ->
    if saved_valid -> output saved_instr
    else -> output NOP
else if error ->
    output NOP, start Exception
*/


always @* begin
    f2e_instr = c_load_data;
    f2e_pc = pc;
    f2e_instr_valid = 0;
    if(!c_reset_done) begin
        
    end else begin
        // Output instr logic
        if (dbg_mode) begin
            // NOP
            f2e_instr_valid = 0;
        end else if(cache_wait) begin
            // NOP
            f2e_instr_valid = 0;
        end else if(cache_done) begin
            if(flushing) begin
                // NOP
                f2e_instr_valid = 0;
            end else begin
                f2e_instr = c_load_data;
                f2e_instr_valid = 1;
            end
        end else if(cache_idle) begin
            if(!bubble) begin
                f2e_instr = saved_instr;
                f2e_instr_valid = 1;
            end else begin
                // NOP
                f2e_instr_valid = 0;
            end
        end else if(cache_error) begin
            // NOP
            f2e_instr_valid = 0;
        end
        // TODO: Add check for else
    end
end


/*
Command logic (not up to date)
    state:
        dbg_mode = 0, flushing = 0, bubble = 1, pc = reset_vector
    
    if dbg_mode && !dbg_exit_request
        -> debug mode, handle debug commands;
        if dbg_set_pc then set bubble to 1
    else if flushing
        if(cache_done) ->
            send NOP
            set flushing to zero
        else ->
            send flush
    else if bubble && cache_idle && (e2f_ready || !f2e_instr_valid)
        start fetching from pc
        bubble = 0
    end else if (wait_for_d2f_cmd) begin
        if (e2f_cmd == `ARMLEOCPU_E2F_CMD_BUBBLE_JUMP) begin
            bubble_nxt = 1;
            pc_nxt = e2f_bubble_jump_target;
        end
    esle if new_fetch_begin
        if dbg_request ->
            dbg_mode = 1
        else if irq_pending ->
            bubble = 1
        else if e2f_exc_bubble_jump
            bubble = 1
            pc_nxt = e2f_bubble_jump_target
        else if e2f_flush
            bubble = 1
            pc_nxt = pc + 4
            cmd = flush
            flushing = 1
        else if e2f_branchtaken
            pc_nxt = branchtarget
        else if cache_error
            buble = 1
        else
            pc_nxt = pc + 4
    else
        continue fetching from pc
    new_fetch_begin =   (dbg_mode && dbg_exit_request && (cache_idle || cache_done)) ||
                        (e2f_ready && (cache_done || cache_idle || cache_error)) ||
    
*/



always @* begin
    pc_nxt = pc;
    bubble_nxt = bubble;
    dbg_mode_nxt = dbg_mode;
    c_cmd = `CACHE_CMD_NONE;
    
    flushing_nxt = flushing;
    instret_incr = 0;

    dbg_done = 0;
    wait_for_d2f_cmd_nxt = wait_for_d2f_cmd;

    
    if(!rst_n) begin
        bubble_nxt = 1;
        flushing_nxt = 0;
        dbg_mode_nxt = 0;
        wait_for_d2f_cmd_nxt = 0;
        pc_nxt = RESET_VECTOR;
    end else if(!c_reset_done) begin
        
    end else begin
        if (dbg_mode && !dbg_exit_request) begin
            dbg_done = cache_done;
            if(dbg_set_pc) begin
                pc_nxt = dbg_pc;
                bubble_nxt = 1;
                dbg_done = 1;
            end
        end else if (flushing) begin
            if (cache_done) begin
                // CMD = NONE
                flushing_nxt = 0;
            end else begin
                c_cmd = `CACHE_CMD_FLUSH_ALL;
            end
        end else if (wait_for_d2f_cmd) begin
            
            if (e2f_cmd == `ARMLEOCPU_E2F_CMD_BUBBLE_JUMP) begin
                bubble_nxt = 1;
                pc_nxt = e2f_bubble_jump_target;
                f2d_instr_fetch_exception_nxt = 0;
                f2d_interrupt_pending_nxt = 0;
            end
        end else if(bubble && cache_idle && (e2f_ready || !f2e_instr_valid)) begin
            c_cmd = `CACHE_CMD_EXECUTE;
            pc_nxt = pc;
            bubble_nxt = 0;
            dbg_mode_nxt = 0;
        end else if (new_fetch_begin) begin
            instret_incr = 1;
            dbg_mode_nxt = 0;
            if (dbg_request) begin
                dbg_mode_nxt = 1;
            end else if(interrupt_pending) begin
                wait_for_d2f_cmd_nxt = 1;
                f2d_interrupt_pending_nxt = 1;
                bubble_nxt = 1;
            end else if (e2f_cmd == `ARMLEOCPU_E2F_CMD_BUBBLE_JUMP) begin
                bubble_nxt = 1;
                pc_nxt = e2f_bubble_jump_target;
            end else if (e2f_cmd == `ARMLEOCPU_E2F_CMD_FLUSH) begin
                bubble_nxt = 1;
                flushing_nxt = 1;
                pc_nxt = pc_plus_4;
            end else if (e2f_cmd == `ARMLEOCPU_E2F_CMD_BRANCHTAKEN) begin
                pc_nxt = e2f_branchtarget;
                c_cmd = `CACHE_CMD_EXECUTE;
            end else if (cache_error) begin
                bubble_nxt = 1;
                f2d_instr_fetch_exception_nxt = 1'b1;
                wait_for_d2f_cmd_nxt = 1;
                if(c_response == `CACHE_RESPONSE_MISSALIGNED) begin
                    f2d_instr_fetch_exception_cause_nxt = `EXCEPTION_CODE_INSTRUCTION_ADDRESS_MISSALIGNED;
                end else if(c_response == `CACHE_RESPONSE_ACCESSFAULT) begin
                    f2d_instr_fetch_exception_cause_nxt = `EXCEPTION_CODE_INSTRUCTION_ACCESS_FAULT;
                end else if(c_response == `CACHE_RESPONSE_PAGEFAULT) begin
                    f2d_instr_fetch_exception_cause_nxt = `EXCEPTION_CODE_INSTRUCTION_PAGE_FAULT;
                end
            end else begin
                pc_nxt = pc_plus_4;
                c_cmd = `CACHE_CMD_EXECUTE;
            end
        end else if (e2f_ready) begin
            pc_nxt = pc;
            c_cmd = `CACHE_CMD_EXECUTE;
        end
    end
end




endmodule