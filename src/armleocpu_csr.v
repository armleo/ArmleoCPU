module armleocpu_csr(clk, rst_n,
csr_mcurrent_privilege,
csr_cmd, /*csr_exc_cause, csr_exc_epc,*/ csr_address, csr_invalid, csr_readdata, csr_writedata);

    `include "armleocpu_csr.vh"
    `include "armleocpu_privilege.vh"


    input clk;
    input rst_n;

    // TODO: output zero at mtvec[0]
/*
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
*/
    output reg [1:0]    csr_mcurrent_privilege;
/*
    output reg          csr_mstatus_mie,

    output reg          csr_mie_meie,
    output reg          csr_mie_mtie,

    output reg          csr_mip_meip,
    
    output reg          csr_mip_mtip,

    output reg [31:0]   csr_mepc,
    output reg [31:0]   csr_sepc,

    input      [63:0]   cycle,
    //input      [63:0]   time,
    // time is hardwired to cycle
    input      [63:0]   instret,
*/

// CSR Interface for csr class instructions
    input      [`ARMLEOCPU_CSR_CMD_WIDTH-1:0]        csr_cmd;
    // NONE, WRITE, READ, READ_WRITE, READ_SET, READ_CLEAR,
    //MRET, SRET, INTERRUPT_BEGIN, EXCEPTION_BEGIN
    //input      [31:0]       csr_exc_cause;
    //input      [31:0]       csr_exc_epc; //  Exception start pc
    input      [11:0]       csr_address;
    output reg              csr_invalid;
    output reg [31:0]       csr_readdata;
    input      [31:0]       csr_writedata;



wire csr_write = csr_cmd == `ARMLEOCPU_CSR_CMD_WRITE || csr_cmd == `ARMLEOCPU_CSR_CMD_READ_WRITE;
wire  csr_read =  csr_cmd == `ARMLEOCPU_CSR_CMD_READ || csr_cmd == `ARMLEOCPU_CSR_CMD_READ_WRITE;

`define DEFINE_CSR_BEHAVIOUR(main_reg, main_reg_nxt, default_val) \
always @(posedge clk) \
    if(!rst_n) \
        main_reg <= default_val; \
    else \
        main_reg <= main_reg_nxt;

reg [31:0] csr_mscratch;
reg [31:0] csr_mscratch_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mscratch, csr_mscratch_nxt, 0)
reg [1:0] csr_mcurrent_privilege_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mcurrent_privilege, csr_mcurrent_privilege_nxt, 2'b11)

always @* begin
    csr_mscratch_nxt = csr_mscratch;
    csr_mcurrent_privilege_nxt = csr_mcurrent_privilege;
    csr_readdata = 0;
    csr_invalid = 0;
    case(csr_address)
        12'hFC0: begin // csr_mcurrent_privilege
            if(csr_write)
                csr_invalid = 1;
            if(csr_read) begin
                if(csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) begin
                    csr_readdata = {30'h0, csr_mcurrent_privilege};
                    csr_invalid = 0;
                end else
                    csr_invalid = 1;
            end
        end
        12'h340: begin // MSCRATCH
            csr_readdata = csr_mscratch;
            if(csr_write) begin
                if(csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) begin
                    csr_mscratch_nxt = csr_writedata;
                    csr_invalid = 0;
                end else begin
                    csr_invalid = 1;
                end
            end
            if(csr_read) begin
                if(csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) begin
                    csr_invalid = csr_invalid || 0;
                end else begin
                    csr_invalid = 1;
                end
            end
        end
        default: begin
            csr_invalid = csr_read || csr_write;
        end
    endcase
end
/*reg csr_satp_mode_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_satp_mode, csr_satp_mode_nxt, 0)
*/

/*

always @(posedge clk) begin
    if(!rst_n) begin
        csr_mcurrent_privilege <= `ARMLEOCPU_PRIVILEGE_MACHINE;
        csr_satp_mode <= 0;
        csr_satp_ppn <= 0;

        csr_mstatus_mprv <= 0;
        csr_mstatus_mxr <= 0;
        csr_mstatus_sum <= 0;

        csr_mstatus_tsr <= 0;
        csr_mstatus_tw <= 0;
        csr_mstatus_tvm <= 0;

        csr_mstatus_spp <= 0;
        csr_mstatus_mpie <= 0;
        csr_mstatus_spie <= 0;
        csr_mstatus_mie <= 1;
        csr_mstatus_sie <= 0;
    end else begin

        csr_satp_mode <= csr_satp_mode_nxt;
        csr_satp_ppn <= csr_writedata[21:0];
        if(csr_write) begin
            case(csr_address)
                12'h180: begin // SATP
                    
                end
                12'h300: begin // MSTATUS
                    csr_mstatus_mprv <= csr_writedata[17];
                    csr_mstatus_mxr <= csr_writedata[19];
                    csr_mstatus_sum <= csr_writedata[18];

                    csr_mstatus_tsr <= csr_writedata[22];
                    csr_mstatus_tw <= csr_writedata[21];
                    csr_mstatus_tvm <= csr_writedata[20];
                    
                    csr_mstatus_mpp <= csr_writedata[12:11];
                    
                    csr_mstatus_spp <= csr_writedata[8];
                    csr_mstatus_mpie <= csr_writedata[7];
                    csr_mstatus_spie <= csr_writedata[6];

                    csr_mstatus_mie <= csr_writedata[3];
                    csr_mstatus_sie <= csr_writedata[1];
                end
                
            endcase
        end
    end
end*/
endmodule