`timescale 1ns/1ns
module tlb_testbench;

`include "../clk_gen_template.svh"

initial begin
	$dumpfile(`SIMRESULT);
	$dumpvars;
	`assert(0, 1)
	#500
	$finish;
	
end

reg enable, invalidate, resolve, write;

reg [7:0] accesstag_w;
reg [21:0] phys_w;
reg [19:0]	virtual_address;

wire miss, done;
wire [7:0] accesstag_r;
wire [21:0] phys_r;


armleocpu_tlb tlb(
	.*
);

/*
	Test cases:
		resolve enable = 0 (1, 2)
		resolve enable = 1 (3, 4)
			w/ invalid
		write valid entry (5, 6, 7)
			to 0 entry
			to 1 entry
			to 2 entry
		resolve
			from 1, 2, 3 entry (8,9, 10,11, 12,13)
		write valid entry
			to 0 entry with different tag
			to 1 entry with different tag
			to 2 entry with different tag
		resolve
			from 0, 1, 2 entry
		invalidate
		resolve
		write valid entry
			to 0 entry
		resolve with enable = 1
		resolve with enable = 0
*/


integer state = 0;

reg [31:0] next_state;

always @* begin
	next_state = state + 1;
	case(state)
		1: begin
			invalidate = 0;
			enable = 0;
			write = 0;
			resolve = 1;
			virtual_address = 32'h0000_0000;
		end
		2: begin
			resolve = 0;
			
		end
		3: begin
			enable = 1;
			resolve = 1;
		end
		4: begin
			resolve = 0;
		end
		5: begin
			write = 1;
			virtual_address = 20'h2_0000;
			phys_w = 22'h1_0000;
			accesstag_w = 8'b1011_0001;
		end
		6: begin
			write = 1;
			virtual_address = 20'h2_0001;
			phys_w = 22'h1_0001;
			accesstag_w = 8'b1011_0011;
		end
		7: begin
			write = 1;
			virtual_address = 20'h2_0002;
			phys_w = 22'h1_0002;
			accesstag_w = 8'b1011_0101;
		end
		8: begin
			write = 0;
			virtual_address = 20'h2_0000;
			resolve = 1;
		end
		9: begin
			resolve = 0;
		end
		10: begin
			virtual_address = 20'h2_0001;
			enable = 1;
			resolve = 1;
		end
		11: begin
			resolve = 0;
		end
		12: begin
			virtual_address = 20'h2_0002;
			enable = 1;
			resolve = 1;
		end
		13: begin
			resolve = 0;
		end
		14: begin
			invalidate = 1;
		end
		15: begin
			invalidate = 0;
			virtual_address = 20'h2_0002;
			enable = 1;
			resolve = 1;
		end
		16: begin
			resolve = 0;
		end
		default: begin
			invalidate = 0;
			enable = 0;
			write = 0;
			resolve = 0;
			virtual_address = 32'h0000_0000;
		end
	endcase
end

always @(posedge clk) begin
	state <= next_state;
	case(state)
		//0:
		//1:
		2: begin
			`assert(done, 1)
			`assert(miss, 0)
		end
		//3:
		4: begin
			`assert(done, 1)
			`assert(miss, 1)
		end
		9: begin
			`assert(done, 1)
			`assert(miss, 0)
			`assert(accesstag_r, 8'b1011_0001);
			`assert(phys_r, 20'h1_0000);
		end
		11: begin
			`assert(done, 1)
			`assert(miss, 0)
			`assert(accesstag_r, 8'b1011_0011);
			`assert(phys_r, 20'h1_0001);
		end
		13: begin
			`assert(done, 1)
			`assert(miss, 0)
			`assert(accesstag_r, 8'b1011_0101);
			`assert(phys_r, 20'h1_0002);
		end
		16: begin
			`assert(done, 1)
			`assert(miss, 1)
			
		end
	endcase
end


endmodule