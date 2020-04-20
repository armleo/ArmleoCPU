`timescale 1ns/1ns

module corevx_tlb_way(
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

`ifdef DEBUG
reg os_active;
`endif

assign phys_r      = phys_readdata;
assign accesstag_r = accesstag_readdata;
assign hit         = accesstag_readdata[0] && (os_virt_tag == vtag_readdata)

always @* begin
    ptag_read       = 1'b0;
    ptag_write      = 1'b0;

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
        `ifdef DEBUG
		    os_active <= 1'b0;
        `endif
	end else if(clk) begin
        `ifdef DEBUG
		    os_active <= 1'b0;
        `endif
        if(command == `TLB_CMD_RESOLVE) begin
            `ifdef DEBUG
                os_active <= 1'b1;
            `endif
            os_virt_tag <= virt_tag;
            // used in output stage for hit calculation
        end else if(command == `TLB_CMD_WRITE) begin
            `ifdef DEBUG
            if(!disable_debug)
                $display("[%d][TLB %d] TLB Write ", $time, );
            `endif
            // nothing in sync
        end else if(command == `TLB_CMD_INVALIDATE) begin
            `ifdef DEBUG
            if(!disable_debug)
                $display("[%d][TLB %d] TLB Invalidate ", $time, );
            `endif
            // nothing in sync
        end
		`ifdef DEBUG
        if(!disable_debug)
            if(os_active) begin
                if(hit) begin
                    $display("[%d][TLB %d] Resolve complete, hit accesstag_r = 0x%X, os_virt_tag = 0x%x", $time, accesstag_r, os_virt_tag);
                end else begin
                    if(!valid[set_index_r])
                        $display("[%d][TLB %d] Resolve missed because invalid", $time);
                    else if(!hit)
                        $display("[%d][TLB %d] Resolve missed because tag is different", $time);
                    else
                        $display("[%d][TLB %d] Unknown resolve result [BUG]", $time);
                end
            end
		`endif
	end
end



endmodule


/*
module corevx_tlb(
    input clk,
    input rst_n,
    
    input [19:0]        virtual_address,
    // commands
    input [1:0]         command,

    // read port
    output  reg         hit,
    output  reg [7:0]   accesstag_r,
    output  reg [21:0]  phys_r,
    
    // write port
    input       [19:0]  virtual_address_w,
    input       [7:0]   accesstag_w,
    input       [21:0]  phys_w
);

parameter  ENTRIES_W = 4;

parameter  WAYS_W = 2;
localparam WAYS = 2**WAYS_W;

/*
for resolve request resolve for all tlb  ways
for write, write to tlb[victim_way]
for invalidate, write 0 to valid to all ways
*/
/*
genvar way_num;
generate
for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : mem_generate_for

end
endgenerate

endmodule
*/
