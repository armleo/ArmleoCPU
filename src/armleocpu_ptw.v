/*`include "armleocpu_params.params.inc"
`ifdef armleocpu_TIMESCALE
`endif*/
`timescale 1ns/1ns
module armleocpu_ptw(
    input clk,
    input rst_n,

    
    output wire         m_transaction,
    output wire  [2:0]  m_cmd,
    output wire  [33:0] m_address,
    input [2:0]         m_transaction_response,
    input               m_transaction_done,
    input  [31:0]       m_rdata,
    
    input               resolve_request,
    output wire         resolve_ack,
    input [19:0]        virtual_address,

    output reg          resolve_done,
    output reg          resolve_pagefault,
    output reg          resolve_accessfault,

    output wire  [7:0]  resolve_access_bits,
    output wire  [21:0] resolve_physical_address,

    input               satp_mode,
    input [21:0]        satp_ppn
);

`include "armleocpu_accesstag_defs.inc"
`include "armleobus_defs.inc"

localparam STATE_IDLE = 1'b0;
localparam STATE_TABLE_WALKING = 1'b1;

localparam false = 1'b0;
localparam true = 1'b1;

reg state;
reg current_level;
reg [21:0] current_table_base;
reg [19:0] saved_virtual_address;

// local states

wire [9:0] virtual_address_vpn[1:0];
assign virtual_address_vpn[0] = saved_virtual_address[9:0];
assign virtual_address_vpn[1] = saved_virtual_address[19:10];

// PTE Decoding
wire pte_valid   = m_rdata[`ACCESSTAG_VALID_BIT_NUM];
wire pte_read    = m_rdata[`ACCESSTAG_READ_BIT_NUM];
wire pte_write   = m_rdata[`ACCESSTAG_WRITE_BIT_NUM];
wire pte_execute = m_rdata[`ACCESSTAG_EXECUTE_BIT_NUM];
/*verilator lint_off UNUSED*/
wire [11:0] pte_ppn0 = m_rdata[31:20];
/*verilator lint_off UNUSED*/
wire [9:0]  pte_ppn1 = m_rdata[19:10];

wire pte_invalid = !pte_valid || (!pte_read && pte_write);
wire pte_missaligned = current_level == 1 && pte_ppn1 != 0;
        // missaligned if current level is zero is impossible
wire pte_is_leaf = pte_read || pte_execute;
wire pte_pointer = m_rdata[3:0] == 4'b0001;

wire pma_error = (m_transaction_response != `ARMLEOBUS_RESPONSE_SUCCESS);

