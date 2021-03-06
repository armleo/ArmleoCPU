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
////////////////////////////////////////////////////////////////////////////////



// Note: Any modification of below should also be changed in undef
// Note: If in code any assumptions are made about underlying values
//      Then it should be mentioned below

`ifndef TIMESCALE_DEFINE
`define TIMESCALE_DEFINE `timescale 1ns/1ns
`endif

`default_nettype none

`define ACCESS_PACKED(idx, len) len*idx +: len


// Underlying values are taken from RISC-V specification, don't change
// ARMLEOCPU_PAGE_METADATA
`define ARMLEOCPU_PAGE_METADATA_W (8)

`define ARMLEOCPU_PAGE_METADATA_VALID_BIT_NUM (0)
`define ARMLEOCPU_PAGE_METADATA_READ_BIT_NUM (1)
`define ARMLEOCPU_PAGE_METADATA_WRITE_BIT_NUM (2)
`define ARMLEOCPU_PAGE_METADATA_EXECUTE_BIT_NUM (3)
`define ARMLEOCPU_PAGE_METADATA_USER_BIT_NUM (4)
`define ARMLEOCPU_PAGE_METADATA_ACCESS_BIT_NUM (6)
`define ARMLEOCPU_PAGE_METADATA_DIRTY_BIT_NUM (7)

