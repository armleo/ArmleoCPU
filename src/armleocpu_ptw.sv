module armleocpu_ptw(
    input clk,
    input async_rst_n,

    output logic [31:0] avl_address,
    output logic        avl_read,
    input  [31:0]       avl_readdata,
    input               avl_readdatavalid,
    input               avl_waitrequest,
    input [1:0]         avl_response,
    //                  avl_burstcount = 1
    //                  avl_write = 0
    //                  avl_writedata = 32'hXXXX_XXXX


    input               resolve_request,
    output logic        resolve_ack,
    input [21:0]        virtual_address,

    output logic        resolve_done,
    output logic        resolve_pagefault,
    output logic        resolve_accessfault,

    output logic [7:0]  resolve_access_bits,
    output logic [21:0] resolve_physical_address,

    input               matp_mode,
    input [21:0]        matp_ppn

    output logic [20+12+2-1:0] state_debug_output;
);

reg [21:0] current_table_base;
reg current_level;

localparam STATE_IDLE = 1'b0;
localparam STATE_TABLE_WALKING = 1'b1;

reg state;
reg read_issued;
reg [19:0] saved_virtual_address;
reg [11:0] saved_offset;

assign state_debug_output = {saved_virtual_address, read_issued, state}

wire [9:0] virtual_address_vpn[1:0];
assign virtual_address_vpn[0] = saved_virtual_address[9:0];
assign virtual_address_vpn[1] = saved_virtual_address[19:10];

wire pte_valid   = avl_readdata[0];
wire pte_read    = avl_readdata[1];
wire pte_write   = avl_readdata[2];
wire pte_execute = avl_readdata[3];

wire [11:0] pte_ppn0 = avl_readdata[31:20];
wire [9:0]  pte_ppn1 = avl_readdata[19:10];

wire pte_invalid = !pte_valid || (!pte_read && pte_write);
wire pte_missaligned = current_level == 1 && pte_ppn1 == 0;
        // missaligned if current level is zero is impossible
wire pte_is_leaf = pte_read || pte_execute;
wire pte_pointer = avl_readdata[3:0] == 4'h0;

wire pma_error = (avl_response != 2'b00);

assign avl_address = {current_table_base, virtual_address_vpn[current_level], 2'b00};
assign avl_read = !read_issued && state == STATE_TABLE_WALKING;

assign resolve_physical_address = {avl_readdata[31:20],
    current_level ? saved_virtual_address[9:0] : avl_readdata[19:10]
};
assign resolve_accessbits = avl_readdata[7:0];
assign resolve_ack = state == STATE_IDLE;

always_comb begin
    case(state)
        STATE_IDLE: begin

        end
        STATE_TABLE_WALKING: begin

        end
    endcase
end



endmodule