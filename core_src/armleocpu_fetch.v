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

module armleocpu_fetch (
    input                   clk,
    input                   rst_n,

    // Reset vector input
    // Should be valid when rst_n is asserted
    // (To clarify terminalogy:
    //      rst_n = 0 = reset condition = rst_n asserted)

    input [31:0]            reset_vector,


    // Cache IF
    input                   c_done,
    input      [3:0]        c_response,

    output reg [3:0]        c_cmd,
    output reg [31:0]       c_address,
    input      [31:0]       c_load_data,

    // Interrupts
    input                   interrupt_pending,
    input                   dbg_mode,

    output reg              busy,

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
/*
// Fetch unit
// This unit sends fetch command to cache
// It is required to keep command the same until cache responds
// So there is three states:
//      non active (no cmd issued in the past, next cmd can be issued)
//      active and last request is done (next cmd can be issued)
//      active and last request is not done ("stalled" or currently processing the command)
// What we are doing is everytime we see non active or (active and last request is done)
// conditions fetch send snext cmd depending on current command of E2F bus
// Or we dont send any if E2F tells us to abort

// This fetch was designed for 3 stage pipeline in mind.
// As of currently slowest and highest delay element is cache response generation
// So there is no purpose on registering E2F, so it is assumed that E2F will be directly connected
// To decode unit.

// Decode unit will abort operations as early as possible.
// In some cases execute may cause interrupt or exception.
// This means that decode stage will get branch taken and
// same command will be issued to fetch
// Fetch will not start new fetch while abort command is active

// When current fetch is done or there is no active command request
// and there was branch taken command then
// next command will be executed from branch
// If no branch is executed then continue fetching from PC + 4

// Other edge case include pending interrupt
// In case there is pending interrupt then according input signal will be set.
// Then fetch unit will issue F2D with type == INTERRUPT PENDING
// and will continue doing so until Execute unit will start interrupt handling
// As interrupt handling starts xIE for current privilege level will be set to zero
// causing interrupt pending signal to go low. Then Branch taken command will be issued
// And fetch unit will start fetching from location of xTVEC which will be passed in branch target

// For debugging dbg_mode input is used
// When dbg_mode is set then when instruction fetch is done
// next fetch command will not be issued. When no active command is sent,
// then busy signal will go low

// When Busy signal is low and dbg_mode is set then fetching is stopped
// This allows debug unit to issue commands in dbg_cmd signal
// Then dbg_cmd_busy will go high and until current command is done
// It will not be deasserted.

// Instructions that this unit will do are:
// Set the PC: which will be treatede as "branch taken"
// IFlush: Which will be treated as "flush command from pipeline"

// As debug unit will not issue commands until all pipeline stages
// will be in idle mode

// TODO: What will happen to commands after debug mode is set
// May be just register all commands? Only abort and branch taken can be issued
// at the same time. This will mean that it can just accept all commands
// from pipeline

// TODO: What will happen if more than one E2F arrives with same command
// Probably just use earlist one because the pipeline should have been reset anyway
// So if it not then it's a BUG, so assert that this is not possible

// Naming -ed and -ing.
// -ed means that command was issued in the past
// -ing means that command is active right now or somewhere in the past
// aborted == abort command was recved in the past
// aborting == there is current cmd - abort or abort recved while fetch was in progress


`DEFINE_REG_REG_NXT(1, active, active_nxt, clk)
`DEFINE_REG_REG_NXT(32, pc, pc_nxt, clk)
`DEFINE_REG_REG_NXT(4, r_cmd, r_cmd_nxt, clk)

`DEFINE_REG_REG_NXT(1, branched, branched_nxt, clk)
`DEFINE_REG_REG_NXT(1, flushed, flushed_nxt, clk)

wire flushing = 
        flushed || (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_FLUSH);

wire aborting = (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_ABORT);
// Aborting does not need to be registered.
// Because abort will be issued right after successful fetch
// And will continue until branch taken is issued
// Abort will always be followed by branch taken


// TODO: Write formal rule for this

wire branching = 
        branched || (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_START_BRANCH);


`ifdef FORMAL_RULES
reg formal_reseted;

reg [3:0] last_cmd;

always @(posedge clk) begin
    formal_reseted <= formal_reseted || !rst_n;

    if(rst_n && formal_reseted) begin
        // TODD: Add requrment for E2F commands
        assert((c_cmd == `CACHE_CMD_FLUSH_ALL) || (c_cmd == `CACHE_CMD_EXECUTE) || (c_cmd == `CACHE_CMD_NONE))
        
        last_cmd <= c_cmd;


        // Cases:
        // last_cmd = NONE, c_cmd = x, if c_done -> ERROR
        // last_cmd != NONE, c_done = 0, if c_cmd != last_cmd -> ERROR
        // last_cmd != NONE, c_done = 1 -> NOTHING TO CHECK
        
        //      either last cycle c_done == 1 or c_cmd for last cycle == NONE
        // c_cmd != NONE -> check that
        //      either last cycle (c_done == 1 and last_cmd == NONE)
        //          or last_cmd != 
        if(last_cmd == `CACHE_CMD_NONE) begin
            assert(c_done == 0);
        end
        if((last_cmd != `CACHE_CMD_NONE) && (c_done == 0)) begin
            assert(last_cmd == c_cmd);
        end
    end
end
`endif

`DEFINE_REG_REG_NXT(32, branched_target, branched_target_nxt, clk)

wire [31:0] branching_target = (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_START_BRANCH) ? e2f_branchtarget : branched_target;

//assign start_new_fetch = ((!active) || (active && c_done)) && !dbg_mode && (e2f_ready && e2f_cmd != `ARMLEOCPU_E2F_CMD_ABORT);

wire [31:0] pc_plus_4 = pc + 4;


// Fetch starts
// Fetch ends, decode detects a CSR, sends ABORT, Fetch sees abort, does not start new fetch
// Fetch starts from aborted location, no abort


always @* begin
    c_cmd = `CACHE_CMD_NONE;
    c_address = pc;
    f2d_valid = 0;
    f2d_type = `F2E_TYPE_INSTR;
    f2d_instr = c_load_data;
    f2d_pc = pc;

    // Internal flip flops input signals
    pc_nxt = pc;
    flushed_nxt = flushed;
    branched_nxt = branched;
    branched_target_nxt = branched_target;
    r_cmd = r_cmd_nxt;

    if(!rst_n) begin
        branched_target_nxt = reset_vector;
        branched_nxt = 1;
        flushed_nxt = 0;


        pc_nxt = 0;
        // This will be overwritten anyway, BUT it should be reseted anyway
        // Just in case it's stuck in metastate or something

        // Pretend that we accepted a branch by setting branched
        // If branched is set and no instruction fetch is active
        // Then it will continue execution from branch_target, which is our reset_vector
    end else begin
        if(!active) begin
            if(dbg_mode || aborting) begin
                // Dont start new fetch
                f2d_valid = 0;
                
            end else if(flushing) begin
                // Issue flush
                c_cmd = `CACHE_CMD_FLUSH_ALL;
                r_cmd_nxt = `CACHE_CMD_FLUSH_ALL;
                active_nxt = 1;
                
            end else if(branching) begin
                c_cmd = `CACHE_CMD_EXECUTE;
                r_cmd_nxt = `CACHE_CMD_EXECUTE;
                c_address = branching_target;
                pc_nxt = branching_target;
                if(c_done)
                    f2d_valid = 1;
            end else begin
                // Can start new fetch at pc + 4
                c_cmd = `CACHE_CMD_EXECUTE;
                r_cmd_nxt = `CACHE_CMD_EXECUTE;
                c_address = pc_plus_4;
                pc_nxt = pc_plus_4;
                if(c_done)
                    f2d_valid = 1;
            end
            f2d_valid = 0;
        end else if(active && !c_done) begin
            // Continue issuing whatever we were issuing
            c_cmd = r_cmd;
            r_cmd_nxt = c_cmd;
            c_address = pc;
            busy = 1;

            if(e2f_ready && (e2f_cmd != `ARMLEOCPU_E2F_CMD_NONE)) begin
                if(e2f_cmd == `ARMLEOCPU_E2F_CMD_FLUSH) begin
                    `ifdef DEBUG_FETCH
                    // TODO: Check in synchronous section for flushed to be zero
                    `endif
                    flushed_nxt = 1;
                end
                if(e2f_cmd == `ARMLEOCPU_E2F_CMD_ABORT) begin
                    // Ignored
                end
                if(e2f_cmd == `ARMLEOCPU_E2F_CMD_START_BRANCH) begin
                    branched_nxt = 1;
                    branched_target_nxt = e2f_branchtarget;
                    `ifdef DEBUG_FETCH
                    // TODO: Check in synchronous section for branched to be zero
                    `endif
                end
            end
            // Remember all E2F's
            // TODO: Assert that no E2Fs will get overwritten
            // TODO: Keep the earlist E2F in memory

            // No need to register ABORT
        end else if(active && c_done) begin // no active request
            busy = 1;
            
            if(f2d_valid && !e2f_ready) begin
                // Dont start new fetch
            end else if(dbg_mode || aborting) begin
                // Dont start new fetch
                
            end else if(flushing) begin
                // Issue flush
                c_cmd = `CACHE_CMD_FLUSH_ALL;
                r_cmd_nxt = `CACHE_CMD_FLUSH_ALL;
                active_nxt = 1;
                
            end else if(branching) begin
                c_cmd = `CACHE_CMD_EXECUTE;
                r_cmd_nxt = `CACHE_CMD_EXECUTE;
                c_address = branching_target;
                pc_nxt = branching_target;
                if(c_done)
                    f2d_valid = 1;
            end else begin
                // Can start new fetch at pc + 4
                c_cmd = `CACHE_CMD_EXECUTE;
                r_cmd_nxt = `CACHE_CMD_EXECUTE;
                c_address = pc_plus_4;
                pc_nxt = pc_plus_4;
                if(c_done)
                    f2d_valid = 1;
            end
            
            if(e2f_ready) begin
                

                if(c_done && r_cmd == `CACHE_CMD_EXECUTE)
                        f2d_valid = 1;
            end
            f2d_valid = 0;
        end
    end
end
*/



endmodule


`include "armleocpu_undef.vh"
