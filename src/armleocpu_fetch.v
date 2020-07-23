module armleocpu_fetch(
    input                   clk,
    input                   rst_n,

    // IRQ/Exception Base address
    input [31:0]            csr_mtvec,

    // From debug
    input                   dbg_request,
    input                   dbg_set_pc,
    input                   dbg_exit_request,
    input [31:0]            dbg_pc,

    // To Debug
    output reg              dbg_mode,
    // async signal:
    output reg              dbg_done,





    // Cache IF
    input      [3:0]        c_response,
    input                   c_reset_done,

    output reg [3:0]        c_cmd,
    output     [31:0]       c_address,
    input      [31:0]       c_load_data,

    // Interrupts
    input                   interrupt_pending_csr,
    input      [31:0]       interrupt_cause,
    input      [31:0]       interrupt_target_pc,
    input       [1:0]       interrupt_target_privilege,
    
    // towards execute
    output reg [31:0]       f2e_instr,
    output reg [31:0]       f2e_pc,
    output reg              f2e_exc_start,
    output reg [31:0]       f2e_epc,
    output reg [31:0]       f2e_cause,
    output reg  [1:0]       f2e_exc_privilege,

    // from execute
    input                   e2f_ready,
    input      [1:0]        e2f_cmd,
    input      [31:0]       e2f_bubble_branch_target,
    input      [31:0]       e2f_branchtarget
    input      [31:0]       e2f_cause,
    input       [1:0]       e2f_exc_privilege

);

parameter RESET_VECTOR = 32'h0000_2000;

