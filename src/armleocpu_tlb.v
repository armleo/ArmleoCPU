`timescale 1ns/1ns

module armleocpu_tlb_way(
    input clk,
    input rst_n,
    
    // commands
    input [1:0]         command,

    // read port
    input [19:0]        virtual_address,
    output  reg         hit,
    output  reg [7:0]   accesstag_r,
    output  reg [21:0]  phys_r,
    
    // write port
    input       [19:0]  virtual_address_w,
    input       [7:0]   accesstag_w,
    input       [21:0]  phys_w,

    // invalidate port
    input       [ENTRIES_W-1:0] invalidate_set_index
);

parameter ENTRIES_W = 4;
localparam ENTRIES = 2**ENTRIES_W;
parameter disable_debug = 0;

`include "armleocpu_tlb_defs.inc"

/*
	Address structure from virtual
	|20-ENTRIES_W bits		|ENTRIES_W bits	| 12 bit	  	|
	|VTAG					|set_index		|don't care		|
*/

wire [ENTRIES_W-1:0]        set_index = virtual_address[ENTRIES_W-1:0];
wire [ENTRIES_W-1:0]        set_index_w = virtual_address_w[ENTRIES_W-1:0];
wire [20-ENTRIES_W-1:0]     virt_tag = virtual_address[19:ENTRIES_W];
wire [20-ENTRIES_W-1:0]     virt_tag_w = virtual_address_w[19:ENTRIES_W];


/*
If resolve:
    request ptag[set_index], vtag[set_index], accesstag[set_index] read and output data on next cycle, keep active until next resolve
If write:
    request write to ptag[set_index_w] <- phys_w, vtag[set_index_w] <- virt_tag_w, accesstag[set_index_w] <- accesstag_w
If invalidate
    request write to accesstag[invalidate_set_index] <- invalid
*/

reg ptag_read;
reg ptag_write;
wire [21:0] ptag_readdata;

mem_1w1r #(
    .ELEMENTS_W(ENTRIES_W),
    .WIDTH(22)
) ptag_storage (
    .clk(clk),
    
    .read(ptag_read),
    .readaddress(set_index),
    .readdata(ptag_readdata),
    
    .write(ptag_write),
    .writeaddress(set_index_w),
    .writedata(phys_w)
);

reg vtag_read;
reg vtag_write;
wire [20-ENTRIES_W-1:0] vtag_readdata;

mem_1w1r #(
    .ELEMENTS_W(ENTRIES_W),
    .WIDTH(20-ENTRIES_W)
) vtag_storage (
    .clk(clk),
    
    .read(vtag_read),
    .readaddress(set_index),
    .readdata(vtag_readdata),
    
    .write(vtag_write),
    .writeaddress(set_index_w),
    .writedata(virt_tag_w)
);

reg accesstag_read;
wire [7:0] accesstag_readdata;
reg accesstag_write;
reg [ENTRIES_W-1:0]accesstag_write_set_index;
reg [7:0] accesstag_writedata;

mem_1w1r #(
    .ELEMENTS_W(ENTRIES_W),
    .WIDTH(8)
) accesstag_storage (
    .clk(clk),
    
    .read(accesstag_read),
    .readaddress(set_index),
    .readdata(accesstag_readdata),
    
    .write(accesstag_write),
    .writeaddress(accesstag_write_set_index),
    .writedata(accesstag_writedata)
);

reg [32-12-ENTRIES_W-1:0]   os_virt_tag;

`ifdef DEBUG_TLB
reg os_active;
`endif

assign phys_r      = ptag_readdata;
assign accesstag_r = accesstag_readdata;
assign hit         = accesstag_readdata[0] && (os_virt_tag == vtag_readdata);

