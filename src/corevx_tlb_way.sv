`timescale 1ns/1ns
module corevx_tlb_way (
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

parameter ENTRIES_W = 4;
localparam ENTRIES = 2**ENTRIES_W;
parameter disable_debug = 0;

/*
	Address structure from virtual
	|20-ENTRIES_W bits		|ENTRIES_W bits	| 12 bit	  	|
	|TAG					|set_index		|don't care		|
*/
wire [ENTRIES_W-1:0]        set_index = virtual_address[ENTRIES_W-1:0];
wire [ENTRIES_W-1:0]        set_index_w = virtual_address_w[ENTRIES_W-1:0];
wire [20-ENTRIES_W-1:0]     virt_tag = virtual_address[19:ENTRIES_W];
wire [20-ENTRIES_W-1:0]     virt_tag_w = virtual_address_w[19:ENTRIES_W];


reg [ENTRIES-1:0]           valid;
reg [7:1]                   accesstag   [ENTRIES-1:0];
reg [19-ENTRIES_W:0]        vtag        [ENTRIES-1:0];
reg [21:0]                  phys        [ENTRIES-1:0];

reg [ENTRIES_W-1:0]         set_index_r;
reg [32-12-ENTRIES_W-1:0]   virt_tag_r;

assign phys_r      = phys[set_index_r];
assign accesstag_r = {accesstag[set_index_r], valid[set_index_r]};
assign hit         = valid[set_index_r] && (virt_tag_r == tag[set_index_r])


integer i;

always @(posedge clk) begin
	if(!rst_n) begin
		for(i = 0; i < ENTRIES; i = i + 1) begin
			valid[i] <= 1'b0;
		end
	end else if(clk) begin
		access_r <= resolve;
		
		if(resolve) begin
			`ifdef DEBUG
			if(!disable_debug)
				$display("[%d][TLB %d] Resolve request for virtual_address = 0x%X, set_index = 0x%X, enable = %b, virt_tag = 0x%X", $time, way_num, virtual_address, set_index, enable, virt_tag);
			`endif
			set_index_r <= set_index;
			enable_r <= enable;
			virt_tag_r <= virt_tag;
		end else if(write) begin
			`ifdef DEBUG
			if(!disable_debug)
				$display("[%d][TLB %d] Write request virtual_address_w = 0x%X, set_index_w = 0x%X, accesstag_w = 0x%X, phys_w = 0x%X, virt_tag_w = 0x%X", $time, way_num, virtual_address_w, set_index_w, accesstag_w, phys_w, virt_tag_w);
			`endif
			accesstag[set_index_w] <= accesstag_w[7:1];
			valid[set_index_w] <= accesstag_w[0];
			phys[set_index_w] <= phys_w;
			tag[set_index_w] <= virt_tag_w;
		end else if(invalidate) begin
			`ifdef DEBUG
			if(!disable_debug)
				$display("[%d][TLB %d] Invalidate request", $time, way_num);
			`endif
			for(i = 0; i < ENTRIES; i = i + 1)
				valid[i] <= 1'b0;
		end
		`ifdef DEBUG
			if(access_r) begin
				if(enable_r) begin
					if(valid[set_index_r] && (virt_tag_r == tag[set_index_r])) begin
						$display("[%d][TLB %d] Resolve complete, hit accesstag_r = 0x%X, virt_tag_r = 0x%x", $time, way_num, accesstag_r, virt_tag_r);
					end else begin
						if(!valid[set_index_r])
							$display("[%d][TLB %d] Resolve missed because invalid", $time, way_num);
						else if(valid[set_index_r] && virt_tag_r != tag[set_index_r])
							$display("[%d][TLB %d] Resolve missed because tag is different", $time, way_num);
						else
							$display("[%d][TLB %d] WTF", $time, way_num);
					end
				end else begin
					$display("[%d][TLB %d] Resolved virtual to physical", $time, way_num);
				end
			end
		`endif
	end
end

endmodule