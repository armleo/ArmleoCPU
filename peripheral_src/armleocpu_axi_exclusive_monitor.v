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
// 
// Filename:    armleocpu_axi_exclusive_monitor.v
// Project:	ArmleoCPU
//
// Purpose:	AXI4 exclusive monitor, to be put between upstream (host AXI4)
//      and downstream (client AXI4) and implements locking and
//      response converstion
//
//  
////////////////////////////////////////////////////////////////////////////////
`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_axi_exclusive_monitor(
    clk,
    rst_n,

    cpu_axi_awvalid, cpu_axi_awready, cpu_axi_awaddr, cpu_axi_awlen, cpu_axi_awburst, cpu_axi_awsize, cpu_axi_awid, cpu_axi_awlock,
    cpu_axi_wvalid, cpu_axi_wready, cpu_axi_wdata, cpu_axi_wstrb, cpu_axi_wlast,
    cpu_axi_bvalid, cpu_axi_bready, cpu_axi_bresp, cpu_axi_bid,
    
    cpu_axi_arvalid, cpu_axi_arready, cpu_axi_araddr, cpu_axi_arlen, cpu_axi_arsize, cpu_axi_arburst, cpu_axi_arid, cpu_axi_arlock,
    cpu_axi_rvalid, cpu_axi_rready, cpu_axi_rresp, cpu_axi_rlast, cpu_axi_rdata, cpu_axi_rid,
    
    memory_axi_awvalid, memory_axi_awready, memory_axi_awaddr, memory_axi_awlen, memory_axi_awburst, memory_axi_awsize, memory_axi_awid,
    memory_axi_wvalid, memory_axi_wready, memory_axi_wdata, memory_axi_wstrb, memory_axi_wlast,
    memory_axi_bvalid, memory_axi_bready, memory_axi_bresp, memory_axi_bid,
    
    memory_axi_arvalid, memory_axi_arready, memory_axi_araddr, memory_axi_arlen, memory_axi_arsize, memory_axi_arburst, memory_axi_arid,
    memory_axi_rvalid, memory_axi_rready, memory_axi_rresp, memory_axi_rlast, memory_axi_rdata, memory_axi_rid
    );


parameter ADDR_WIDTH = 32; // Determines the size of addr bus. If memory outside this peripheral is accessed BRESP/RRESP is set to DECERR
parameter ID_WIDTH = 4;
localparam SIZE_WIDTH = 3;
parameter DATA_WIDTH = 32; // 32 or 64
localparam DATA_STROBES = DATA_WIDTH / 8;

    input wire          clk;
    input wire          rst_n;

    // client port, connects to cpu's host port
    input wire          cpu_axi_awvalid;
    output reg          cpu_axi_awready;
    input wire  [ID_WIDTH-1:0]
                        cpu_axi_awid;
    input wire  [ADDR_WIDTH-1:0]
                        cpu_axi_awaddr;
    input wire  [7:0]   cpu_axi_awlen;
    input wire  [SIZE_WIDTH-1:0]
                        cpu_axi_awsize;
    input wire  [1:0]   cpu_axi_awburst;
    input wire          cpu_axi_awlock;
    

    // AXI W Bus
    input wire          cpu_axi_wvalid;
    output reg          cpu_axi_wready;
    input wire  [DATA_WIDTH-1:0]
                        cpu_axi_wdata;
    input wire  [DATA_STROBES-1:0]
                        cpu_axi_wstrb;
    input wire          cpu_axi_wlast;
    
    // AXI B Bus
    output reg          cpu_axi_bvalid;
    input wire          cpu_axi_bready;
    output reg [1:0]    cpu_axi_bresp;
    output reg [ID_WIDTH-1:0]
                        cpu_axi_bid;
    
    
    input wire          cpu_axi_arvalid;
    output reg          cpu_axi_arready;
    input wire  [ID_WIDTH-1:0]
                        cpu_axi_arid;
    input wire  [ADDR_WIDTH-1:0]
                        cpu_axi_araddr;
    input wire  [7:0]   cpu_axi_arlen;
    input wire  [SIZE_WIDTH-1:0]
                        cpu_axi_arsize;
    input wire  [1:0]   cpu_axi_arburst;
    input wire          cpu_axi_arlock;
    
    

    output reg          cpu_axi_rvalid;
    input wire          cpu_axi_rready;
    output reg  [1:0]   cpu_axi_rresp;
    output reg          cpu_axi_rlast;
    output reg  [DATA_WIDTH-1:0]
                        cpu_axi_rdata;
    output reg [ID_WIDTH-1:0]
                        cpu_axi_rid;
    

    // Host port, connectes to memory or peripheral device
    output reg          memory_axi_awvalid;
    input wire          memory_axi_awready;
    output reg  [ID_WIDTH-1:0]
                        memory_axi_awid;
    output reg  [ADDR_WIDTH-1:0]
                        memory_axi_awaddr;
    output reg  [7:0]   memory_axi_awlen;
    output reg  [SIZE_WIDTH-1:0]
                        memory_axi_awsize;
    output reg  [1:0]   memory_axi_awburst;
    

    // AXI W Bus
    output reg          memory_axi_wvalid;
    input wire          memory_axi_wready;
    output reg  [DATA_WIDTH-1:0]
                        memory_axi_wdata;
    output reg  [DATA_STROBES-1:0]
                        memory_axi_wstrb;
    output reg          memory_axi_wlast;
    
    // AXI B Bus
    input wire          memory_axi_bvalid;
    output reg          memory_axi_bready;
    input wire [1:0]    memory_axi_bresp;
    input wire [ID_WIDTH-1:0]
                        memory_axi_bid;
    
    
    output reg          memory_axi_arvalid;
    input wire          memory_axi_arready;
    output reg  [ID_WIDTH-1:0]
                        memory_axi_arid;
    output reg  [ADDR_WIDTH-1:0]
                        memory_axi_araddr;
    output reg  [7:0]   memory_axi_arlen;
    output reg  [SIZE_WIDTH-1:0]
                        memory_axi_arsize;
    output reg  [1:0]   memory_axi_arburst;
    
    

    input wire          memory_axi_rvalid;
    output reg          memory_axi_rready;
    input wire  [1:0]   memory_axi_rresp;
    input wire          memory_axi_rlast;
    input wire  [DATA_WIDTH-1:0]
                        memory_axi_rdata;
    input wire [ID_WIDTH-1:0]
                        memory_axi_rid;


`ifdef DEBUG_EXCLUSIVE_MONITOR
`include "assert.vh"
`endif

