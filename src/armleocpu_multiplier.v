//`timescale 1ns/1ns

module armleocpu_multiplier(
	input  wire         clk,
    input  wire         rst_n,
	
	input  wire         valid,
	
	input  wire [31:0]  factor0,
	input  wire [31:0]  factor1,
	
	output reg          ready,
	output reg [63:0]  result
);

always @(posedge clk) begin
	result <= factor0 * factor1;
	ready <= valid;
end


/*
assign result = adder;

assign ready = cycle;


// multiply two 32b numbers
// a = a_up << 16 + a_down;
// b = b_up << 16 + b_down;

// a * b = (a_up * 2**16 + a_down) * (b_up * 2**16 + b_down) =
// = (a_down * b_up * 2**16) + b_down * a_down + (b_down * a_up * 2**16) + (a_up * b_up * 2**32) = 
// = (b_down * a_down) + (b_up * a_down * 2**16) + (b_down * a_up * 2**16) + (a_up * b_up * 2**32)



reg [63:0] accumulator;
reg cycle;

reg [31:0] mult0_in0;
reg [31:0] mult0_in1;
reg [5:0] mult0_shift_count;
wire [31:0] mult0_out = mult0_in0 * mult0_in1;


reg [31:0] mult1_in0;
reg [31:0] mult1_in1;
reg [5:0] mult1_shift_count;
wire [31:0] mult1_out = mult1_in0 * mult1_in1;

wire [15:0] a_down = factor0[15:0];
wire [15:0] b_down = factor1[15:0];
wire [15:0] a_up = factor0[31:16];
wire [15:0] b_up = factor1[31:16];

wire [63:0] mult0_intermediate_result = mult0_out << mult0_shift_count;
wire [63:0] mult1_intermediate_result = mult1_out << mult1_shift_count;

wire [63:0] adder = mult0_intermediate_result + mult1_intermediate_result + accumulator;

// Multiplexers

always @* begin
	mult0_shift_count = 0;
	mult0_in0 = b_down;
	mult0_in1 = a_down;

	mult1_shift_count = 16;
	mult1_in0 = b_down;
	mult1_in1 = a_down;


	case(cycle)
		0: begin
			mult0_in0 = b_down;
			mult0_in1 = a_down;
			mult0_shift_count = 0;

			mult1_in0 = b_up;
			mult1_in1 = a_up;
			mult1_shift_count = 32;

		end
		1: begin
			mult0_in0 = b_down;
			mult0_in1 = a_up;
			mult1_shift_count = 16;


			mult1_in0 = b_up;
			mult1_in1 = a_down;
			mult1_shift_count = 16;
		end
	endcase
end

always @(posedge clk) begin
	if(!rst_n) begin
		cycle <= 0;
		accumulator <= 0;
	end else begin
		case(cycle)
			0: begin
				if(valid) begin
					cycle <= 1;
					accumulator <= adder;
				end
			end
			1: begin
				if(valid) begin
					cycle <= 0;
					accumulator <= 0;
				end
			end
		endcase
	end
end
*/

endmodule