// Randomly selected, SUCCESS should always be zero
// CACHE
`define CACHE_RESPONSE_SUCCESS (4'd0)
`define CACHE_RESPONSE_ACCESSFAULT (4'd1)
`define CACHE_RESPONSE_PAGEFAULT (4'd2)
`define CACHE_RESPONSE_MISSALIGNED (4'd3)
`define CACHE_RESPONSE_UNKNOWNTYPE (4'd4)
`define CACHE_RESPONSE_ATOMIC_FAIL (4'd5)


// Randomly selected, can be changed. NONE should always be zero
// More optimization of values is possible but it's skipped right now
`define CACHE_CMD_NONE (4'd0)
`define CACHE_CMD_EXECUTE (4'd1)
`define CACHE_CMD_LOAD (4'd2)
`define CACHE_CMD_STORE (4'd3)
`define CACHE_CMD_FLUSH_ALL (4'd4)
`define CACHE_CMD_LOAD_RESERVE (4'd5)
`define CACHE_CMD_STORE_CONDITIONAL (4'd6)


// Taken from AXI4 Specification
// AXI Defs
`define AXI_BURST_INCR (2'b01)
`define AXI_BURST_WRAP (2'b10)

`define AXI_RESP_OKAY   (2'b00)
`define AXI_RESP_EXOKAY (2'b01)
`define AXI_RESP_SLVERR (2'b10)
`define AXI_RESP_DECERR (2'b11)



`define CONNECT_AXI_BUS(left, right) \
.``left``awvalid(``right``awvalid), \
.``left``awready(``right``awready), \
.``left``awaddr(``right``awaddr), \
.``left``awlen(``right``awlen), \
.``left``awsize(``right``awsize), \
.``left``awburst(``right``awburst), \
.``left``awid(``right``awid), \
\
.``left``wvalid(``right``wvalid), \
.``left``wready(``right``wready), \
.``left``wdata(``right``wdata), \
.``left``wstrb(``right``wstrb), \
.``left``wlast(``right``wlast), \
\
.``left``bvalid(``right``bvalid), \
.``left``bready(``right``bready), \
.``left``bresp(``right``bresp), \
.``left``bid(``right``bid), \
\
.``left``arvalid(``right``arvalid), \
.``left``arready(``right``arready), \
.``left``araddr(``right``araddr), \
.``left``arlen(``right``arlen), \
.``left``arsize(``right``arsize), \
.``left``arburst(``right``arburst), \
.``left``arid(``right``arid), \
\
.``left``rvalid(``right``rvalid), \
.``left``rready(``right``rready), \
.``left``rresp(``right``rresp), \
.``left``rdata(``right``rdata), \
.``left``rid(``right``rid), \
.``left``rlast(``right``rlast) \



// CSR CMDs
// Randomly selected? TODO: Check with execute module

`define ARMLEOCPU_CSR_CMD_NONE (4'd0)
`define ARMLEOCPU_CSR_CMD_READ (4'd1)
`define ARMLEOCPU_CSR_CMD_WRITE (4'd2)
`define ARMLEOCPU_CSR_CMD_READ_WRITE (4'd3)
`define ARMLEOCPU_CSR_CMD_READ_SET (4'd4)
`define ARMLEOCPU_CSR_CMD_READ_CLEAR (4'd5)
`define ARMLEOCPU_CSR_CMD_MRET (4'd6)
`define ARMLEOCPU_CSR_CMD_SRET (4'd7)
`define ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN (4'd8)

// Randomly selected
// F2E 
`define F2E_TYPE_WIDTH 2
`define F2E_TYPE_INSTR 0
`define F2E_TYPE_INTERRUPT_PENDING 1


// Randomly selected
// D2F CMDs
`define ARMLEOCPU_D2F_CMD_WIDTH 2
`define ARMLEOCPU_D2F_CMD_FLUSH (2'h2)
`define ARMLEOCPU_D2F_CMD_START_BRANCH (2'h1)
`define ARMLEOCPU_D2F_CMD_NONE (2'h0)

// None is no operation
// Start branch causes branch start (pc change)
// Flush causes fetch unit to issue FLUSH command to Cache





// Debug command interface defintions
// NONE is assumed zero
`define DEBUG_CMD_WIDTH (2)
`define DEBUG_CMD_NONE (4'd0)
// Reserved command NONE

// handled by debug unit:
`define DEBUG_CMD_IFLUSH (4'd1)


// Handled by fetch unit:
`define DEBUG_CMD_JUMP (4'd2)

// Handled by



// Below values are taken from RISC-V specification and shouldn't be modified
// Exceptions and interrupts
`define EXCEPTION_CODE_INTERRUPT (32'h8000_0000)
`define INTERRUPT_CODE_SOFTWATE_INTERRUPT (3)
`define INTERRUPT_CODE_TIMER_INTERRUPT (7)
`define INTERRUPT_CODE_EXTERNAL_INTERRUPT (11)

`define EXCEPTION_CODE_SOFTWATE_INTERRUPT (`INTERRUPT_CODE_SOFTWATE_INTERRUPT | `EXCEPTION_CODE_INTERRUPT)
`define EXCEPTION_CODE_TIMER_INTERRUPT (`INTERRUPT_CODE_TIMER_INTERRUPT | `EXCEPTION_CODE_INTERRUPT)
`define EXCEPTION_CODE_EXTERNAL_INTERRUPT (`INTERRUPT_CODE_EXTERNAL_INTERRUPT | `EXCEPTION_CODE_INTERRUPT)


// Below values are taken from RISC-V specification and shouldn't be modified
`define EXCEPTION_CODE_INSTRUCTION_ADDRESS_MISSALIGNED (0)
`define EXCEPTION_CODE_INSTRUCTION_ACCESS_FAULT (1)
`define EXCEPTION_CODE_ILLEGAL_INSTRUCTION (2)
`define EXCEPTION_CODE_BREAKPOINT (3)
`define EXCEPTION_CODE_LOAD_ADDRESS_MISALIGNED (4)
`define EXCEPTION_CODE_LOAD_ACCESS_FAULT (5)
`define EXCEPTION_CODE_STORE_ADDRESS_MISALIGNED (6)
`define EXCEPTION_CODE_STORE_ACCESS_FAULT (7)

// Below values are taken from RISC-V specification and shouldn't be modified
// Calls from x privilege
`define EXCEPTION_CODE_UCALL (8)
`define EXCEPTION_CODE_SCALL (9)
`define EXCEPTION_CODE_MCALL (11)
`define EXCEPTION_CODE_INSTRUCTION_PAGE_FAULT (12)
`define EXCEPTION_CODE_LOAD_PAGE_FAULT (13)
`define EXCEPTION_CODE_STORE_PAGE_FAULT (15)


// Below values are taken from RISC-V specification and shouldn't be modified
// INSTRs
`define INSTRUCTION_NOP ({12'h0, 5'h0, 3'b000, 5'h0, 7'b00_100_11})


// Below values are taken from RISC-V specification and shouldn't be modified
`define OPCODE_LUI (7'b0110111)
`define OPCODE_AUIPC (7'b0010111)
`define OPCODE_JAL (7'b1101111)
`define OPCODE_JALR (7'b1100111)
`define OPCODE_BRANCH (7'b1100011)
`define OPCODE_LOAD (7'b0000011)
`define OPCODE_STORE (7'b0100011)
`define OPCODE_OP_IMM (7'b0010011)
`define OPCODE_OP (7'b0110011)
`define OPCODE_FENCE (7'b0001111)
`define OPCODE_SYSTEM (7'b1110011)

// Privileges
// Below values are taken from RISC-V specification and shouldn't be modified
// _SV suffix means it's one bit. Used in SSTATUS concats, don't change width
`define ARMLEOCPU_PRIVILEGE_USER (2'b00)
`define ARMLEOCPU_PRIVILEGE_USER_SV (1'b0)
`define ARMLEOCPU_PRIVILEGE_SUPERVISOR (2'b01)
`define ARMLEOCPU_PRIVILEGE_SUPERVISOR_SV (1'b1)
`define ARMLEOCPU_PRIVILEGE_MACHINE (2'b11)



// TLB CMDs
`define TLB_CMD_NONE (2'b00)
`define TLB_CMD_RESOLVE (2'b01)
`define TLB_CMD_NEW_ENTRY (2'b10)
`define TLB_CMD_INVALIDATE_ALL (2'b11)


// Below values are taken from RISC-V specification and shouldn't be modified
// LD_TYOE
`define LOAD_BYTE (3'b000)
`define LOAD_BYTE_UNSIGNED (3'b100)

`define LOAD_HALF (3'b001)
`define LOAD_HALF_UNSIGNED (3'b101)

`define LOAD_WORD (3'b010)

// Below values are taken from RISC-V specification and shouldn't be modified
// ST_TYPE
`define STORE_BYTE (2'b00)
`define STORE_HALF (2'b01)
`define STORE_WORD (2'b10)



// Below are macro definitions commonly used inside core
// Also any DEFINEs should be defined here and undefined in armleocpu_undef.vh
// To not clutter DEFINE space for other modules
// Some synthesis/simulator tools keep definitions between files some don't
// It is assumed that they do, because for cases that they don't it does not matter


`define DEFINE_REG_REG_NXT(WIDTH, REG, REG_NXT, CLK) \
    reg [WIDTH-1:0] REG; \
    reg [WIDTH-1:0] REG_NXT; \
    always @(posedge CLK) REG <= REG_NXT; \



// Define a cur flip flop and ``cur``_nxt signals with default value on reset
// Assumes: clk is clk
// rst_n is negative clocked reset

`define DEFINE_CSR_REG(bit_count, cur, default_val) \
reg [bit_count-1:0] cur; \
reg [bit_count-1:0] ``cur``_nxt; \
always @(posedge clk) \
    if(!rst_n) \
        cur <= default_val; \
    else \
        cur <= ``cur``_nxt;

// Define a ``cur``_nxt signals and use flip flop
// named cur with default value on reset
// Assumes: clk is clk
// rst_n is negative clocked reset

`define DEFINE_CSR_OREG(bit_count, cur, default_val) \
reg [bit_count-1:0] ``cur``_nxt; \
always @(posedge clk) \
    if(!rst_n) \
        cur <= default_val; \
    else \
        cur <= ``cur``_nxt;

// Assumes that suffix _nxt is used
// Just a shorthand. Why not just type it?
// Because commonly developers accidently assign value
// To cur instead of cur_nxt

`define INIT_COMB_DEFAULT(cur) ``cur``_nxt = ``cur``;

`define DEFINE_CSR_COMB_RO(address, val) \
        address: begin \
            csr_exists = 1; \
            csr_to_rd = val; \
            rmw_before = csr_to_rd; \
        end

`define DEFINE_SCRATCH_CSR_REG_COMB(address, cur) \
        address: begin \
            csr_exists = 1; \
            csr_to_rd = cur; \
            rmw_before = csr_to_rd; \
            if((!csr_invalid) && csr_write) \
                ``cur``_nxt = rmw_after; \
        end

// Be ignored or written anyway?
`define DEFINE_ADDRESS_CSR_REG_COMB(address, cur) \
        address: begin \
            csr_exists = 1; \
            csr_to_rd = cur; \
            rmw_before = csr_to_rd; \
            if((!csr_invalid) && csr_write) \
                ``cur``_nxt = rmw_after; \
            ``cur``_nxt[1:0] = 2'b00;\
        end



// Chip2Chip definitions. Should not change because they are going to be taped out
`define CHIP2CHIP_OPCODE_NONE (8'd0)
`define CHIP2CHIP_OPCODE_READY (8'd1)
`define CHIP2CHIP_OPCODE_WRITE (8'd2)


