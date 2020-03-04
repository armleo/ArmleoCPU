module armleocpu_tlb(
	input clk,
	input rst_n,
	
	input					enable,
	input [21:0]		    virt,
	
	
	// commands
	input					invalidate,
	input					resolve,
	output	reg				miss,
	output	reg				done,
	input					write,
	
	
	output	reg [7:0]		accesstag_r,
	output	reg [21:0]		phys_r,
	
	input	[7:0]			accesstag_w,
	input 	[21:0]		    phys_w
	
	
);

parameter ENTRIES = 32;
localparam ENTRIES_W = $clog2(ENTRIES);


/*
	Address structure from virtual
	|32-ENTRIES_W bits		|ENTRIES_W bits	| 12 bit	  	|
	|TAG					|index			|don't care		|
*/
localparam                  PHYS_W = 32 - 12;
localparam                  VIRT_W = 32 - 12 - ENTRIES_W;

wire [ENTRIES_W-1:0]		index = virt[ENTRIES_W-1:0];
wire [22-ENTRIES_W-1:0]	    virt_tag = virt[21:ENTRIES_W];

reg [ENTRIES-1:0]           valid;
reg [7:1]			        accesstag	[ENTRIES-1:0];
reg [VIRT_W-1:0] 	        tag			[ENTRIES-1:0];
reg [PHYS_W-1:0]	        phys		[ENTRIES-1:0];

reg [ENTRIES_W-1:0]         index_r;
reg                         access_r;
reg                         enable_r;
reg [32-12-ENTRIES_W-1:0]   virt_tag_r;

always @* begin
	done = 1'b0;
	miss = 1'b0;
	if(enable_r) begin
		phys_r = phys[index_r];
		accesstag_r = {accesstag[index_r], valid[index_r]};
	end else begin
		phys_r = virt;
		accesstag_r = 8'b11011111;
		// Read, write, execute, no global, access 1, dirty 1, user
	end
	if(access_r) begin
		if(enable_r) begin
			if(valid[index_r] && (virt_tag_r == tag[index_r])) begin
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
			index_r <= index;
			enable_r <= enable;
			virt_tag_r <= virt_tag;
		end else if(write) begin
			accesstag[index] <= accesstag_w[7:1];
			valid[index] <= accesstag_w[0];
			phys[index] <= phys_w;
			tag[index] <= virt_tag;
		end else if(invalidate) begin
			for(i = 0; i < ENTRIES; i = i + 1)
				valid[i] <= 1'b0;
		end
	end
end

endmodule