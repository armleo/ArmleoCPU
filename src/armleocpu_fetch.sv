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
    input wire              clk,
    input wire              rst_n,


    
    output logic            req_valid,
    output reg [3:0]        req_cmd,
    output reg [31:0]       req_address,
    input  wire             req_ready,

    // Cache IF
    input wire              resp_valid,
    input wire [3:0]        resp_status,
    input wire [31:0]       resp_read_data,

    // Interrupts
    input wire              interrupt_pending,

    // Debug port
    input wire                      dbg_mode,
    input wire                      dbg_cmd_valid,
    input wire [`DEBUG_CMD_WIDTH-1:0] dbg_cmd,
    input wire [31:0]               dbg_arg0_i,
    output reg [31:0]               dbg_arg0_o,

    output reg                      dbg_cmd_ready,
    output reg                      dbg_pipeline_busy,

    // towards decode
    output reg              f2d_valid,
    output reg [`F2E_TYPE_WIDTH-1:0]
                            f2d_type,
    output reg [31:0]       f2d_instr,
    output reg [31:0]       f2d_pc,
    output reg  [3:0]       f2d_status,

    // from decode
    input wire              d2f_ready,
    input wire [`ARMLEOCPU_D2F_CMD_WIDTH-1:0]
                            d2f_cmd,
    input wire [31:0]       d2f_branchtarget

);

parameter [31:0] RESET_VECTOR = 32'h0000_1000;
// Fetch unit
// This unit sends fetch command to cache
// It is NOT required to keep command the same until cache accepts it
// What we are doing is everytime we see that response is ready
// or we didn't start any requests then we start new one
// then fetch sends next cmd to cache depending on current command of D2F bus
// Or we dont send any if D2F tells us to stall

// This fetch was designed for 3 stage pipeline in mind.
// As of currently slowest and highest delay element is cache response generation
// So there is no purpose on registering D2F, so it is assumed that D2F will be directly connected
// To decode unit. This just gives the fetch a little bit more freedom,
// but it is not a requirement

// Decode unit will abort operations as early as possible.
// In some cases execute may cause interrupt or exception.
// This means that decode stage will get branch taken and
// same command will be issued to fetch
// Fetch will continue execution from branch target

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

// Debug instructions that this unit will do are:
// Set the PC: which will be treated as "branch taken"
// I Cache flushin and commands are handled by Debug unit itself

// As debug unit will not issue commands until all pipeline stages
// will be in idle mode

// What will happen to commands after debug mode is set
// We just accept both debug commands and pipeline commands but prioritize debug commands

// What will happen if more than one D2F command arrives?
// It was decided that this is impossible.
// It's either branch first then all pipeline is reset so flush is not possible
// OR flush is issued, but decode will abort fetching of next instruction allowing
// flush to be issued before next instruction is even fetched.





// Variables below store Cache's response
// Because Cache will respond for one cycle only.
// If host connected to cache is not ready to accepts a response,
// Then it should not issue an request

// It is allowed to change req_* signals at any cycle. However the accepted request is

`DEFINE_REG_REG_NXT(32, saved_read_data, saved_read_data_nxt, clk)
`DEFINE_REG_REG_NXT(1, saved_read_data_valid, saved_read_data_valid_nxt, clk)
`DEFINE_REG_REG_NXT(4, saved_status, saved_status_nxt, clk)


// Naming -ed and -ing.
// -ed means that command was issued in the past
// -ing means that command is active right now or somewhere in the past
// branched == branch command was recved in the past
// branching == there is current cmd - branch or branch recved while fetch was in progress

`DEFINE_REG_REG_NXT(1, branched, branched_nxt, clk)
`DEFINE_REG_REG_NXT(32, branched_target, branched_target_nxt, clk)

`DEFINE_REG_REG_NXT(1, flushed, flushed_nxt, clk)


// Signals below are used to signal if commands need to be registered or not
reg register_d2f_commands;
reg register_dbg_cmds;

wire flushing = 
        flushed || (d2f_ready && d2f_cmd == `ARMLEOCPU_D2F_CMD_FLUSH);

wire branching = 
        branched || (d2f_ready && d2f_cmd == `ARMLEOCPU_D2F_CMD_START_BRANCH);

wire [31:0] branching_target = (d2f_ready && (d2f_cmd == `ARMLEOCPU_D2F_CMD_START_BRANCH)) ? d2f_branchtarget : branched_target;

wire [31:0] pc_plus_4 = pc + 4;

reg [32-1:0] pc;
always @(posedge clk)
    if(req_valid && req_ready)
        pc <= req_address;


