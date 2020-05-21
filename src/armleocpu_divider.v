`timescale 1ns/1ns

module armleocpu_unsigned_divider(
	input  wire 		clk,
	input  wire 		rst_n,
	input  wire 		fetch,
	
	input  wire [31:0]  dividend,
	input  wire [31:0]  divisor,
	
	
	output reg			ready,
	output reg   		division_by_zero,
	output reg   [31:0] quotient,
	output reg   [31:0] remainder
);

reg [31:0] r_dividend;
reg [31:0] r_divisor;

reg [5:0] counter;


wire [31:0] difference = remainder - divisor;
wire positive = remainder >= divisor;

reg state;
localparam STATE_IDLE = 1'b0;
localparam STATE_OP = 1'b1;

always @(posedge clk) begin
	if(!rst_n) begin
		state <= STATE_IDLE;
		ready <= 0;
		division_by_zero <= 1'b0;
	end else begin
		case(state)
			STATE_IDLE: begin
				ready <= 0;
				division_by_zero <= 1'b0;
				counter <= 0;
				remainder <= 0;
				if(fetch) begin
					if(divisor != 0) begin
						r_dividend <= dividend;
						r_divisor <= divisor;
						state <= STATE_OP;
					end else begin
						ready <= 1'b1;
						division_by_zero <= 1'b1;
					end
				end
			end
			STATE_OP: begin
				r_dividend <= {r_dividend[30:0], 1'b0};
				quotient <= {quotient[30:0], positive};
				
				if(positive) begin // add 1 to quotient, substract from dividend
					remainder <= {difference[30:0], r_dividend[31]};
				end else begin // add 0 to quotient,
					remainder <= {remainder[30:0], r_dividend[31]};
				end
				
				if(counter != 32) begin
					counter <= counter + 6'b1;
				end else begin
					if(positive)
						remainder <= difference;
					else
						remainder <= remainder;
					ready <= 1'b1;
					state <= STATE_IDLE;
				end
			end
		endcase
	end
end

endmodule

/*
module signed_divider(
	input  wire clk,
	
	input  wire fetch,
	
	input  wire [31:0] dividend,
	input  wire [31:0] divisor,
	
	
	output reg ready,
	output reg division_by_zero,
	output reg  [31:0] quotient,
	output reg  [31:0] remainder
);

reg i_fetch;
reg sign_invert;

wire i_ready, i_division_by_zero;

reg [31:0] i_dividend, i_divisor;
wire [31:0] i_quotient, i_remainder;
unsigned_divider u0(
	.clk(clk),
	
	.fetch(i_fetch),
	
	.dividend(i_dividend),
	.divisor(i_divisor),
	
	.ready(i_ready),
	.division_by_zero(i_division_by_zero),
	.quotient(i_quotient),
	.remainder(i_remainder)
);

always @(posedge clk) begin
	if(fetch) begin
		if(dividend[31]) begin
			i_dividend <= ~(dividend) + 1;
		end else begin
			i_dividend <= dividend;
		end
		if(divisor[31]) begin
			i_divisor <= ~(divisor) + 1;
		end else begin
			i_divisor <= divisor;
		end
		sign_invert <= divisor[31] ^ dividend[31];
		i_fetch <= 1;
	end else begin
		i_fetch <= 0;
	end
	
	if(i_ready) begin
		if(i_division_by_zero) begin
			ready <= 1;
			division_by_zero <= 1;
		end else begin
			if(sign_invert) begin
				quotient <= -i_quotient;
			end else begin
				quotient <= i_quotient;
			end
			remainder <= i_remainder;
			ready <= 1;
		end
	end else begin
		ready <= 0;
	end
end

endmodule
*/