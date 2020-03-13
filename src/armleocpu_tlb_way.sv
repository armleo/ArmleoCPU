module armleocpu_tlb_way (
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

parameter ENTRIES_W = 4;
`ifdef DEBUG
parameter way_num = 0;
`endif
localparam ENTRIES = 2**ENTRIES_W;

`ifdef DEBUG
initial begin
	$display("[TLB] ENTRIES_W = %d, ENTRIES = %d", ENTRIES_W, ENTRIES);
end
`endif

/*
	Address structure from virtual
	|32-ENTRIES_W bits		|ENTRIES_W bits	| 12 bit	  	|
	|TAG					|set_index		|don't care		|
*/
localparam                  PHYS_W = 22;

wire [ENTRIES_W-1:0]		set_index = virtual_address[ENTRIES_W-1:0];
wire [ENTRIES_W-1:0]		set_index_w = virtual_address_w[ENTRIES_W-1:0];
wire [20-ENTRIES_W-1:0]	    virt_tag = virtual_address[19:ENTRIES_W];
wire [20-ENTRIES_W-1:0]	    virt_tag_w = virtual_address_w[19:ENTRIES_W];


reg [ENTRIES-1:0]           valid;
reg [7:1]			        accesstag	[ENTRIES-1:0];
reg [19-ENTRIES_W:0] 	    tag			[ENTRIES-1:0];
reg [PHYS_W-1:0]	        phys		[ENTRIES-1:0];

reg [ENTRIES_W-1:0]         set_index_r;
reg                         access_r;
reg                         enable_r;
reg [32-12-ENTRIES_W-1:0]   virt_tag_r;

always @* begin
	done = 1'b0;
	miss = 1'b0;
	if(enable_r) begin
		phys_r = phys[set_index_r];
		accesstag_r = {accesstag[set_index_r], valid[set_index_r]};
	end else begin
		phys_r = virtual_address;
		accesstag_r = 8'b11011111;
		// Read, write, execute, no global, access 1, dirty 1, user
	end
	if(access_r) begin
		if(enable_r) begin
			if(valid[set_index_r] && (virt_tag_r == tag[set_index_r])) begin
				done = 1'b1;
				miss = 1'b0;
			end else begin
				done = 1'b1;
				miss = 1'b1;
			end
		end else begin
			done = 1'b1;
			miss = 1'b0;
		end
	end
end


integer i;

always @(negedge rst_n or posedge clk) begin
	if(!rst_n) begin
		for(i = 0; i < ENTRIES; i = i + 1) begin
			valid[i] <= 1'b0;
		end
	end else if(clk) begin
		access_r <= resolve;
		
		if(resolve) begin
			`ifdef DEBUG
			$display("[TLB %d] Resolve request for virtual_address = 0x%X, set_index = 0x%X, enable = %b, virt_tag = 0x%X", way_num, virtual_address, set_index, enable, virt_tag);
			`endif
			set_index_r <= set_index;
			enable_r <= enable;
			virt_tag_r <= virt_tag;
		end else if(write) begin
			`ifdef DEBUG
			$display("[TLB %d] Write request virtual_address_w = 0x%X, set_index_w = 0x%X, accesstag_w = 0x%X, phys_w = 0x%X, virt_tag_w = 0x%X", way_num, virtual_address_w, set_index_w, accesstag_w, phys_w, virt_tag_w);
			`endif
			accesstag[set_index_w] <= accesstag_w[7:1];
			valid[set_index_w] <= accesstag_w[0];
			phys[set_index_w] <= phys_w;
			tag[set_index_w] <= virt_tag_w;
		end else if(invalidate) begin
			`ifdef DEBUG
			$display("[TLB %d] Invalidate request", way_num);
			`endif
			for(i = 0; i < ENTRIES; i = i + 1)
				valid[i] <= 1'b0;
		end
		`ifdef DEBUG
			if(access_r) begin
				if(enable_r) begin
					if(valid[set_index_r] && (virt_tag_r == tag[set_index_r])) begin
						$display("[TLB %d] Resolve complete, hit accesstag_r = 0x%X, virt_tag_r = 0x%x", way_num, accesstag_r, virt_tag_r);
					end else begin
						if(!valid[set_index_r])
							$display("[TLB %d] Resolve missed because invalid", way_num);
						else if(valid[set_index_r] && virt_tag_r != tag[set_index_r])
							$display("[TLB %d] Resolve missed because tag is different", way_num);
						else
							$display("[TLB %d] WTF", way_num);
					end
				end else begin
					$display("[TLB %d] Resolved virtual to physical", way_num);
				end
			end
		`endif
	end
end

endmodule