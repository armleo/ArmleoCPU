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
    parameter RESET_VECTOR = 32'h0000_2000
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
// Fetch will abort its current fetch and will start fetching instruction
// In location specified in the abort command.

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

`DEFINE_REG_REG_NXT(1, aborted, aborted_nxt, clk)
`DEFINE_REG_REG_NXT(1, branched, branched_nxt, clk)
`DEFINE_REG_REG_NXT(1, flushed, flushed_nxt, clk)

wire flushing = 
        flushed || (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_FLUSH);

wire aborting =
        aborted || (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_ABORT);

wire branching = 
        branched || (e2f_ready && e2f_cmd == `ARMLEOCPU_E2F_CMD_START_BRANCH);

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
    aborted_nxt = aborted;
    flush_nxt = flush;
    branched_nxt = branched;
    branched_target_nxt = branched_target;

    if(!rst_n) begin
        branched_target_nxt = RESET_VECTOR;
        branched_nxt = 1;
        aborted_nxt = 0;
        flushed_nxt = 0;


        pc_nxt = 0;
        // This will be overwritten anyway, BUT it should be reseted anyway
        // Just in case it's stuck in metastate

        // Pretend that we accepted a branch by setting branched
        // If branched is set and no instruction fetch is active
        // Then it will continue execution from branch_target
    end else begin
        if(!active) begin
            if(dbg_mode || aborting) begin
                // Dont start new fetch
            end else if(flushing) begin
                // Issue flush
            end else if(branching) begin
                c_cmd = 
                c_address = branching_target;
                pc_nxt = branching_target;
            end else begin
                // Can start new fetch at pc + 4
                c_address = pc + 4;
            end
        end else if(active && !c_done) begin
            // Continue issuing whatever we were issuing
            c_cmd = r_cmd;
            c_address = pc;
            busy = 1;

            // Remember all E2F's
            // TODO: Assert that no E2Fs will get overwritten
        end else if(active && c_done) begin // no active request
            c_cmd = next_cmd;
            c_address = next_pc;
            busy = 1;
            
            if(aborting) begin
                f2d_valid = 0; // Request that was aborted
            end else if(flushing) begin
                flush_nxt = 0;
            end else if() begin

            end

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
        end
    end
end


endmodule


`include "armleocpu_undef.vh"
