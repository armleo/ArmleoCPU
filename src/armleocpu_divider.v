// TODO: Add Timescale

// TODO: Fix and test

/*module unsigned_divider(
	input  wire clk,
	
	input  wire fetch,
	
	input  wire [31:0] dividend,
	input  wire [31:0] divisor,
	
	
	output wire ready,
	output wire division_by_zero,
	output wire  [31:0] quotient,
	output wire  [31:0] remainder
);

// Registered signals
// May be changed in operations
reg [31:0] r_dividend;
reg [31:0] r_divisor;

reg [5:0] r_counter = 6'd31;

reg [31:0] r_quotient;


reg [31:0] r_minued;

assign remainder = r_minued;
assign quotient = r_quotient;

wire [31:0] difference = r_minued - r_divisor;
wire positive = r_minued >= r_divisor;

assign ready = r_ready;
assign division_by_zero = r_division_by_zero;

reg r_ready, r_division_by_zero;

reg state = STATE_IDLE;
localparam STATE_IDLE = 1'b0;
localparam STATE_OP = 1'b1;

always @(posedge clk) begin
	case(state)
		STATE_IDLE: begin
			if(fetch) begin
				if(divisor > 0) begin
					r_dividend <= dividend;
					r_divisor <= divisor;
					r_quotient <= 0;
					r_counter <= 0;
					r_ready <= 1'b0;
					r_minued <= 0;
					r_division_by_zero <= 1'b0;
					state <= STATE_OP;
				end else begin
					r_ready <= 1'b1;
					r_division_by_zero <= 1'b1;
				end
			end else begin
				r_ready <= 1'b0;
				r_division_by_zero <= 1'b0;
			end
		end
		STATE_OP: begin
			r_dividend <= {r_dividend[30:0], 1'b0};
			r_quotient <= {r_quotient[30:0], positive};
			
			if(positive) begin // positive
				r_minued <= {difference[30:0], r_dividend[31]};
			end else begin     // negative
				r_minued <= {r_minued [30:0], r_dividend[31]};
			end
			
			if(r_counter != 32) begin
				r_counter <= r_counter + 6'b1;
			end else begin
				r_ready <= 1'b1;
				state <= STATE_IDLE;
			end
		end
	endcase
end

endmodule


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

endmodule*/
