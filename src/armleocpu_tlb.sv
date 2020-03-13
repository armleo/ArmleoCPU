module armleocpu_tlb(
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
	output	reg [PHYS_W-1:0]phys_r,
	
	input   [19:0]			virtual_address_w,
	input	[7:0]			accesstag_w,
	input 	[PHYS_W-1:0]    phys_w
	
	
);

parameter  ENTRIES_W = 4;


parameter  WAYS_W = 2;
localparam WAYS = 2**WAYS_W;


localparam PHYS_W = 22;


logic [WAYS-1:0] tlbway_done;
logic [WAYS-1:0] tlbway_miss;

logic [7:0]         tlbway_accesstag_r  [WAYS-1:0];
logic [PHYS_W-1:0]  tlbway_phys_r       [WAYS-1:0];

logic [WAYS_W-1:0] tlb_current_way;

logic [WAYS_W-1:0] hit_way;

integer i;
genvar way_num;

`ifdef DEBUG

always @* begin
    if(tlbway_done[0] != &tlbway_done) begin
        $display("One tlb responded incorrectly");
        $finish;
    end
end
`endif


always @* begin 
    done = &tlbway_done;
    miss = done;
    hit_way = 0;
    accesstag_r = tlbway_accesstag_r[0];
    phys_r      = tlbway_phys_r[0];
    
    for(i = WAYS-1; i >= 0; i = i - 1) begin
        if(tlbway_done[i] && !tlbway_miss[i]) begin
            miss        = 0;
            hit_way     = i;
            accesstag_r = tlbway_accesstag_r[i];
            phys_r      = tlbway_phys_r[i];
        end
    end
end

always @(negedge rst_n or posedge clk) begin
    if(!rst_n) begin
        tlb_current_way = 0;
        $display("tlb_current_way = %d", tlb_current_way);
    end else if(clk) begin
        if(!resolve && write) begin
            tlb_current_way = tlb_current_way + 1;
            $display("tlb_current_way = %d", tlb_current_way);
        end
    end
end



for(way_num = 0; way_num < WAYS; way_num = way_num + 1) begin
    armleocpu_tlb_way #(ENTRIES_W, way_num) tlbway(
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


endmodule

