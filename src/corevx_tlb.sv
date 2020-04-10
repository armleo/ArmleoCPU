`timescale 1ns/1ns

module corevx_tlb(
	input clk,
	input rst_n,
	
	input					enable,
	input [19:0]		    virtual_address,
	
	
	// commands
	input					invalidate,
	input					resolve,
	output	reg				miss,
	output	reg				done,
	input					write,
	
	
	output	reg [7:0]		accesstag_r,
	output	reg [21:0]phys_r,
	
	input   [19:0]			virtual_address_w,
	input	[7:0]			accesstag_w,
	input 	[21:0]    phys_w
	
	
);

parameter  ENTRIES_W = 4;


parameter  WAYS_W = 2;
localparam WAYS = 2**WAYS_W;

wire [WAYS-1:0] tlbway_done;
wire [WAYS-1:0] tlbway_miss;

wire [7:0]         tlbway_accesstag_r  [WAYS-1:0];
wire [21:0]  tlbway_phys_r       [WAYS-1:0];

reg [WAYS_W-1:0] tlb_current_way;

reg [WAYS_W:0] i;


`ifdef DEBUG

always @* begin
    if(tlbway_done[0] != &tlbway_done) begin
        $display("[%d]One tlb responded incorrectly", $time);
        $finish;
    end
end
`endif


always @* begin 
    done = &tlbway_done;
    miss = done;
    accesstag_r = tlbway_accesstag_r[0];
    phys_r      = tlbway_phys_r[0];
    
    for(i = 0; i < WAYS; i = i + 1) begin
        if(!tlbway_miss[i[WAYS_W-1:0]]) begin
            miss        = 0;
            accesstag_r = tlbway_accesstag_r[i[WAYS_W-1:0]];
            phys_r      = tlbway_phys_r[i[WAYS_W-1:0]];
        end
    end
end

always @(posedge clk) begin
    if(!rst_n) begin
        tlb_current_way <= 0;
        `ifdef DEBUG
        $display("[%d]tlb_current_way = %d", $time, tlb_current_way);
        `endif
    end else if(clk) begin
        if(!resolve && write) begin
            tlb_current_way <= tlb_current_way + 1;
            `ifdef DEBUG
            $display("[%d]tlb_current_way = %d", $time, tlb_current_way);
            `endif
        end
    end
end

genvar way_num;
generate

for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin : tlbway_for
    corevx_tlb_way #(ENTRIES_W
    `ifdef DEBUG
    , way_num
    `endif
    ) tlbway(
        .rst_n              (rst_n),
        .clk                (clk),
        
        .enable             (enable),
        .virtual_address    (virtual_address),
        
        .invalidate         (invalidate),
        .resolve            (resolve),
        
        .miss               (tlbway_miss[way_num]),
        .done               (tlbway_done[way_num]),
        
        .accesstag_r        (tlbway_accesstag_r[way_num]),
        .phys_r             (tlbway_phys_r[way_num]),
        
        // write for for entry virt
        .write              (way_num == tlb_current_way && write),
        .virtual_address_w  (virtual_address_w),
        .accesstag_w        (accesstag_w),
        .phys_w             (phys_w)
    );
end
endgenerate

endmodule