assign m_address = {current_table_base, virtual_address_vpn[current_level], 2'b00};
assign m_transaction = state == STATE_TABLE_WALKING;
assign m_cmd = `ARMLEOBUS_CMD_READ;

// Resolve resolved physical address
assign resolve_physical_address = {m_rdata[31:20],
    current_level ? saved_virtual_address[9:0] : m_rdata[19:10]
};
// resolved access bits
assign resolve_access_bits = m_rdata[7:0];
// resolve request was accepted
assign resolve_ack = state == STATE_IDLE;

`ifdef DEBUG_PTW
task debug_write_all; begin
    debug_write_request();
    debug_write_state();
    debug_write_pte();
end endtask

task debug_write_request; begin
    $display("[%d] [PTW]\tRequested virtual address = 0x%H", $time, {saved_virtual_address, 12'hXXX});
end endtask

task debug_write_state; begin
    $display("[%d] [PTW]\tstate = %s, current_level = %s, current_table_base = 0x%X",
            $time,
            state == 1 ? "IDLE" : "TABLE_WALKING",
            current_level ? "megapage": "page",
            {current_table_base, 12'hXXX});
end endtask

task debug_write_pte; begin
    $display("[%d] [PTW]\tPTE value = 0x%X, avl_response = %s, m_address = 0x%X", $time, m_rdata, m_transaction_response == `ARMLEOBUS_RESPONSE_SUCCESS ? "VALID": "ERROR", m_address);
    $display("[%d] [PTW]\tvalid? = %s, access_bits = %s%s%s\t", $time, pte_valid ? "VALID" : "INVALID", (pte_read ? "r" : " "), (pte_write ? "w" : " "), (pte_execute ? "x" : " "));
    $display("[%d] [PTW]\tpte_ppn0 = 0x%X, pte_ppn1 = 0x%X", $time, pte_ppn0, pte_ppn1);
    if(pma_error) begin
                                $display("[%d] [PTW]\tPMA_Error", $time);
    end else if(pte_invalid) begin
                                $display("[%d] [PTW]\tPTE_Invalid", $time);
    end else if(pte_is_leaf) begin
        if(!pte_missaligned)    $display("[%d] [PTW]\tAligned page", $time);
        else                    $display("[%d] [PTW]\tMissaligned megapage", $time);
    end else if(pte_pointer) begin
        if(current_level)       $display("[%d] [PTW]\tGoing deeper", $time);
        else                    $display("[%d] [PTW]\tPage leaf expected, insted pointer found", $time);
    end
end endtask
`endif



always @* begin
    resolve_done = false;
    resolve_pagefault = false;
    resolve_accessfault = false;
    case(state)
        STATE_IDLE: begin

        end
        STATE_TABLE_WALKING: begin
            if(m_transaction_done) begin
                if(pma_error) begin
                    resolve_accessfault = true;
                    resolve_done = true;
                end else if(pte_invalid) begin
                    resolve_pagefault = true;
                    resolve_done = true;
                end else if(pte_is_leaf) begin
                    if(pte_missaligned) begin
                        resolve_pagefault = true;
                        resolve_done = true;
                    end else if(!pte_missaligned) begin
                        resolve_done = true;
                    end
                end else if(pte_pointer) begin
                    if(current_level == 1'b0) begin
                        resolve_pagefault = true;
                        resolve_done = true;
                    end
                    //else if(current_level == 1'b1) begin end;  
                end
            end
        end
    endcase
end

always @(posedge clk) begin
    if(!rst_n) begin
        state <= STATE_IDLE;
    end else if(clk) begin
        case(state)
            STATE_IDLE: begin
                current_level <= 1'b1;
                saved_virtual_address <= virtual_address;
                current_table_base <= satp_ppn;
                if(resolve_request) begin
                    state <= STATE_TABLE_WALKING;
                    `ifdef DEBUG_PTW
                    $display("[%d] [PTW] Page table walk request for address = 0x%X, w/ satp_mode = %b", $time, {virtual_address, 12'hXXX}, satp_mode);
                    `endif
                end
            end
            STATE_TABLE_WALKING: begin
                if(m_transaction_done) begin
                    if(pma_error) begin
                        state <= STATE_IDLE;
                        `ifdef DEBUG_PTW
                        $display("[%d] [PTW] Request failed because of PMA", $time);
                        debug_write_all();
                        `endif
                    end else if(pte_invalid) begin
                        state <= STATE_IDLE;
                        `ifdef DEBUG_PTW
                        $display("[%d] [PTW] Request failed because PTE", $time);
                        debug_write_all();
                        `endif
                    end else if(pte_is_leaf) begin
                        state <= STATE_IDLE;
                        if(pte_missaligned) begin
                            `ifdef DEBUG_PTW
                            $display("[%d] [PTW] Request failed because PTE is missalligned", $time);
                            debug_write_all();
                            `endif
                        end else if(!pte_missaligned) begin
                            `ifdef DEBUG_PTW
                            $display("[%d] [PTW] Request successful completed", $time);
                            debug_write_all();
                            `endif
                        end
                    end else if(pte_pointer) begin
                        if(current_level == 1'b0) begin
                            state <= STATE_IDLE;
                            `ifdef DEBUG_PTW
                            $display("[%d] [PTW] Resolve pagefault", $time);
                            debug_write_all();
                            `endif
                        end else if(current_level == 1'b1) begin
                            current_level <= 1'b0;
                            current_table_base <= m_rdata[31:10];
                            `ifdef DEBUG_PTW
                            $display("[%d] [PTW] Resolve going to next level", $time);
                            debug_write_all();
                            `endif
                        end
                    end
                end
            end
        endcase
    end
end



endmodule