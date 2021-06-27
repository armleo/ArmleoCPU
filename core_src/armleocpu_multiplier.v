////////////////////////////////////////////////////////////////////////////////
//
// Filename: armleocpu_multiplier.v
// Project:	ArmleoCPU
//
// Purpose:	Multiplier 32x32 = 64
//		
//
// Copyright (C) 2021, Arman Avetisyan
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE



module armleocpu_multiplier(
	input  wire         clk,
    input  wire         rst_n,
	
	input  wire         valid,
	
	input  wire [31:0]  factor0,
	input  wire [31:0]  factor1,
	
	output reg          ready,
	output wire [63:0]  result
);

assign result = accumulator;

localparam STATE_IDLE = 1'd0;
localparam STATE_OP = 1'd1;
reg state = STATE_IDLE;

reg [63:0] accumulator;
reg [63:0] a;
reg [31:0] b;
reg [5:0] cycle;

always @(posedge clk) begin
	if(!rst_n) begin
		state <= STATE_IDLE;
		ready <= 0;
	end else begin
		case(state)
			STATE_IDLE: begin
				ready <= 0;
				accumulator <= 0;
				cycle <= 0;
				if(factor1 < factor0) begin
					a <= factor0;
					b <= factor1;
				end else begin
					a <= factor1;
					b <= factor0;
				end
				
				if(valid) begin
					state <= STATE_OP;
				end
			end
			STATE_OP: begin
				ready <= 0;
				accumulator <= accumulator + (b[0] ? a : 0);
				a <= a << 1;
				b <= {1'b0, b[31:1]}; // Shift right
				if(cycle == 31 || (b == 0)) begin
					ready <= 1;
					state <= STATE_IDLE;
				end else begin
					cycle <= cycle + 1;
				end
			end
		endcase
	end
end

endmodule


`include "armleocpu_undef.vh"
