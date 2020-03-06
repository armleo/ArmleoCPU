module armleocpu_ptw(
    input clk,
    input async_rst_n,

    output logic [33:0] avl_address,
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
    input [19:0]        virtual_address,

    output logic        resolve_done,
    output logic        resolve_pagefault,
    output logic        resolve_accessfault,

    output logic [7:0]  resolve_access_bits,
    output logic [21:0] resolve_physical_address,

    input               matp_mode,
    input [21:0]        matp_ppn

    `ifdef DEBUG
    , output wire [24:0] state_debug_output
    `endif
);

reg [21:0] current_table_base;
reg current_level;

localparam STATE_IDLE = 1'b0;
localparam STATE_TABLE_WALKING = 1'b1;

localparam false = 1'b0;
localparam true = 1'b1;

reg state;
reg read_issued;
reg [19:0] saved_virtual_address;
`ifdef DEBUG
assign state_debug_output = {current_table_base, current_level, read_issued, state};
`endif
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

always @* begin
    resolve_done = false;
    resolve_pagefault = false;
    resolve_accessfault = false;
    case(state)
        STATE_IDLE: begin

        end
        STATE_TABLE_WALKING: begin
            if(!avl_waitrequest && avl_readdatavalid) begin
                if(pma_error) begin
                    resolve_accessfault = true;
                end else if(pte_invalid) begin
                    resolve_pagefault = true;
                end else if(pte_is_leaf) begin
                    if(pte_missaligned) begin
                        resolve_pagefault = true;
                    end else if(!pte_missaligned) begin
                        resolve_done = true;
                    end
                end else if(pte_pointer) begin
                    if(current_level == 1'b0)
                        resolve_pagefault = true;
                    //else if(current_level == 1'b1) begin end;  
                end
            end
        end
    endcase
end

always @(posedge clk or negedge async_rst_n) begin
    if(!async_rst_n) begin
        state <= STATE_IDLE;

    end
    if(clk) begin
        case(state)
            STATE_IDLE: begin
                read_issued <= false;
                current_level <= 1'b1;
                saved_virtual_address <= virtual_address;
                current_table_base <= matp_ppn;
                if(resolve_request) begin
                    state <= STATE_TABLE_WALKING;
                    `ifdef DEBUG
                    $display("[PTW] Page table walk request matp_mode = %b for virtual_address = 0x%H", matp_mode, virtual_address);
                    `endif
                end
            end
            STATE_TABLE_WALKING: begin
                if(!avl_waitrequest)
                    read_issued <= true;
                if(!avl_waitrequest && avl_readdatavalid) begin
                    if(pma_error) begin
                        state <= STATE_IDLE;
                        `ifdef DEBUG
                        $display("[PTW] Request failed because of PMA for virtual_address = 0x%H, avl_address = 0x%H", {saved_virtual_address, 12'hXXX}, avl_address);
                        `endif
                    end else if(pte_invalid) begin
                        state <= STATE_IDLE;
                        `ifdef DEBUG
                        $display("[PTW] Request failed because PTE is invalid for virtual_address = 0x%H, avl_readdata = 0x%H", {saved_virtual_address, 12'hXXX}, avl_readdata);
                        `endif
                    end else if(pte_is_leaf) begin
                        state <= STATE_IDLE;
                        if(pte_missaligned) begin
                            `ifdef DEBUG
                            $display("[PTW] Request failed because PTE is missalligned for virtual_address = 0x%H, avl_readdata = 0x%H", {saved_virtual_address, 12'hXXX}, avl_readdata);
                            `endif
                        end else if(!pte_missaligned) begin
                            `ifdef DEBUG
                            $display("[PTW] Request successful for virtual_address = 0x%H, avl_readdata = 0x%H", {saved_virtual_address, 12'hXXX}, avl_readdata);
                            `endif
                        end
                    end else if(pte_pointer) begin
                        if(current_level == 1'b0) begin
                            `ifdef DEBUG
                            $display("[PTW] Resolve pagefault for virtual_address 0x%H, avl_readdata = 0x%H", {saved_virtual_address, 12'hXXX}, avl_readdata);
                            `endif
                        end else if(current_level == 1'b1) begin
                            current_level <= current_level - 1;
                            read_issued <= false;
                            current_table_base <= avl_readdata[31:10];
                            `ifdef DEBUG
                            $display("[PTW] Resolve going to next level for virtual_address 0x%H, avl_readdata = 0x%H", {saved_virtual_address, 12'hXXX}, avl_readdata);
                            `endif

                        end
                    end
                end
            end
        endcase
    end
end



endmodule