module armleocpu_csr(
    input clk,
    input rst_n,

    // TODO: output zero at mtvec[0]

    output reg [31:0]   csr_mtvec,

    output reg          csr_satp_mode,
    output reg  [21:0]  csr_satp_ppn,

    output reg          csr_mstatus_mprv,
    output reg          csr_mstatus_mxr,
    output reg          csr_mstatus_sum,

    output reg [1:0]    csr_mstatus_mpp,

    output reg [1:0]    csr_mcurrent_privilege,

    output reg          csr_mstatus_mie,

    output reg          csr_mie_meie,
    output reg          csr_mie_mtie,

    output reg          csr_mip_meip,
    
    output reg          csr_mip_mtip,

    output reg [31:0]   csr_mepc,
    output reg [31:0]   csr_sepc,


// CSR Interface for exceptions
    input      [1:0]        csr_exc_cmd, //  Exception start, mret, sret
    input      [31:0]       csr_exc_cause,
    input      [31:0]       csr_exc_epc, //  Exception start pc

// CSR Interface for csr class instructions
    input      [2:0]        csr_cmd, // NONE, WRITE, READ, READ_WRITE, READ_SET, READ_CLEAR
    input      [11:0]       csr_address,
    output reg              csr_invalid,
    output reg [31:0]       csr_readdata,
    input      [31:0]       csr_writedata
    
);



endmodule