// Shows which command is active, only valid if active is set
reg [3:0] saved_req_cmd;

always @(posedge clk)
    if(req_valid)
        saved_req_cmd <= req_cmd;

wire saved_req_cmd_is_flush = saved_req_cmd == `CACHE_CMD_FLUSH_ALL;


// Shows that there is a command in cache's pipeline
// Also signals that saved_req_* signals are valid.
`DEFINE_REG_REG_NXT(1, req_done, req_done_nxt, clk)



// start_fetch is used to signal that new fetch can be started
// However changes to registers are only allowed in a cycle that req_ready is set
// This is because start_fetch is raised for every cycle while Cache does not
// Accept the request
reg start_fetch;


assign req_valid = req_cmd != `CACHE_CMD_NONE;



/*
`ifdef FORMAL_RULES
    reg formal_reseted;

    reg [3:0] formal_last_cmd;
    reg [31:0] formal_last_req_address;

    always @(posedge clk) begin
        // TODO: Add formal rules for fetch logic
        
        formal_reseted <= formal_reseted || !rst_n;

        if(rst_n && formal_reseted) begin
            // TODD: Add requrment for D2F commands

            // TODO: Add requirment for F2D stage to not change
            assert((req_cmd == `CACHE_CMD_FLUSH_ALL) || (req_cmd == `CACHE_CMD_EXECUTE) || (req_cmd == `CACHE_CMD_NONE));
            
            formal_last_cmd <= req_cmd;
            formal_last_req_address <= req_address;
            
            if(!f2d_valid)
                assert(d2f_ready);
            
            //if(f2d_valid && (f2d_type == `F2E_TYPE_INTERRUPT_PENDING))
            //    assert(d2f_ready); // No longer required


            // Cases:
            // formal_last_cmd = NONE, req_cmd = x, if c_done -> ERROR
            // formal_last_cmd != NONE, c_done = 0, if req_cmd != formal_last_cmd -> ERROR
            // formal_last_cmd != NONE, c_done = 1 -> NOTHING TO CHECK
            
            //      either last cycle c_done == 1 or req_cmd for last cycle == NONE
            // req_cmd != NONE -> check that
            //      either last cycle (c_done == 1 and formal_last_cmd == NONE)
            //          or formal_last_cmd != 
            
            if((formal_last_cmd != `CACHE_CMD_NONE) && (c_done == 0)) begin
                assert(formal_last_cmd == req_cmd);
                assert(formal_last_req_address == req_address);
            end

            if(formal_last_cmd == `CACHE_CMD_NONE) begin
                assert(c_done == 0);
            end
        end

        
    end
`endif
*/

// Fetch starts
// Fetch ends, decode detects a CSR, sends d2f_ready to zero, Fetch sees stall, does not start new fetch
// Then decode stage passes aborting instruction to execute
// Execute send abort request to decode which means that decode passes abort to fetch
// Then Execute passes then branch taken request


always @* begin
    start_fetch = 0;
    dbg_cmd_ready = 0;
    dbg_pipeline_busy = 1;
    register_d2f_commands = 0;
    register_dbg_cmds = 0;

    
    req_cmd = `CACHE_CMD_NONE;
    req_address = pc;
    f2d_valid = 0;
    f2d_type = `F2E_TYPE_INSTR;
    f2d_instr = resp_read_data;
    f2d_pc = pc;
    f2d_status = resp_status;

    // Internal flip flops input signals
    // Active and active cmd is assigned above
    saved_read_data_nxt = saved_read_data;
    saved_status_nxt = saved_status;
    saved_read_data_valid_nxt = saved_read_data_valid;

    branched_nxt = branched;
    branched_target_nxt = branched_target;

    flushed_nxt = flushed;
    dbg_arg0_o = pc;

    if(branched)
        dbg_arg0_o =  branched_target;
    

    if(!rst_n) begin
        req_cmd = `CACHE_CMD_NONE;
        req_address = RESET_VECTOR; // This will be registered by PC
        // While PC will get overwritten on first cycle after reset
        // We still reset it just in case it's stuck in metastate or something
        saved_read_data_valid_nxt = 0;

        branched_target_nxt = RESET_VECTOR;
        branched_nxt = 1;
        flushed_nxt = 0;

        req_done_nxt = 0;

        // Pretend that we accepted a branch by setting branched
        // If branched is set and no instruction fetch is active
        // Then it will continue execution from branch_target, which is our reset_vector
    end else begin
        if(saved_read_data_valid) begin
            f2d_valid = !branched;
            f2d_instr = saved_read_data;
            f2d_status = saved_status;
        end else if(resp_valid && req_done && !saved_req_cmd_is_flush) begin
            f2d_valid = !branched;
            // In case branch was recved while fetching then don't raise valid and dont save fetched instruction
            // Instead start fetching next instruction

            f2d_instr = resp_read_data;
            f2d_type = `F2E_TYPE_INSTR;
            f2d_status = resp_status;
            
            // If d2f_ready then no need to stall fetching
            // Else still output the load data
            saved_read_data_valid_nxt = !d2f_ready && !branched; // We save it if it was stalled AND no branch
            saved_read_data_nxt = resp_read_data;
            saved_status_nxt = resp_status;
        end else if(req_done && !resp_valid) begin // Request is active but no response
            saved_read_data_valid_nxt = 0;
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
        


        start_fetch = 0;
        register_d2f_commands = 0;
        if(!saved_read_data_valid && !resp_valid && !req_done) begin
            // No data saved, no request active
            // Start new fetch
            start_fetch = 1;
            // No need to save requests, because they can be accepted without stall
        end else if(!saved_read_data_valid && !resp_valid && req_done) begin
            // No saved data, no response yet, but the request is accepted
            // Don't send more requests.
            start_fetch = 0;
            register_d2f_commands = 1;  // Register D2F commands, because it might be from deeper pipeline stage
        end else if(!saved_read_data_valid && resp_valid && d2f_ready) begin
            // No saved data, response recved, and pipeline accepted it
            // Start new fetch
            start_fetch = 1;
            // No need to register because D2F can be accepted
        end else if(saved_read_data_valid && !d2f_ready) begin
            // We have saved data but pipeline stalled us
            // No need to register D2F commands because d2f_ready is deasserted
            // Making D2F commands impossible
        end else if(saved_read_data_valid && d2f_ready) begin
            // We have saved data and pipeline accepted it
            start_fetch = 1;
        end


        if(start_fetch) begin
            // Then start fetching next instruction
            // If req_ready is asserted then apply the changes to registers
            saved_read_data_valid_nxt = 0;
            
            if(dbg_mode) begin
                // Dont start new fetch
                dbg_pipeline_busy = !req_done;
                register_d2f_commands = 1;
                register_dbg_cmds = 1;
                req_cmd = `CACHE_CMD_NONE;
            end else if(interrupt_pending) begin
                // Don't start new fetch, because interrupt pending
                req_cmd = `CACHE_CMD_NONE;
            end else if(flushing) begin
                // Issue flush
                // Flush is done first, because FLUSH requires instruction restart
                // Therefore we do the flush and then on next request we just do
                // pretend that branch happened
                
                req_cmd = `CACHE_CMD_FLUSH_ALL;
                if(req_ready)
                    flushed_nxt = 0;

                // However if the command was issued in the past,
                //      then the branched_nxt and bracned target don't need to be overwritten
                if(req_ready && d2f_ready && (d2f_cmd == `ARMLEOCPU_D2F_CMD_FLUSH)) begin
                    branched_nxt = 1;
                    branched_target_nxt = d2f_branchtarget;
                end else begin
                    register_d2f_commands = 1;
                end
            end else if(branching) begin
                req_cmd = `CACHE_CMD_EXECUTE;
                req_address = branching_target;
                if(req_ready)
                    branched_nxt = 0;
            end else begin
                // Can start new fetch at pc + 4
                req_cmd = `CACHE_CMD_EXECUTE;
                req_address = pc_plus_4;
                // The PC will be updated only in case of req_ready
            end
        end

        if(register_d2f_commands) begin
            if(d2f_ready && (d2f_cmd != `ARMLEOCPU_D2F_CMD_NONE)) begin
                if(d2f_cmd == `ARMLEOCPU_D2F_CMD_FLUSH) begin
                    flushed_nxt = 1;
                    branched_nxt = 1;
                    branched_target_nxt = d2f_branchtarget;
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
                    branched_target_nxt = dbg_arg0_i;
                    dbg_cmd_ready = 1;
                end else if(dbg_cmd == `DEBUG_CMD_READ_PC) begin
                    
                end else begin
                    dbg_cmd_ready = 1;
                end
            end
        end
    end
end

always @* begin
    // TODO: Check if this is correct
    // If req_ready and resp_valid then we don't need to raise req_done_nxt
    // 
    req_done_nxt = 0;
    
    if(req_valid && req_ready && !resp_valid) begin
        req_done_nxt = 1;
    end
end

`ifdef DEBUG_FETCH
always @(posedge clk) begin
    
end
`endif



endmodule


`include "armleocpu_undef.vh"
