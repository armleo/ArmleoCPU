`timescale 1ns/1ns

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

/*
multiply two 32b numbers
a = a_up << 16 + a_down;
b = b_up << 16 + b_down;

a * b = (a_up * 2**16 + a_down) * (b_up * 2**16 + b_down) =
= (a_down * b_up * 2**16) + b_down * a_down + (b_down * a_up * 2**16) + (a_up * b_up * 2**32) = 
= (b_down * a_down) + (b_up * a_down * 2**16) + (b_down * a_up * 2**16) + (a_up * b_up * 2**32)

=>
ready <= 0;
cycle register ->
	intermediate_result <= 0
	accumulator <= 0
cycle 0 -> 
	accumulator <= accumulator + intermediate_result
	intermediate_result <= (b_down * a_down)
cycle 1 ->
	accumulator <= accumulator + intermediate_result
	intermediate_result <= (b_down * a_up) << 16
cycle 2 ->
	accumulator <= accumulator + intermediate_result
	intermediate_result <= (b_up * a_down) << 16
cycle 3->
	accumulator <= accumulator + intermediate_result
	intermediate_result <= (b_up * a_up) << 32
cycle 4->
	accumulator <= accumulator + intermediate_result
	intermediate_result <= (b_down * a_down);// does not matter
	ready <= 1;
*/



localparam STATE_IDLE = 1'd0;
localparam STATE_OP = 1'd1;
reg state = STATE_IDLE;

reg [63:0] accumulator;
reg [63:0] intermediate_result;
reg [2:0] cycle;

reg [15:0] a_down;
reg [15:0] b_down;
reg [15:0] a_up;
reg [15:0] b_up;


reg [31:0] mult_in0;
reg [31:0] mult_in1;

wire [31:0] mult_out = mult_in0 * mult_in1;

reg [5:0] shift_count;

always @* begin
	shift_count = 0;
	mult_in0 = b_down;
	mult_in1 = a_down;
	case(cycle)
		0: begin
			mult_in0 = b_down;
			mult_in1 = a_down;
		end
		1: begin
			mult_in0 = b_down;
			mult_in1 = a_up;
			shift_count = 16;

		end
		2: begin
			mult_in0 = b_up;
			mult_in1 = a_down;
			shift_count = 16;
		end
		3: begin
			mult_in0 = b_up;
			mult_in1 = a_up;
			shift_count = 32;
		end
		default: begin
			shift_count = 0;
			mult_in0 = b_down;
			mult_in1 = a_down;
		end
	endcase
end

always @(posedge clk) begin
	if(!rst_n) begin
		state <= STATE_IDLE;
		ready <= 0;
	end else begin
		case(state)
			STATE_IDLE: begin
				ready <= 0;
				intermediate_result <= 0;
				accumulator <= 0;
				cycle <= 0;
				a_down <= factor0[15:0];
				b_down <= factor1[15:0];
				a_up <= factor0[31:16];
				b_up <= factor1[31:16];
				if(valid && !ready) begin
					state <= STATE_OP;
				end
			end
			STATE_OP: begin
				ready <= 0;
				intermediate_result <= 0;
				accumulator <= accumulator + intermediate_result;
				cycle <= cycle + 1;
				case(cycle)
					0, 1, 2, 3: begin
						intermediate_result <= mult_out << shift_count;
					end
					4: begin
						ready <= 1;
						state <= STATE_IDLE;
					end
				endcase
			end
		endcase
	end
end
/*
`ifdef FORMAL
reg [63:0] saved_result;
reg reseted = 0;
reg checked_at_least_one = 0;
always @(posedge clk) begin
	if(valid) begin
		saved_result <= {32'b0, factor0} * {32'b0, factor1};
		checked_at_least_one <= 1;
	end
	if(!rst_n) begin
		reseted <= 1;
	end
	if($past(valid) && !ready)
		assume ($stable(factor0) && $stable(factor1) && $stable(valid));
	// Verify all cases is impossible, but at least we can verify for (-100, +100)
	assume((factor0 < 100 && $signed(factor0) > -100) && (factor1 < 100 && $signed(factor1) > -100));
	assume(checked_at_least_one || valid || $past(valid));
	cover (reseted);
	cover (ready);
end


always @(negedge clk)
	if(ready && rst_n && reseted)
		assert(saved_result == result);
`endif
*/


endmodule