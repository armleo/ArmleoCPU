////////////////////////////////////////////////////////////////////////////////
// 
// This file is part of ArmleoCPU.
// ArmleoCPU is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// ArmleoCPU is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with ArmleoCPU.  If not, see <https://www.gnu.org/licenses/>.
// 
// Copyright (C) 2016-2021, Arman Avetisyan, see COPYING file or LICENSE file
// SPDX-License-Identifier: GPL-3.0-or-later
// 
// Filename:    armleocpu_fetch.v
// Project:	ArmleoCPU
//
// Purpose:	ArmleoCPU's Fetch unit
//
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_fetch #(
    parameter RESET_VECTOR = 32'h0000_2000;
) (
    input                   clk,
    input                   rst_n,

    // Cache IF
    input                   c_done,
    input      [3:0]        c_response,

    output reg [3:0]        c_cmd,
    output     [31:0]       c_address,
    input      [31:0]       c_load_data,

    // Interrupts
    input                   interrupt_pending,
    input                   dbg_mode,

    output                  busy,

    // towards execute
    output reg              f2d_valid,
    output reg [`F2E_TYPE_WIDTH-1:0]
                            f2d_type,
    output reg [31:0]       f2d_instr,
    output reg [31:0]       f2d_pc,

    // from execute
    input                   e2f_ready,
    input      [`ARMLEOCPU_E2F_CMD_WIDTH-1:0]
                            e2f_cmd,
    input      [31:0]       e2f_branchtarget

);


reg active;

reg [31:0] pc;

reg aborted;
reg flush;

assign flushing = 
        flush || (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_FLUSH);

assign aborting =
        aborted || (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_ABORT);

assign branching = 
        branched || (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_START_BRANCH);
reg [31:0] branched_target = ;

wire branching_target = (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_START_BRANCH) ? e2f_branchtarget : branched_target;

//assign start_new_fetch = ((!active) || (active && c_done)) && !dbg_mode && (e2f_ready && e2f_cmd != `ARMLEOCPU_E2F_CMD_ABORT);

always @* begin
    if(!active) begin
        if(dbg_mode || aborting) begin
            // Dont start new fetch
        end else if(flush) begin
            // Issue flush
        end else if(branching) begin
            c_cmd = 
            c_address = branching_target;
        end else begin
            // Can start new fetch at pc + 4
            
            c_address = pc + 4;
        end
    end else if(active && !c_done) begin
        // Continue issuing whatever we were issuing
        c_cmd = r_cmd;
        c_address = pc;
        busy = 1;
    end else if(active && c_done) begin // no active request
        c_cmd = next_cmd;
        c_address = next_pc;
        busy = 1;
        
        if(dbg_mode) begin
            // Dont start new fetch
        end else if(aborting) begin
            
        end else if(flush) begin

        end else if(branching) begin

        end else begin
            // Can start new fetch at pc + 4
        end
    end


    /*
    else if() begin
        if(interrupt_pending && active && c_done) begin
            active_nxt = 0;
            f2d_valid = 0;
        end else if(dbg_mode && active && c_done) begin
            active_nxt = 0;
            f2d_valid = 0;
        end else if((flush || (e2f_cmd == `ARMLEOCPU_E2F_CMD_FLUSH)) && active) begin
            active_nxt = 0;
            f2d_valid = 0;
        end else if(interrupt_pending && !active) begin
            f2d_valid = 1;
            f2d_type = `F2E_TYPE_INTERRUPT_PENDING;
            active_nxt = 0;
        end else if(dbg_mode && !active) begin
            f2d_valid = 0;
            active_nxt = 0;
        end else if((flush || (e2f_cmd == `ARMLEOCPU_E2F_CMD_FLUSH)) && active) begin
            f2d_valid = 0;
            active_nxt = 0;
        end else if(flush && !active) begin
            c_cmd = `CACHE_CMD_FLUSH_ALL;
            active_nxt = 1;
        end else begin
            c_address = next_pc;
            c_cmd = `CACHE_CMD_EXECUTE;
            active_nxt = 1;
            pc_nxt = next_pc;
        end

    if(c_done) begin
        if(!flush) begin

        end else if(!aborted) begin

        end else begin

        end
    end

    if(start_new_fetch) begin
        c_address = pc_nxt;
        if(flush) begin
            c_cmd = `CACHE_CMD_FLUSH_ALL;
        end else begin
            c_cmd = `CACHE_CMD_EXECUTE;
        end
    end else begin
        c_cmd = saved_cmd;
        c_address = pc;
    end*/
end


endmodule

