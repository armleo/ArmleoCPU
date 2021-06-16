////////////////////////////////////////////////////////////////////////////////
//
// Filename:    armleocpu_arbiter.v
// Project:	ArmleoCPU
//
// Purpose:	Simple round-robin Arbiter
// Deps: None
//
// Parameters:
//	OPT_N
//      Number of request ports
// Ports:
//  next
//      Host registered outputs and arbiter should register
//      current grants and start generating new grant signals for next cycle.
// Copyright (C) 2021, Arman Avetisyan
////////////////////////////////////////////////////////////////////////////////
module armleocpu_arbiter #(
    parameter OPT_N = 2,
    localparam OPT_N_CLOG2 = $clog2(OPT_N)
) (
    input clk,
    input rst_n,

    input next,
    input [OPT_N-1:0] req,
    output [OPT_N-1:0] grant,
    output [OPT_N_CLOG2-1:0] grant_id
);
// TODO: Implement

endmodule