`include "armleocpu_cache.vh"
`include "ld_type.vh"
`include "armleocpu_exception.vh"
`include "armleocpu_privilege.vh"
`include "armleocpu_e2f_cmd.vh"

`define INSTRUCTION_NOP ({12'h0, 5'h0, 3'b000, 5'h0, 7'b00_100_11});

/*STATE*/
reg [31:0] pc;
reg flushing;
reg bubble;
reg [31:0] saved_instr;

/*SIGNALS*/
reg [31:0] pc_nxt;
reg flushing_nxt;
reg bubble_nxt;
reg dbg_mode_nxt;
reg f2e_exc_start_nxt;
reg [31:0] f2e_cause_nxt;
reg [31:0] f2e_epc_nxt;

reg [1:0] f2e_exc_privilege_nxt;

wire new_fetch_begin =
                    (dbg_mode && dbg_exit_request && (cache_idle || cache_done)) ||
                    (e2f_ready && (cache_done || cache_idle || cache_error));
wire cache_done = c_response == `CACHE_RESPONSE_DONE;
wire cache_error =  (c_response == `CACHE_RESPONSE_ACCESSFAULT) ||
                    (c_response == `CACHE_RESPONSE_MISSALIGNED) ||
                    (c_response == `CACHE_RESPONSE_PAGEFAULT);
wire cache_idle =   (c_response == `CACHE_RESPONSE_IDLE);
wire cache_wait =   (c_response == `CACHE_RESPONSE_WAIT);
wire [31:0] pc_plus_4 = pc + 4;


assign c_address = pc_nxt;

always @(posedge clk)
    flushing <= flushing_nxt;

always @(posedge clk)
    bubble <= bubble_nxt;

always @(posedge clk)
    pc <= pc_nxt;

always @(posedge clk)
    saved_instr <= f2e_instr;

// reg dbg_mode;
always @(posedge clk)
    dbg_mode <= dbg_mode_nxt;
always @(posedge clk)
    f2e_cause <= f2e_cause_nxt;
always @(posedge clk)
    f2e_exc_start <= f2e_exc_start_nxt;
always @(posedge clk)
    f2e_exc_privilege <= f2e_exc_privilege_nxt;
always @(posedge clk)
    f2e_epc <= f2e_epc_nxt;
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
    f2e_instr = `INSTRUCTION_NOP;
    f2e_pc = pc;
    if(!c_reset_done) begin
        
    end else begin
        // Output instr logic
        if (dbg_mode) begin
            // NOP
        end else if(cache_wait) begin
            // NOP
        end else if(cache_done) begin
            if(flushing) begin
                // NOP
            end else
                f2e_instr = c_load_data;
        end else if(cache_idle) begin
            if(!bubble)
                f2e_instr = saved_instr;
            else begin
                // NOP
            end
        end else if(cache_error) begin
            // NOP
        end
        // TODO: Add check for else
    end
end


/*
Command logic
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
    else if bubble && cache_idle
        start fetching from pc
        bubble = 0
    esle if new_fetch_begin
        if dbg_request ->
            dbg_mode = 1
        else if irq && irq_enabled ->
            bubble = 1
            pc_nxt = mtvec
            start_exception(INTERRUPT);
        else if e2f_exc_start
            bubble = 1
            pc_nxt = mtvec
        else if e2f_exc_mret
            bubble = 1
            pc_nxt = mepc
        else if e2f_exc_sret
            bubble = 1
            pc_nxt = sepc
        else if e2f_branchtaken
            pc_nxt = branchtarget
        else if e2f_flush
            bubble = 1
            pc_nxt = pc + 4
            cmd = flush
            flushing = 1
        else if cache_error
            buble = 1
            pc_nxt = mtvec
            start_exception(FETCH_ERROR)
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
    f2e_exc_start_nxt = 1'b0;
    f2e_cause_nxt = 0;
    f2e_epc_nxt = 0;
    f2e_exc_privilege_nxt = 0;
    dbg_done = 0;
    
    
    if(!rst_n) begin
        bubble_nxt = 1;
        flushing_nxt = 0;
        dbg_mode_nxt = 0;
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
        end else if(bubble && cache_idle && e2f_ready) begin
            c_cmd = `CACHE_CMD_EXECUTE;
            pc_nxt = pc;
            bubble_nxt = 0;
            f2e_exc_start_nxt = 0;
            f2e_cause_nxt = 0;
            dbg_mode_nxt = 0;
            f2e_exc_privilege_nxt = 0;
        end else if (new_fetch_begin) begin
            dbg_mode_nxt = 0;
            if (dbg_request) begin
                dbg_mode_nxt = 1;
            end else if(interrupt_pending_csr) begin
                bubble_nxt = 1;
                pc_nxt = interrupt_target_pc;
                f2e_exc_start_nxt = 1'b1;
                f2e_epc_nxt = pc;
                f2e_cause_nxt = interrupt_cause;
                f2e_exc_privilege_nxt = interrupt_target_privilege;
            end else if (e2f_cmd == `ARMLEOCPU_E2F_CMD_BUBBLE_BRANCH) begin
                bubble_nxt = 1;
                pc_nxt = e2f_bubble_branch_target;
                f2e_exc_start_nxt = 1'b1;
                f2e_epc_nxt = pc;
                f2e_cause_nxt = e2f_cause;
                f2e_exc_privilege_nxt = e2f_exc_privilege;
            end else if (e2f_cmd == `ARMLEOCPU_E2F_CMD_FLUSH) begin
                bubble_nxt = 1;
                flushing_nxt = 1;
                pc_nxt = pc_plus_4;
            end else if (e2f_cmd == `ARMLEOCPU_E2F_CMD_BRANCHTAKEN) begin
                pc_nxt = e2f_branchtarget;
                c_cmd = `CACHE_CMD_EXECUTE;
            end else if (cache_error) begin
                bubble_nxt = 1;
                f2e_epc_nxt = pc;
                f2e_exc_start_nxt = 1'b1;
                
                if(c_response == `CACHE_RESPONSE_MISSALIGNED) begin
                    f2e_cause_nxt = `EXCEPTION_CODE_INSTRUCTION_ADDRESS_MISSALIGNED;
                end else if(c_response == `CACHE_RESPONSE_ACCESSFAULT) begin
                    f2e_cause_nxt = `EXCEPTION_CODE_INSTRUCTION_ACCESS_FAULT;
                end else if(c_response == `CACHE_RESPONSE_PAGEFAULT) begin
                    f2e_cause_nxt = `EXCEPTION_CODE_INSTRUCTION_PAGE_FAULT;
                end
                
                if((csr_mcurrent_privilege != `ARMLEOCPU_PRIVILEGE_MACHINE) && csr_medeleg[f2e_cause_nxt]) begin
                    f2e_exc_privilege_nxt =  `ARMLEOCPU_PRIVILEGE_SUPERVISOR;
                    pc_nxt = csr_stvec;
                end else begin
                    f2e_exc_privilege_nxt =  `ARMLEOCPU_PRIVILEGE_MACHINE;
                    pc_nxt = csr_mtvec;
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