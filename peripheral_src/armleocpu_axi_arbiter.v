////////////////////////////////////////////////////////////////////////////////
//
// Filename:    armleocpu_axi_arbiter.v
// Project:	ArmleoCPU
//
// Purpose:	A basic N-to-1 AXI4 arbiter
// Deps: armleocpu_defines.vh for AXI4 declarations
//
// Parameters:
//	OPT_NUMBER_OF_HOSTS
//      Number of hosts from 1 to any value that can be synthesized
//      and fits into parameter.
//      Some synthesizer cant synthesize too many for loops.
//
//	OPT_PASSTHROUGH
//		Dont put register at outputs. Not recommended because for high number of hosts perfomance might take a hit
// Copyright (C) 2021, Arman Avetisyan
////////////////////////////////////////////////////////////////////////////////


module armleocpu_axi_arbiter #(
    parameter OPT_NUMBER_OF_HOSTS = 2,
    

    parameter [0:0] OPT_PASSTHROUGH
) (
    
);

endmodule