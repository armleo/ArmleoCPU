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

    // Debug port
    input                           dbg_mode,
    input                           dbg_cmd_valid,
    input  [`DEBUG_CMD_WIDTH-1:0]   dbg_cmd,
    input [31:0]                    dbg_arg0,

    output reg                      dbg_cmd_ready,
    output reg                      dbg_pipeline_busy,

    // towards execute
    output reg              f2d_valid,
    output reg [`F2E_TYPE_WIDTH-1:0]
                            f2d_type,
    output reg [31:0]       f2d_instr,
    output reg [31:0]       f2d_pc,
    // TODO: Add f2d error signals

    // from execute
    input                   d2f_ready,
    input      [`ARMLEOCPU_D2F_CMD_WIDTH-1:0]
                            d2f_cmd,
    input      [31:0]       d2f_branchtarget

);

// Fetch unit
// This unit sends fetch command to cache
// It is required to keep command the same until cache responds
// Check fetch.xlsx for each possible case
// What we are doing is everytime we see non active or (active and last request is done)
// conditions fetch send next cmd depending on current command of D2F bus
// Or we dont send any if D2F tells us to abort

// This fetch was designed for 3 stage pipeline in mind.
// As of currently slowest and highest delay element is cache response generation
// So there is no purpose on registering D2F, so it is assumed that D2F will be directly connected
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
// then dbg_pipeline_busy signal will go low

// When Busy signal is low and dbg_mode is set then fetching is stopped
// This allows debug unit to issue commands in dbg_cmd signal
// Then dbg_cmd_dbg_pipeline_busy will go high and until current command is done
// It will not be deasserted.

// Instructions that this unit will do are:
// Set the PC: which will be treatede as "branch taken"
// IFlush: Which will be treated as "flush command from pipeline"

// As debug unit will not issue commands until all pipeline stages
// will be in idle mode

// TODO: What will happen to commands after debug mode is set
// Just reject them?
// May be just register all commands? Only flush and branch taken can be issued
// at the same time. This will mean that it can just accept all commands
// from pipeline

// What will happen if more than one D2F command arrives?
// It was decided that this is impossible.
// It's either branch first then all pipeline is reset so flush is not possible
// OR flush is issued, but decode will abort fetching of next instruction allowing
// flush to be issued instead.




// Naming -ed and -ing.
// -ed means that command was issued in the past
// -ing means that command is active right now or somewhere in the past
// aborted == abort command was recved in the past
// aborting == there is current cmd - abort or abort recved while fetch was in progress


reg [4-1:0] active_cmd;
always @(posedge clk) active_cmd <= c_cmd;

reg [32-1:0] pc;
always @(posedge clk) pc <= c_address;

`DEFINE_REG_REG_NXT(32, saved_load_data, saved_load_data_nxt, clk)
`DEFINE_REG_REG_NXT(1, saved_load_data_valid, saved_load_data_valid_nxt, clk)

`DEFINE_REG_REG_NXT(1, branched, branched_nxt, clk)
`DEFINE_REG_REG_NXT(32, branched_target, branched_target_nxt, clk)

`DEFINE_REG_REG_NXT(1, flushed, flushed_nxt, clk)

// Internal signals

wire active = active_cmd != `CACHE_CMD_NONE;

reg register_d2f_commands;
reg register_dbg_cmds;

wire flushing = 
        flushed || (d2f_ready && d2f_cmd == `ARMLEOCPU_D2F_CMD_FLUSH);

wire branching = 
        branched || (d2f_ready && d2f_cmd == `ARMLEOCPU_D2F_CMD_START_BRANCH);

wire [31:0] branching_target = (d2f_ready && (d2f_cmd == `ARMLEOCPU_D2F_CMD_START_BRANCH)) ? d2f_branchtarget : branched_target;

wire [31:0] pc_plus_4 = pc + 4;


