module armleocpu_csr(clk, rst_n,
csr_mcurrent_privilege, csr_mtvec,
csr_mstatus_mprv, csr_mstatus_mxr, csr_mstatus_sum,
csr_mstatus_tsr, csr_mstatus_tw, csr_mstatus_tvm,
csr_mstatus_mpp,
csr_mstatus_mie,
csr_cmd, /*csr_exc_cause, csr_exc_epc,*/ csr_address, csr_invalid, csr_readdata, csr_writedata);

    `include "armleocpu_csr.vh"
    `include "armleocpu_privilege.vh"


    input clk;
    input rst_n;

    // TODO: output zero at mtvec[0]

    output reg [31:0]   csr_mtvec;
/*
    output reg          csr_satp_mode,
    output reg  [21:0]  csr_satp_ppn,
*/

    output reg          csr_mstatus_mprv;
    output reg          csr_mstatus_mxr;
    output reg          csr_mstatus_sum;

    output reg          csr_mstatus_tsr;
    output reg          csr_mstatus_tw;
    output reg          csr_mstatus_tvm;
    

    output reg [1:0]    csr_mstatus_mpp;



    output reg [1:0]    csr_mcurrent_privilege;

    output reg          csr_mstatus_mie;

/*
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



parameter MVENDORID = 32'h0A1AA1E0;
parameter MARCHID = 32'h1;
parameter MIMPID = 32'h1;
parameter MHARTID = 32'h0;

wire csr_write = csr_cmd == `ARMLEOCPU_CSR_CMD_WRITE || csr_cmd == `ARMLEOCPU_CSR_CMD_READ_WRITE;
wire  csr_read =  csr_cmd == `ARMLEOCPU_CSR_CMD_READ || csr_cmd == `ARMLEOCPU_CSR_CMD_READ_WRITE;

wire accesslevel_invalid = (csr_write || csr_read) && (csr_mcurrent_privilege < csr_address[9:8]);
wire write_invalid = (csr_write && (csr_address[11:10] == 2'b11));
// wire address_writable = (csr_address[11:10] != 2'b11);

`define DEFINE_CSR_BEHAVIOUR(main_reg, main_reg_nxt, default_val) \
always @(posedge clk) \
    if(!rst_n) \
        main_reg <= default_val; \
    else \
        main_reg <= main_reg_nxt;
`define DEFINE_COMB_MRO(address, val) \
    address: begin \
        csr_invalid = accesslevel_invalid || write_invalid; \
        csr_readdata = val; \
    end


reg [31:0] csr_mscratch;

reg [31:0]   csr_mtvec_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mtvec, csr_mtvec_nxt, 0)
reg [31:0] csr_mscratch_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mscratch, csr_mscratch_nxt, 0)
reg [1:0] csr_mcurrent_privilege_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mcurrent_privilege, csr_mcurrent_privilege_nxt, 2'b11)

reg csr_mstatus_tsr_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_tsr, csr_mstatus_tsr_nxt, 0)
reg csr_mstatus_tw_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_tw, csr_mstatus_tw_nxt, 0)
reg csr_mstatus_tvm_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_tvm, csr_mstatus_tvm_nxt, 0)

reg csr_mstatus_mxr_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_mxr, csr_mstatus_mxr_nxt, 0)
reg csr_mstatus_sum_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_sum, csr_mstatus_sum_nxt, 0)
reg csr_mstatus_mprv_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_mprv, csr_mstatus_mprv_nxt, 0)

reg [1:0] csr_mstatus_mpp_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_mpp, csr_mstatus_mpp_nxt, 0)


reg csr_mstatus_spp;
reg csr_mstatus_spp_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_spp, csr_mstatus_spp_nxt, 0)


reg csr_mstatus_mpie, csr_mstatus_spie;
reg csr_mstatus_mpie_nxt, csr_mstatus_spie_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_mpie, csr_mstatus_mpie_nxt, 0)
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_spie, csr_mstatus_spie_nxt, 0)


reg csr_mstatus_sie;
reg csr_mstatus_mie_nxt, csr_mstatus_sie_nxt;

`DEFINE_CSR_BEHAVIOUR(csr_mstatus_sie, csr_mstatus_sie_nxt, 0)
`DEFINE_CSR_BEHAVIOUR(csr_mstatus_mie, csr_mstatus_mie_nxt, 0)


// Just a scratch bit in MISA, will be used by machine mode to emulate Atomic instruction
reg csr_misa_atomic;
reg csr_misa_atomic_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_misa_atomic, csr_misa_atomic_nxt, 0)

wire [31:0] csr_misa = {2'b01, // MXLEN = 32, only valid value
4'b0000, // Reserved
1'b0, // Z
1'b0, // Y
1'b0, // X
1'b0, // W
1'b0, // V
1'b1, // U - User mode, present
1'b0, // T
1'b1, // S - Supervisor mode, present
1'b0, // R
1'b0, // Q
1'b0, // P
1'b0, // O
1'b0, // N
1'b1, // M - Multiply/Divide, Present
1'b0, // L
1'b0, // K
1'b0, // J
1'b1, // I - RV32I
1'b0, // H
1'b0, // G
1'b0, // F
1'b0, // E
1'b0, // D
1'b0, // C
1'b0, // B
csr_misa_atomic  // A
};

reg [31:0] csr_sscratch;
reg [31:0] csr_sscratch_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_sscratch, csr_sscratch_nxt, 0)


always @* begin
    csr_mscratch_nxt = csr_mscratch;
    
    csr_mcurrent_privilege_nxt = csr_mcurrent_privilege;

    csr_mtvec_nxt = csr_mtvec;

    csr_mstatus_tsr_nxt = csr_mstatus_tsr;
    csr_mstatus_tw_nxt = csr_mstatus_tw;
    csr_mstatus_tvm_nxt = csr_mstatus_tvm;

    csr_mstatus_mxr_nxt = csr_mstatus_mxr;
    csr_mstatus_sum_nxt = csr_mstatus_sum;
    csr_mstatus_mprv_nxt = csr_mstatus_mprv;

    csr_mstatus_mpp_nxt = csr_mstatus_mpp;
    csr_mstatus_spp_nxt = csr_mstatus_spp;

    csr_mstatus_mpie_nxt = csr_mstatus_mpie;
    csr_mstatus_spie_nxt = csr_mstatus_spie;

    csr_mstatus_mie_nxt = csr_mstatus_mie;
    csr_mstatus_sie_nxt = csr_mstatus_sie;

    csr_misa_atomic_nxt = csr_misa_atomic;

    csr_sscratch_nxt = csr_sscratch;

    csr_readdata = 0;
    csr_invalid = 0;
    case(csr_address)
        `DEFINE_COMB_MRO(12'hFC0, {30'h0, csr_mcurrent_privilege})
        `DEFINE_COMB_MRO(12'hF11, MVENDORID)
        `DEFINE_COMB_MRO(12'hF12, MARCHID)
        `DEFINE_COMB_MRO(12'hF13, MIMPID)
        `DEFINE_COMB_MRO(12'hF14, MHARTID)
        12'h300: begin // MSTATUS
            csr_invalid = accesslevel_invalid;
            csr_readdata = {
                        9'h0, // Padding SD, 8 empty bits
                        csr_mstatus_tsr, csr_mstatus_tw, csr_mstatus_tvm, // trap enable bits
                        csr_mstatus_mxr, csr_mstatus_sum, csr_mstatus_mprv, //machine privilege mode
                        2'b00, 2'b00, // xs, fs
                        csr_mstatus_mpp, 2'b00, csr_mstatus_spp, // MPP, 2 bits (reserved by spec), SPP
                        csr_mstatus_mpie, 1'b0, csr_mstatus_spie, 1'b0,
                        csr_mstatus_mie, 1'b0, csr_mstatus_sie, 1'b0};
            if(!csr_invalid && csr_write) begin
                csr_mstatus_tsr_nxt = csr_writedata[22];
                csr_mstatus_tw_nxt = csr_writedata[21];
                csr_mstatus_tvm_nxt = csr_writedata[20];
                csr_mstatus_mxr_nxt = csr_writedata[19];
                csr_mstatus_sum_nxt = csr_writedata[18];
                csr_mstatus_mprv_nxt = csr_writedata[17];

                if(csr_writedata[12:11] != 2'b10)
                    csr_mstatus_mpp_nxt = csr_writedata[12:11];
                csr_mstatus_spp_nxt = csr_writedata[8];

                csr_mstatus_mpie_nxt = csr_writedata[7];
                csr_mstatus_spie_nxt = csr_writedata[5];
                csr_mstatus_mie_nxt = csr_writedata[3];
                csr_mstatus_sie_nxt = csr_writedata[1];
            end
        end
        12'h301: begin // MISA
            csr_readdata = csr_misa;
            csr_invalid = accesslevel_invalid;
            csr_misa_atomic_nxt = (!csr_invalid && csr_write) ? csr_writedata[0] : csr_misa_atomic; 
        end
        12'h302: begin

        end
        12'h305: begin // MTVEC
            csr_invalid = accesslevel_invalid;
            csr_readdata = {csr_mtvec};
            csr_mtvec_nxt = (!accesslevel_invalid) && csr_write ? {csr_writedata[31:2], 2'b00} : csr_mtvec;
        end
        12'h340: begin // MSCRATCH
            csr_invalid = accesslevel_invalid;
            csr_readdata = csr_mscratch;
            csr_mscratch_nxt = (!accesslevel_invalid) && csr_write ? csr_writedata : csr_mscratch;
        end
        12'h140: begin // SSCRATCH
            csr_invalid = accesslevel_invalid;
            csr_readdata = csr_sscratch;
            csr_sscratch_nxt = (!accesslevel_invalid) && csr_write ? csr_writedata : csr_sscratch;
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