/*

module armleocpu_fetch(
    input                   clk,
    input                   rst_n,

    // IRQ/Exception Base address
    input [31:0]            csr_mtvec,
    input [31:0]            csr_stvec,

    // CSR Registers
    input [1:0]             csr_mcurrent_privilege,
    input [15:0]            csr_medeleg,

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
    
    output reg              instret_incr,

    // towards execute
    output reg              f2e_instr_valid,
    output reg [31:0]       f2e_instr,
    output reg [31:0]       f2e_pc,
    output reg              f2e_exc_start,
    output reg [31:0]       f2e_epc,
    output reg [31:0]       f2e_cause,
    output reg  [1:0]       f2e_exc_privilege,

    // from execute
    input                                               e2f_ready,
    input      [`ARMLEOCPU_E2F_CMD_WIDTH-1:0]           e2f_cmd,
    input      [31:0]                                   e2f_bubble_jump_target,
    input      [31:0]                                   e2f_branchtarget
);

parameter RESET_VECTOR = 32'h0000_2000;


//STATE
reg [31:0] pc;
reg flushing;
reg bubble;
reg [31:0] saved_instr;

//SIGNALS
reg [31:0] pc_nxt;
reg flushing_nxt;
reg bubble_nxt;
reg dbg_mode_nxt;
reg f2e_exc_start_nxt;
reg [31:0] f2e_cause_nxt;
reg [31:0] f2e_epc_nxt;

reg [1:0] f2e_exc_privilege_nxt;


wire cache_done = c_response == `CACHE_RESPONSE_DONE;
wire cache_error =  (c_response == `CACHE_RESPONSE_ACCESSFAULT) ||
                    (c_response == `CACHE_RESPONSE_MISSALIGNED) ||
                    (c_response == `CACHE_RESPONSE_PAGEFAULT);
wire cache_idle =   (c_response == `CACHE_RESPONSE_IDLE);
wire cache_wait =   (c_response == `CACHE_RESPONSE_WAIT);

wire new_fetch_begin =
                    (dbg_mode && dbg_exit_request && (cache_idle || cache_done)) ||
                    (e2f_ready && (cache_done || cache_idle || cache_error));

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


// if dbg_mode ->
//     output NOP
// else if cache_wait -> NOP
// else if cache_done ->
//     if flushing -> NOP
//     else -> output data from cache
// else if idle ->
//     if saved_valid -> output saved_instr
//     else -> output NOP
// else if error ->
//     output NOP, start Exception



always @* begin
    f2e_instr = c_load_data;
    f2e_pc = pc;
    f2e_instr_valid = 1;
    if(!c_reset_done) begin
        f2e_instr_valid = 0;
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
            end else
                f2e_instr = c_load_data;
        end else if(cache_idle) begin
            if(!bubble)
                f2e_instr = saved_instr;
            else begin
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


// 
// Command logic (not up to date)
//     state:
//         dbg_mode = 0, flushing = 0, bubble = 1, pc = reset_vector
    
//     if dbg_mode && !dbg_exit_request
//         -> debug mode, handle debug commands;
//         if dbg_set_pc then set bubble to 1
//     else if flushing
//         if(cache_done) ->
//             send NOP
//             set flushing to zero
//         else ->
//             send flush
//     else if bubble && cache_idle
//         start fetching from pc
//         bubble = 0
//     esle if new_fetch_begin
//         if dbg_request ->
//             dbg_mode = 1
//         else if irq && irq_enabled ->
//             bubble = 1
//             pc_nxt = mtvec
//             start_exception(INTERRUPT);
//         else if e2f_exc_start
//             bubble = 1
//             pc_nxt = mtvec
//         else if e2f_exc_mret
//             bubble = 1
//             pc_nxt = mepc
//         else if e2f_exc_sret
//             bubble = 1
//             pc_nxt = sepc
//         else if e2f_branchtaken
//             pc_nxt = branchtarget
//         else if e2f_flush
//             bubble = 1
//             pc_nxt = pc + 4
//             cmd = flush
//             flushing = 1
//         else if cache_error
//             buble = 1
//             pc_nxt = mtvec
//             start_exception(FETCH_ERROR)
//         else
//             pc_nxt = pc + 4
//     else
//         continue fetching from pc
//     new_fetch_begin =   (dbg_mode && dbg_exit_request && (cache_idle || cache_done)) ||
//                         (e2f_ready && (cache_done || cache_idle || cache_error)) ||
    
// 



always @* begin
    pc_nxt = pc;
    bubble_nxt = bubble;
    dbg_mode_nxt = dbg_mode;
    c_cmd = `CACHE_CMD_NONE;
    f2e_exc_start_nxt = 1'b0;
    f2e_cause_nxt = 0;
    f2e_epc_nxt = 0;
    f2e_exc_privilege_nxt = 0;
    
    flushing_nxt = flushing;
    instret_incr = 0;

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
            instret_incr = 1;
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
*/


`include "armleocpu_undef.vh"