localparam STATE_IDLE = 0;
localparam STATE_READ = 1;
localparam STATE_WRITE = 2;

`DEFINE_REG_REG_NXT(4, state, state_nxt, clk)
`DEFINE_REG_REG_NXT(1, ar_done, ar_done_nxt, clk)
`DEFINE_REG_REG_NXT(1, aw_done, aw_done_nxt, clk)
`DEFINE_REG_REG_NXT(1, aw_processed, aw_processed_nxt, clk)
`DEFINE_REG_REG_NXT(1, w_done, w_done_nxt, clk)

`DEFINE_REG_REG_NXT(1, current_transaction_atomic_error, current_transaction_atomic_error_nxt, clk)
`DEFINE_REG_REG_NXT(1, current_transaction_is_locking, current_transaction_is_locking_nxt, clk)
`DEFINE_REG_REG_NXT(ADDR_WIDTH, current_transaction_addr, current_transaction_addr_nxt, clk)


`DEFINE_REG_REG_NXT(1, atomic_lock_valid, atomic_lock_valid_nxt, clk)
`DEFINE_REG_REG_NXT(ADDR_WIDTH, atomic_lock_addr, atomic_lock_addr_nxt, clk)


always @* begin
    `ifdef SIMULATION
    #1
    // This is required because of infinite loop
    // When simulations is running
    // This is caused by changing of values between multiple combinational alwayses
    `endif
    state_nxt = state;
    ar_done_nxt = ar_done;
    aw_done_nxt = aw_done;
    aw_processed_nxt = aw_processed;
    w_done_nxt = w_done;

    current_transaction_atomic_error_nxt = current_transaction_atomic_error;
    current_transaction_is_locking_nxt = current_transaction_is_locking;
    current_transaction_addr_nxt = current_transaction_addr;

    atomic_lock_addr_nxt = atomic_lock_addr;
    atomic_lock_valid_nxt = atomic_lock_valid;

    memory_axi_awvalid = 0;
    cpu_axi_awready = 0;

    memory_axi_awid = cpu_axi_awid;
    memory_axi_awaddr = cpu_axi_awaddr;
    memory_axi_awlen = cpu_axi_awlen;
    memory_axi_awsize = cpu_axi_awsize;
    memory_axi_awburst = cpu_axi_awburst;


    memory_axi_wvalid = 0;
    cpu_axi_wready = 0;

    memory_axi_wdata = cpu_axi_wdata;
    memory_axi_wstrb = cpu_axi_wstrb; // Overwritten with zero when locking write is not EXOKAY
    memory_axi_wlast = cpu_axi_wlast; // Assumed to be always one


    cpu_axi_bvalid = 0;
    memory_axi_bready = 0;

    cpu_axi_bresp = 0;
    cpu_axi_bid = memory_axi_bid;




    memory_axi_arvalid = 0;
    cpu_axi_arready = 0;

    memory_axi_arid = cpu_axi_arid;
    memory_axi_araddr = cpu_axi_araddr;
    memory_axi_arlen = cpu_axi_arlen;
    memory_axi_arsize = cpu_axi_arsize;
    memory_axi_arburst = cpu_axi_arburst;


    cpu_axi_rvalid = 0;
    memory_axi_rready = 0;

    cpu_axi_rresp = memory_axi_rresp;
    cpu_axi_rlast = memory_axi_rlast;
    cpu_axi_rdata = memory_axi_rdata;
    cpu_axi_rid = memory_axi_rid;

    if(!rst_n) begin
        state_nxt = STATE_IDLE;
        atomic_lock_addr_nxt = 0;
        atomic_lock_valid_nxt = 0;

        ar_done_nxt = 0;
        aw_done_nxt = 0;
        w_done_nxt = 0;


    end else begin
        // TODO: Maybe? Change priority to write first. Should not matter anyway
        if(state == STATE_READ || (cpu_axi_arvalid && (state == STATE_IDLE))) begin
            state_nxt = STATE_READ;
            
            if(!ar_done) begin
                cpu_axi_arready = memory_axi_arready;

                if(cpu_axi_arvalid) begin
                    current_transaction_is_locking_nxt = cpu_axi_arlock;
                    current_transaction_addr_nxt = cpu_axi_araddr;

                    if(current_transaction_is_locking_nxt) begin
                        current_transaction_atomic_error_nxt = 0;
                        atomic_lock_valid_nxt = 1;
                        atomic_lock_addr_nxt = cpu_axi_araddr;
                    end
                    
                    memory_axi_arvalid = 1;
                    
                    if(memory_axi_arready)
                        ar_done_nxt = 1;
                    // TODO: For atomic:
                    // TODO: Assert its only ARSIZE = BUS WIDTH or BUS_WIDTH/2 for 64 bit
                    // TODO: Assert its only ARLEN = 0
                    // TODO: MAYBE: Assert its only BURST = INCR
                    // TODO: For everything else values does not matter
                end
            end

            // ar done is reset only when rvalid && rready && rlast
            if(ar_done && memory_axi_rvalid) begin
                
                cpu_axi_rvalid = 1;

                memory_axi_rready = cpu_axi_rready;

                if(memory_axi_rresp == `AXI_RESP_OKAY) begin
                    if(current_transaction_is_locking)
                        cpu_axi_rresp = `AXI_RESP_EXOKAY;
                    else
                        cpu_axi_rresp = `AXI_RESP_OKAY;
                end else begin
                    cpu_axi_rresp = memory_axi_rresp;
                end
                if(cpu_axi_rready && cpu_axi_rlast) begin
                    // Reset everything to start values for next transaction
                    state_nxt = STATE_IDLE;
                    ar_done_nxt = 0;
                end
            end


            // Pass by the AR request
            // Pass the R response and on last return to idle
            // Only non-burst atomic transactions are supported, so all transactions that is atomic is last
            // If locking atomic_lock_valid_nxt = 1; atomic_lock_addr_nxt = araddr;
        end else if(state == STATE_WRITE || (cpu_axi_awvalid && (state == STATE_IDLE))) begin
            state_nxt = STATE_WRITE;
            
            // aw_processed is set when first cycle of AW is done
            // This is intentional, because otherwise we didn't know if transactions
            // is EXOKAY or OKAY and we need this information to mask WSTRB
            // TODO: Make assertions. Locking transaction cant be burst for this peripheral
            if(!aw_done && cpu_axi_awvalid) begin
                
                memory_axi_awvalid = 1;
                current_transaction_is_locking_nxt = cpu_axi_awlock;
                current_transaction_addr_nxt = cpu_axi_awaddr;

                if(current_transaction_is_locking_nxt) begin
                    if(atomic_lock_valid) begin
                        if(atomic_lock_addr_nxt == current_transaction_addr_nxt) begin
                            current_transaction_atomic_error_nxt = 0;
                            atomic_lock_valid_nxt = 0;
                        end else begin
                            // Atomic lock is otherwritten Return just OKAY, dont write
                            current_transaction_atomic_error_nxt = 1;
                        end
                    end else begin
                        // No atomic lock, Return just OKAY, dont write
                        current_transaction_atomic_error_nxt = 1;
                    end
                end else begin
                    // Return OKAY
                    if(atomic_lock_valid && atomic_lock_addr == current_transaction_addr_nxt) begin
                        atomic_lock_valid_nxt = 0;
                    end
                    current_transaction_atomic_error_nxt = 0;
                end
                memory_axi_awvalid = 1;
                cpu_axi_awready = memory_axi_awready;
                aw_processed_nxt = 1;
                if(cpu_axi_awready) begin
                    aw_done_nxt = 1;
                end
            end
            
            if(!w_done && aw_processed) begin
                // TODO: Assert write is last
                memory_axi_wvalid = cpu_axi_wvalid;
                cpu_axi_wready = memory_axi_wready;
                if(current_transaction_is_locking && current_transaction_atomic_error) begin
                    memory_axi_wstrb = 0;
                end
                if(cpu_axi_wvalid && cpu_axi_wready && cpu_axi_wlast) begin
                    w_done_nxt = 1;
                end
            end

            if(w_done && aw_done && memory_axi_bvalid) begin
                cpu_axi_bvalid = 1;
                if(current_transaction_is_locking) begin
                    if(memory_axi_bresp == `AXI_RESP_OKAY) begin
                        if(current_transaction_atomic_error) begin
                            cpu_axi_bresp = memory_axi_bresp;
                        end else begin
                            cpu_axi_bresp = `AXI_RESP_EXOKAY;
                        end
                    end else begin
                        cpu_axi_bresp = memory_axi_bresp;
                    end
                end else begin
                    cpu_axi_bresp = memory_axi_bresp;
                end
                if(cpu_axi_bready) begin
                    memory_axi_bready = 1;
                    state_nxt = STATE_IDLE;
                    w_done_nxt = 0;
                    aw_done_nxt = 0;
                    aw_processed_nxt = 0;
                    current_transaction_atomic_error_nxt = 0;
                    current_transaction_is_locking_nxt = 0;
                end
            end
            // If not locking, dont mask anything
            //  -> if write to reserved address, OKAY and invalidate reservation
            //  -> else OKAY nothing
            // else if locking
            //  -> if to reserved address, EXOKAY and invalidate reservation, DO the write
            //  -> if not to reserved address, OKAY and mask the written data
            // pass by the AW and W until last one
            // Only non-burst atomic transactions are supported
            
        end
    end
end

/*
`ifdef AXI_EXCUSIVE_MONITOR
always @(posedge clk) begin

end
`endif
*/
endmodule

`include "armleocpu_undef.vh"

    