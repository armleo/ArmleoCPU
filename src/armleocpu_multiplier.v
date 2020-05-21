`timescale 1ns/1ns

module armleocpu_multiplier(
	input  wire         clk,
    input  wire         rst_n,
	
	input  wire         valid,
	
	input  wire [31:0]  factor0,
	input  wire [31:0]  factor1,
	
	output reg          ready,
	output reg  [63:0]  result
);

reg [31:0] r_factor;

reg [63:0] r_addvalue = 64'd0;

reg [5:0] r_counter = 6'd0;

localparam step_size = 16;

reg state = STATE_IDLE;
localparam STATE_IDLE = 1'd0;
localparam STATE_OP = 1'd1;

always @(posedge clk) begin
	if(!rst_n) begin
		state <= STATE_IDLE;
		ready <= 0;
	end else begin
		case(state)
			STATE_IDLE: begin
				ready <= 0;
				r_counter <= 0;
				result <= 0;
				r_factor <= factor0;
				/* verilator lint_off WIDTH */
				r_addvalue <= factor1;
				/* verilator lint_on WIDTH */
				if(valid) begin
					state <= STATE_OP;
				end
			end
			STATE_OP: begin
				ready <= 0;
				/* verilator lint_off WIDTH */
				r_factor <= r_factor[31:step_size];
				/* verilator lint_on WIDTH */
				result <= result + (r_factor[step_size-1:0] * r_addvalue);
				r_addvalue <= r_addvalue << step_size;
				if(r_counter + step_size < 31) begin
					r_counter <= r_counter + step_size;
				end else begin
					ready <= 1;
					state <= STATE_IDLE;
				end
			end
		endcase
	end
end

endmodule

/*
// TODO: Fix and Test

module signed_multiplier(
	input  wire clk,
	
	input  wire fetch,
	
	input  wire [31:0] factor0,
	input  wire [31:0] factor1,
	
	
	output reg ready = 1'b0,
	output reg [63:0] result = 64'd0
);

reg r_fetch = 1'd0;
wire r_ready;
wire [63:0] r_result;

reg [31:0] r_factor0, r_factor1;



reg sign_invert;

unsigned_multiplier u0(
	.clk(clk),
	.fetch(r_fetch),
	
	.factor0(r_factor0),
	.factor1(r_factor1),
	
	.ready(r_ready),
	.result(r_result)
);

always @(posedge clk) begin
	if(fetch) begin
		if(factor0[31]) begin
			r_factor0 <= ~(factor0) + 1;
		end else begin
			r_factor0 <= factor0;
		end
		
		if(factor1[31]) begin
			r_factor1 <= ~(factor1) + 1;
		end else begin
			r_factor1 <= factor1;
		end
		sign_invert <= factor0[31] ^ factor1[31];
		r_fetch <= 1;
	end else begin
		r_fetch <= 0;
	end
	if(r_ready) begin
		ready <= 1;
		if(sign_invert)
			result <= ~(r_result) + 1;
		else
			result <= r_result;
	end else begin
		ready <= 0;
	end
end

endmodule
*/