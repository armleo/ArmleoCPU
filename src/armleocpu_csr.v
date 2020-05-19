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

    output reg          csr_mstatus_tsr,
    output reg          csr_mstatus_tw,
    output reg          csr_mstatus_tvm,
    

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

wire csr_write = csr_cmd == `CSR_CMD_WRITE || csr_cmd == `CSR_CMD_READ_WRITE;


always @(posedge clk) begin
    if(!rst_n) begin

    end else begin
        if(csr_write) begin
            case(csr_address)
                12'h180: begin // SATP
                    csr_satp_mode <= csr_writedata[31];
                    csr_satp_ppn <= csr_writedata[21:0];
                end
                12'h300: begin // MSTATUS
                    csr_mstatus_mprv <= csr_writedata[];
                    csr_mstatus_mxr <= csr_writedata[];
                    csr_mstatus_sum <= csr_writedata[];

                    csr_mstatus_tsr <= csr_writedata[];
                    csr_mstatus_tw <= csr_writedata[];
                    csr_mstatus_tvm <= csr_writedata[];
                    
                    csr_mstatus_mpp <= csr_writedata[];
                    

                end
            endcase
        end
    end
end
endmodule