always @* begin
    ptag_read       = 1'b0;
    ptag_write      = 1'b0;
    vtag_read                       = 1'b0;
    accesstag_read                  = 1'b0;
    vtag_write                      = 1'b0;
    accesstag_write                 = 1'b0;
    accesstag_writedata             = accesstag_w;
    accesstag_write_set_index       = set_index_w;
    if(command == `TLB_CMD_RESOLVE) begin
        ptag_read                   = 1'b1;
        vtag_read                   = 1'b1;
        accesstag_read              = 1'b1;
    end else if(command == `TLB_CMD_WRITE) begin
        vtag_write                  = 1'b1;
        ptag_write                  = 1'b1;
        accesstag_write             = 1'b1;
        accesstag_writedata         = accesstag_w;
        accesstag_write_set_index   = set_index_w;
    end else if(command == `TLB_CMD_INVALIDATE) begin
        accesstag_write             = 1'b1;
        accesstag_writedata         = 8'd0;
        accesstag_write_set_index   = invalidate_set_index;
    end
end


always @(posedge clk) begin
	if(!rst_n) begin
        `ifdef DEBUG_TLB
		    os_active <= 1'b0;
        `endif
	end else if(clk) begin
        `ifdef DEBUG_TLB
		    os_active <= 1'b0;
        `endif
        if(command == `TLB_CMD_RESOLVE) begin
            `ifdef DEBUG_TLB
                os_active <= 1'b1;
                if(!disable_debug) begin
                    $display("[%m][%d][TLB] TLB Resolve virtual_address=0x%X", $time, virtual_address);
                end
            `endif
            os_virt_tag <= virt_tag;
            // used in output stage for hit calculation
        end else if(command == `TLB_CMD_WRITE) begin
            `ifdef DEBUG_TLB
            if(!disable_debug)
                $display("[%m][%d][TLB] TLB Write virtual_address_w = 0x%X, accesstag_w = 0x%X, phys_w = 0x%X", $time, virtual_address_w, accesstag_w, phys_w);
            `endif
            // nothing in sync
        end else if(command == `TLB_CMD_INVALIDATE) begin
            `ifdef DEBUG_TLB
            if(!disable_debug)
                $display("[%m][%d][TLB] TLB Invalidate invalidate_set_index=0x%X", $time, invalidate_set_index);
            `endif
            // nothing in sync
        end
		`ifdef DEBUG_TLB
        if(!disable_debug)
            if(os_active) begin
                if(hit) begin
                    $display("[%m][%d][TLB] Resolve complete, hit accesstag_r = 0x%X, os_virt_tag = 0x%x", $time, accesstag_r, os_virt_tag);
                end else begin
                        $display("[%m][%d][TLB] Resolve missed", $time);
                end
            end
		`endif
	end
end

endmodule


module armleocpu_tlb(
    input clk,
    input rst_n,
    
    // commands
    input [1:0]         command,

    // read port
    input [19:0]        virtual_address,
    output  reg         hit,
    output  reg [7:0]   accesstag_r,
    output  reg [21:0]  phys_r,
    
    // write port
    input       [19:0]  virtual_address_w,
    input       [7:0]   accesstag_w,
    input       [21:0]  phys_w,

    // invalidate port
    input       [ENTRIES_W-1:0] invalidate_set_index
);

parameter  ENTRIES_W = 4;

parameter  WAYS_W = 2;
localparam WAYS = 2**WAYS_W;

parameter disable_debug = 0;

reg [WAYS_W-1:0] victim_way;

always @(posedge clk) begin
    if(!rst_n) begin
        victim_way <= 0;
    end else begin
        if(command == `TLB_CMD_WRITE)
            victim_way <= victim_way + 1;
    end
end

/*
for resolve request resolve for all tlb  ways
for write, write to tlb[victim_way]
for invalidate, write 0 to valid to all ways
*/
reg [WAYS_W-1:0] hit_waynum;
reg [1:0]       tlbway_command        [WAYS-1:0];
wire [WAYS-1:0] tlbway_hit;
wire [21:0]     tlbway_phys_r         [WAYS-1:0];
wire [7:0]      tlbway_accesstag_r    [WAYS-1:0];

integer i;
always @* begin
    hit_waynum = 0;
    hit = tlbway_hit[0];
    phys_r = tlbway_phys_r[0];
    accesstag_r = tlbway_accesstag_r[0];

    for(i = 0; i < WAYS; i = i + 1) begin
        if(tlbway_hit[i]) begin
            /* verilator lint_off WIDTH */
            hit_waynum = i;
            /* verilator lint_on WIDTH */
            hit = tlbway_hit[hit_waynum];
            phys_r = tlbway_phys_r[hit_waynum];
            accesstag_r = tlbway_accesstag_r[hit_waynum];
        end
    end
end

genvar way_num;
generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : mem_generate_for
    armleocpu_tlb_way #(ENTRIES_W, disable_debug) u_tlb_way (
        .clk(clk),
        .rst_n(rst_n),

        .command(tlbway_command[way_num]),

        .virtual_address(virtual_address),
        .hit(tlbway_hit[way_num]),
        .accesstag_r(tlbway_accesstag_r[way_num]),
        .phys_r(tlbway_phys_r[way_num]),

        .virtual_address_w(virtual_address_w),
        .accesstag_w(accesstag_w),
        .phys_w(phys_w),

        .invalidate_set_index(invalidate_set_index)
    );

    always @* begin
        tlbway_command[way_num] = command;

        if(command == `TLB_CMD_RESOLVE) begin
            tlbway_command[way_num] = command;
        end else if(command == `TLB_CMD_WRITE) begin
            if(way_num == victim_way) begin
                tlbway_command[way_num] = command;
            end else begin
                tlbway_command[way_num] = `TLB_CMD_NONE;
            end
        end
    end
end
endgenerate

endmodule