`ifdef FORMAL_RULES
    reg formal_reseted;

    reg [3:0] formal_last_cmd;
    reg [31:0] formal_last_c_address;

    always @(posedge clk) begin
        formal_reseted <= formal_reseted || !rst_n;

        if(rst_n && formal_reseted) begin
            // TODD: Add requrment for D2F commands

            // TODO: Add requirment for F2D stage to not change
            assert((c_cmd == `CACHE_CMD_FLUSH_ALL) || (c_cmd == `CACHE_CMD_EXECUTE) || (c_cmd == `CACHE_CMD_NONE));
            
            formal_last_cmd <= c_cmd;
            formal_last_c_address <= c_address;
            


            // Cases:
            // formal_last_cmd = NONE, c_cmd = x, if c_done -> ERROR
            // formal_last_cmd != NONE, c_done = 0, if c_cmd != formal_last_cmd -> ERROR
            // formal_last_cmd != NONE, c_done = 1 -> NOTHING TO CHECK
            
            //      either last cycle c_done == 1 or c_cmd for last cycle == NONE
            // c_cmd != NONE -> check that
            //      either last cycle (c_done == 1 and formal_last_cmd == NONE)
            //          or formal_last_cmd != 
            
            if((formal_last_cmd != `CACHE_CMD_NONE) && (c_done == 0)) begin
                assert(formal_last_cmd == c_cmd);
                assert(formal_last_c_address == c_address);
            end

            if(formal_last_cmd == `CACHE_CMD_NONE) begin
                assert(c_done == 0);
            end
        end

        
    end
`endif


// Fetch starts
// Fetch ends, decode detects a CSR, sends d2f_ready to zero, Fetch sees stall, does not start new fetch
// Then decode stage passes aborting instruction to execute
// Execute send abort request to decode which means that decode passes abort to fetch
// Then Execute passes then branch taken request


always @* begin
    dbg_cmd_ready = 0;
    dbg_pipeline_busy = 1;
    register_d2f_commands = 0;
    register_dbg_cmds = 0;

    c_cmd = active_cmd;
    c_address = pc;
    f2d_valid = 0;
    f2d_type = `F2E_TYPE_INSTR;
    f2d_instr = c_load_data;
    f2d_pc = pc;

    // Internal flip flops input signals
    // Active and active cmd is assigned above
    saved_load_data_nxt = saved_load_data;
    saved_load_data_valid_nxt = saved_load_data_valid;

    branched_nxt = branched;
    branched_target_nxt = branched_target;

    flushed_nxt = flushed;
    

    if(!rst_n) begin
        c_cmd = `CACHE_CMD_NONE;
        c_address = reset_vector; // This will be registered by PC
        // While PC will get overwritten on first cycle after reset
        // We still reset it just in case it's stuck in metastate or something
        saved_load_data_valid_nxt = 0;

        branched_target_nxt = reset_vector;
        branched_nxt = 1;
        flushed_nxt = 0;

        // Pretend that we accepted a branch by setting branched
        // If branched is set and no instruction fetch is active
        // Then it will continue execution from branch_target, which is our reset_vector
    end else begin
        if(saved_load_data_valid) begin
            f2d_valid = 1;
            f2d_instr = saved_load_data;
            // TODO: feed saved errors to f2d;
        end else if(c_done && active && active_cmd == `CACHE_CMD_EXECUTE) begin
            f2d_valid = 1;
            f2d_instr = c_load_data;
            f2d_type = `F2E_TYPE_INSTR;
            // If d2f_ready then no need to stall fetching
            // Else still output the load data
            saved_load_data_valid_nxt = !d2f_ready;
            saved_load_data_nxt = c_load_data;
            // TODO: save errors to feed f2d;
        end else if(active) begin
            saved_load_data_valid_nxt = 0;
            f2d_valid = 0;
            // Currently active cache request,
            // but no response from cache yet
        end else if(interrupt_pending) begin
            // Currently no saved data and no command was issued
            // Now we can send interrupt pending to decode stage
            f2d_valid = 1;
            f2d_type = `F2E_TYPE_INTERRUPT_PENDING;
        end else begin
            // There is no data
            // No cache response
            // No active interrupt
            // And we didn't send cache request last cycle
            // Nothing to send to decode stage
            f2d_valid = 0;
        end

        if(saved_load_data_valid && !d2f_ready) begin
            // If currently have data and stalled then do nothing
        end else if(
            (saved_load_data_valid && d2f_ready) ||
            // If have data and not stalled
            !active ||
            // Or currently idle
            (active && c_done && d2f_ready)
            // Or active but the f2d was accepted
        ) begin
            // Then start fetching next instruction
            saved_load_data_valid_nxt = 0;
            
            if(dbg_mode) begin
                // Dont start new fetch
                dbg_pipeline_busy = active;
                register_d2f_commands = 1;
                register_dbg_cmds = 1;
                c_cmd = `CACHE_CMD_NONE;
                
            end else if(interrupt_pending) begin
                // Don't start new fetch
                c_cmd = `CACHE_CMD_NONE;
            end else if(flushing) begin
                // Issue flush
                c_cmd = `CACHE_CMD_FLUSH_ALL;
                flushed_nxt = 0;
                // TODO: If flushed then continue execution from flush_target
            end else if(branching) begin
                c_cmd = `CACHE_CMD_EXECUTE;
                c_address = branching_target;
                branched_nxt = 0;
            end else begin
                // Can start new fetch at pc + 4
                c_cmd = `CACHE_CMD_EXECUTE;
                c_address = pc_plus_4;
            end
        end else if(active && !c_done) begin
            // Continue issuing whatever we were issuing
            c_cmd = active_cmd;
            // c_address = pc; already set in logic above

            register_d2f_commands = 1;
            
            // Remember all D2F's
            // TODO: Assert that no D2Fs will get overwritten
            // TODO: Keep the earlist D2F in memory

            // No need to register ABORT
        end else begin
            c_cmd = `CACHE_CMD_NONE;
        end

        if(register_d2f_commands) begin
            if(d2f_ready && (d2f_cmd != `ARMLEOCPU_D2F_CMD_NONE)) begin
                if(d2f_cmd == `ARMLEOCPU_D2F_CMD_FLUSH) begin
                    flushed_nxt = 1;
                end else if(d2f_cmd == `ARMLEOCPU_D2F_CMD_START_BRANCH) begin
                    branched_nxt = 1;
                    branched_target_nxt = d2f_branchtarget;
                    
                end
            end
        end

        // Only cmd used by fetch is jump
        if(register_dbg_cmds) begin
            if(dbg_cmd_valid) begin
                if(dbg_cmd == `DEBUG_CMD_JUMP) begin
                    branched_nxt = 1;
                    branched_target_nxt = dbg_arg0;
                    dbg_cmd_ready = 1;
                end else begin
                    dbg_cmd_ready = 1;
                end
            end
        end
    end
end

`ifdef DEBUG_FETCH
always @(posedge clk) begin
    
end
`endif



endmodule


`include "armleocpu_undef.vh"
