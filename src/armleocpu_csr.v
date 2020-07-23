module armleocpu_csr(
    input clk,
    input rst_n,

    // Memory access level check registers
    output reg          csr_satp_mode,
    output reg [21:0]   csr_satp_ppn,

    output reg          csr_mstatus_mprv,
    output reg          csr_mstatus_mxr,
    output reg          csr_mstatus_sum,

    
    output reg [1:0]    csr_mstatus_mpp,

    output reg [1:0]    csr_mcurrent_privilege,

    // Trap registers

    output reg          csr_mstatus_tsr,
    output reg          csr_mstatus_tw,
    output reg          csr_mstatus_tvm,

    // instret increment
    input               instret_incr,


    // Interrupts logic, level sensitive
    input               irq_timer_i,
    input               irq_exti_i,
    input               irq_swi_i,

    // Goes to fetch, then if pending and fetch unit begins new fetch then it will bubble for one cycle
    // and send execute a command to start an interrupt while in bubble cycle
    output  reg         interrupt_pending_csr,

    input      [3:0]    csr_cmd,
    input      [31:0]   csr_exc_cause,
    input      [31:0]   csr_exc_epc,

    output reg [31:0]   csr_next_pc,

    input      [11:0]   csr_address,
    output reg          csr_invalid,
    output reg [31:0]   csr_readdata,
    input      [31:0]   csr_writedata
);

    `include "armleocpu_csr.vh"
    `include "armleocpu_privilege.vh"
    `include "armleocpu_exception.vh"

    // TODO: Output interrupt/mret/sret fetch target


parameter MVENDORID = 32'h0A1AA1E0;
parameter MARCHID = 32'h1;
parameter MIMPID = 32'h1;
parameter MHARTID = 32'h0;

wire csr_write =    csr_cmd == `ARMLEOCPU_CSR_CMD_WRITE ||
                    csr_cmd == `ARMLEOCPU_CSR_CMD_READ_WRITE ||
                    csr_cmd == `ARMLEOCPU_CSR_CMD_READ_SET ||
                    csr_cmd == `ARMLEOCPU_CSR_CMD_READ_CLEAR;
wire  csr_read =  csr_cmd == `ARMLEOCPU_CSR_CMD_READ ||
                    csr_cmd == `ARMLEOCPU_CSR_CMD_READ_WRITE ||
                    csr_cmd == `ARMLEOCPU_CSR_CMD_READ_SET ||
                    csr_cmd == `ARMLEOCPU_CSR_CMD_READ_CLEAR;

wire accesslevel_invalid = (csr_write || csr_read) && (csr_mcurrent_privilege < csr_address[9:8]);
wire write_invalid = (csr_write && (csr_address[11:10] == 2'b11));
// wire address_writable = (csr_address[11:10] != 2'b11);

reg supervisor_user_calculated_sie;

reg [31:0] writedata;
reg [31:0] rmw_readdata; // holds read modify write operations readdata, because it's different for mip, sip


`define DEFINE_CSR_BEHAVIOUR(main_reg, main_reg_nxt, default_val) \
always @(posedge clk) \
    if(!rst_n) \
        main_reg <= default_val; \
    else \
        main_reg <= main_reg_nxt;
`define DEFINE_COMB_RO(address, val) \
    address: begin \
        csr_invalid = accesslevel_invalid || write_invalid; \
        csr_readdata = val; \
        rmw_readdata = csr_readdata; \
    end
`define DEFINE_SCRATCH_CSR_REG_COMB(address, cur, nxt) \
        address: begin \
            csr_invalid = accesslevel_invalid; \
            csr_readdata = cur; \
            rmw_readdata = csr_readdata; \
            if((!accesslevel_invalid) && csr_write) \
                nxt = writedata; \
        end

`define DEFINE_ADDRESS_CSR_REG_COMB(address, cur, nxt) \
        address: begin \
            csr_invalid = accesslevel_invalid; \
            csr_readdata = cur; \
            rmw_readdata = csr_readdata; \
            if((!accesslevel_invalid) && (writedata[1:0] == 0) && csr_write) \
                nxt = writedata; \
        end

`define DEFINE_SCRATCH_CSR(bit_count, cur, nxt, default_val) \
reg [bit_count-1:0] cur; \
reg [bit_count-1:0] nxt; \
always @(posedge clk) \
    if(!rst_n) \
        cur <= default_val; \
    else \
        cur <= nxt;
`define DEFINE_ONE_BIT_CSR(cur, nxt, default_val) \
    reg cur; \
    reg nxt; \
    always @(posedge clk) \
        if(!rst_n) \
            cur <= default_val; \
        else \
            cur <= nxt;

`DEFINE_SCRATCH_CSR(32, csr_mtvec, csr_mtvec_nxt, 0)
`DEFINE_SCRATCH_CSR(32, csr_stvec, csr_stvec_nxt, 0)

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
`DEFINE_ONE_BIT_CSR(csr_mstatus_spp, csr_mstatus_spp_nxt, 0)


`DEFINE_ONE_BIT_CSR(csr_mstatus_mpie, csr_mstatus_mpie_nxt, 0)
`DEFINE_ONE_BIT_CSR(csr_mstatus_spie, csr_mstatus_spie_nxt, 0)

`DEFINE_ONE_BIT_CSR(csr_mstatus_sie, csr_mstatus_sie_nxt, 0)
`DEFINE_ONE_BIT_CSR(csr_mstatus_mie, csr_mstatus_mie_nxt, 0)


// Just a scratch bit in MISA, will be used by machine mode to emulate Atomic instruction
`DEFINE_ONE_BIT_CSR(csr_misa_atomic, csr_misa_atomic_nxt, 0)

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

`DEFINE_SCRATCH_CSR(32, csr_mscratch, csr_mscratch_nxt, 0)

`DEFINE_SCRATCH_CSR(32, csr_sscratch, csr_sscratch_nxt, 0)

`DEFINE_SCRATCH_CSR(32, csr_mepc, csr_mepc_nxt, 0)
`DEFINE_SCRATCH_CSR(32, csr_sepc, csr_sepc_nxt, 0)


`DEFINE_SCRATCH_CSR(32, csr_mcause, csr_mcause_nxt, 0)
`DEFINE_SCRATCH_CSR(32, csr_scause, csr_scause_nxt, 0)

`DEFINE_SCRATCH_CSR(32, csr_mtval, csr_mtval_nxt, 0)
`DEFINE_SCRATCH_CSR(32, csr_stval, csr_stval_nxt, 0)
`DEFINE_SCRATCH_CSR(32, csr_cycle, csr_cycle_nxt, 0)
`DEFINE_SCRATCH_CSR(32, csr_cycleh, csr_cycleh_nxt, 0)
`DEFINE_SCRATCH_CSR(32, csr_instret, csr_instret_nxt, 0)
`DEFINE_SCRATCH_CSR(32, csr_instreth, csr_instreth_nxt, 0)

reg [21:0] csr_satp_ppn_nxt;
reg csr_satp_mode_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_satp_ppn, csr_satp_ppn_nxt, 0)
`DEFINE_CSR_BEHAVIOUR(csr_satp_mode, csr_satp_mode_nxt, 0)

reg [15:0] csr_medeleg;
reg [15:0] csr_medeleg_nxt;
`DEFINE_CSR_BEHAVIOUR(csr_medeleg, csr_medeleg_nxt, 0)

/*
`define CONNECT_WIRE_TO_REG(name, register, bitnum) \
    wire name = register[bitnum];

 // MEDELEG
`CONNECT_WIRE_TO_REG(csr_medeleg_instruction_address_missaligned, csr_medeleg, `EXCEPTION_CODE_INSTRUCTION_ADDRESS_MISSALIGNED)
`CONNECT_WIRE_TO_REG(csr_medeleg_instruction_access_fault, csr_medeleg, `EXCEPTION_CODE_INSTRUCTION_ACCESS_FAULT)
`CONNECT_WIRE_TO_REG(csr_medeleg_illegal_instruction, csr_medeleg, `EXCEPTION_CODE_ILLEGAL_INSTRUCTION)
`CONNECT_WIRE_TO_REG(csr_medeleg_breakpoint, csr_medeleg, `EXCEPTION_CODE_BREAKPOINT)
`CONNECT_WIRE_TO_REG(csr_medeleg_load_address_misaligned, csr_medeleg, `EXCEPTION_CODE_LOAD_ADDRESS_MISALIGNED)
`CONNECT_WIRE_TO_REG(csr_medeleg_load_access_fault, csr_medeleg, `EXCEPTION_CODE_LOAD_ACCESS_FAULT)
`CONNECT_WIRE_TO_REG(csr_medeleg_store_address_misaligned, csr_medeleg, `EXCEPTION_CODE_STORE_ADDRESS_MISALIGNED)
`CONNECT_WIRE_TO_REG(csr_medeleg_store_access_fault, csr_medeleg, `EXCEPTION_CODE_STORE_ACCESS_FAULT)
`CONNECT_WIRE_TO_REG(csr_medeleg_ucall, csr_medeleg, `EXCEPTION_CODE_UCALL)
`CONNECT_WIRE_TO_REG(csr_medeleg_scall, csr_medeleg, `EXCEPTION_CODE_SCALL)
// 10th bit reserved
`CONNECT_WIRE_TO_REG(csr_medeleg_mcall, csr_medeleg, `EXCEPTION_CODE_MCALL)
`CONNECT_WIRE_TO_REG(csr_medeleg_instruction_page_fault, csr_medeleg, `EXCEPTION_CODE_INSTRUCTION_PAGE_FAULT)
`CONNECT_WIRE_TO_REG(csr_medeleg_load_page_fault, csr_medeleg, `EXCEPTION_CODE_LOAD_PAGE_FAULT)
// 13th bit reserved
`CONNECT_WIRE_TO_REG(csr_medeleg_store_page_fault, csr_medeleg, `EXCEPTION_CODE_STORE_PAGE_FAULT)
*/


`DEFINE_SCRATCH_CSR(12, csr_mideleg, csr_mideleg_nxt, 0)
wire csr_mideleg_external_interrupt = csr_mideleg[9];
wire csr_mideleg_timer_interrupt = csr_mideleg[5];
wire csr_mideleg_software_interrupt = csr_mideleg[1];


`DEFINE_ONE_BIT_CSR(csr_mie_meie, csr_mie_meie_nxt, 0)
// 11th bit, active and read/writeable when no mideleg

`DEFINE_ONE_BIT_CSR(csr_mie_seie, csr_mie_seie_nxt, 0)
// 9th bit, active and read/writeable when mideleg



`DEFINE_ONE_BIT_CSR(csr_mie_mtie, csr_mie_mtie_nxt, 0)
// 7th bit, active and read/writeable when no mideleg

`DEFINE_ONE_BIT_CSR(csr_mie_stie, csr_mie_stie_nxt, 0)
// 5th bit, active and read/writeable when mideleg



`DEFINE_ONE_BIT_CSR(csr_mie_msie, csr_mie_msie_nxt, 0)
// 3th bit, active and read/writeable when no mideleg

`DEFINE_ONE_BIT_CSR(csr_mie_ssie, csr_mie_ssie_nxt, 0)
// 1th bit, active and read/writeable when mideleg


`DEFINE_ONE_BIT_CSR(csr_mip_seip, csr_mip_seip_nxt, 0)
// 9th bit, read/write if mideleg, else zero
// when read seip is logical or of this bit and external signal
// when rmw then seip is this bit

`DEFINE_ONE_BIT_CSR(csr_mip_stip, csr_mip_stip_nxt, 0) // 5th bit, read/write if mideleg, else zero

`DEFINE_ONE_BIT_CSR(csr_mip_ssip, csr_mip_ssip_nxt, 0) // 1th bit, read/write if mideleg, else zero


always @* begin
    writedata = 0;
    if(csr_cmd == `ARMLEOCPU_CSR_CMD_READ_WRITE || csr_cmd == `ARMLEOCPU_CSR_CMD_WRITE)
        writedata = csr_writedata;
    if(csr_cmd == `ARMLEOCPU_CSR_CMD_READ_SET)
        writedata = rmw_readdata | csr_writedata;
    if(csr_cmd == `ARMLEOCPU_CSR_CMD_READ_CLEAR)
        writedata = rmw_readdata & ~csr_writedata;
end


reg interrupt_pending_csr;

reg irq_timer_en;
reg irq_exti_en;
reg irq_swi_en;

always @* begin
    irq_timer_en = 0;
    irq_exti_en = 0;
    irq_swi_en = 0;

    

    
    if(csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_MACHINE) begin
        if(csr_mstatus_mie) begin
            if(csr_mie_meie & !csr_mideleg_external_interrupt)
                irq_exti_en = 1;
            if(csr_mie_mtie & !csr_mideleg_timer_interrupt)
                irq_timer_en = 1;
            if(csr_mie_msie & !csr_mideleg_software_interrupt)
                irq_swi_en = 1;
        end
    end else if(csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR ||
                csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_USER) begin
        
        supervisor_user_calculated_sie = 
            (csr_mcurrent_privilege == `ARMLEOCPU_PRIVILEGE_SUPERVISOR) ? csr_mstatus_sie : 1;
        
        // EXTI
        if(csr_mie_meie & !csr_mideleg_external_interrupt)
            irq_exti_en = 1;
        else if(csr_mideleg_external_interrupt)
            if(supervisor_user_calculated_sie & csr_mie_seie)
                irq_exti_en = 1;

        // TIMER
        if(csr_mie_mtie & !csr_mideleg_timer_interrupt)
            irq_timer_en = 1;
        else if(csr_mideleg_timer_interrupt)
            if(supervisor_user_calculated_sie & csr_mie_stie)
                irq_timer_en = 1;

        // SWI
        if(csr_mie_msie & !csr_mideleg_software_interrupt)
            irq_swi_en = 1;
        else if(csr_mideleg_software_interrupt)
            if(supervisor_user_calculated_sie & csr_mie_ssie)
                irq_swi_en = 1;
    end


    csr2f_timer_pending = irq_timer_en &
        (csr_mideleg_timer_interrupt ? csr_mip_stip || irq_timer_i : irq_timer_i);
    csr2f_exti_pending = irq_exti_en &
            (csr_mideleg_external_interrupt ? csr_mip_seip || irq_exti_i : irq_exti_i);
    csr2f_swi_pending = irq_swi_en & 
            (csr_mideleg_software_interrupt ? csr_mip_ssip || irq_swi_i : irq_swi_i);

    interrupt_pending_csr = csr2f_timer_pending || csr2f_exti_pending || csr2f_swi_pending;

    csr_readdata = 0;
    csr_invalid = 0;
    rmw_readdata = 0;

    csr_mcurrent_privilege_nxt = csr_mcurrent_privilege;

    csr_mtvec_nxt = csr_mtvec;
    csr_stvec_nxt = csr_stvec;

    csr_mcurrent_privilege_nxt = csr_mcurrent_privilege;

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
    csr_mscratch_nxt = csr_mscratch;
    
    csr_mepc_nxt = csr_mepc;
    csr_sepc_nxt = csr_sepc;

    csr_mcause_nxt = csr_mcause;
    csr_scause_nxt = csr_scause;

    csr_mtval_nxt = csr_mtval;
    csr_stval_nxt = csr_stval;
    
    {csr_cycleh_nxt, csr_cycle_nxt} = {csr_cycleh, csr_cycle} + 1;

    if(instret_incr)
        {csr_instreth_nxt, csr_instret_nxt} = {csr_instreth, csr_instret} + 1;
    else
        {csr_instreth_nxt, csr_instret_nxt} = {csr_instreth, csr_instret};
    
    csr_satp_ppn_nxt = csr_satp_ppn;
    csr_satp_mode_nxt = csr_satp_mode;

    csr_medeleg_nxt = csr_medeleg;
    csr_mideleg_nxt = csr_mideleg;
    // TODO: nxt values defaults for: medeleg, mideleg
    
    {csr_mie_meie_nxt, csr_mie_seie_nxt} = {csr_mie_meie, csr_mie_seie};
    {csr_mie_mtie_nxt, csr_mie_stie_nxt} = {csr_mie_mtie, csr_mie_stie};
    {csr_mie_msie_nxt, csr_mie_ssie_nxt} = {csr_mie_msie, csr_mie_ssie};

    {csr_mip_seip_nxt} = {csr_mip_seip};
    {csr_mip_stip_nxt} = {csr_mip_stip};
    {csr_mip_ssip_nxt} = {csr_mip_ssip};
    

    case(csr_address)
        `DEFINE_COMB_RO(12'hFC0, {30'h0, csr_mcurrent_privilege})
        `DEFINE_COMB_RO(12'hF11, MVENDORID)
        `DEFINE_COMB_RO(12'hF12, MARCHID)
        `DEFINE_COMB_RO(12'hF13, MIMPID)
        `DEFINE_COMB_RO(12'hF14, MHARTID)
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
            rmw_readdata = csr_readdata;
            if(!csr_invalid && csr_write) begin
                csr_mstatus_tsr_nxt = writedata[22];
                csr_mstatus_tw_nxt = writedata[21];
                csr_mstatus_tvm_nxt = writedata[20];
                csr_mstatus_mxr_nxt = writedata[19];
                csr_mstatus_sum_nxt = writedata[18];
                csr_mstatus_mprv_nxt = writedata[17];

                if(writedata[12:11] != 2'b10)
                    csr_mstatus_mpp_nxt = writedata[12:11];
                csr_mstatus_spp_nxt = writedata[8];

                csr_mstatus_mpie_nxt = writedata[7];
                csr_mstatus_spie_nxt = writedata[5];
                csr_mstatus_mie_nxt = writedata[3];
                csr_mstatus_sie_nxt = writedata[1];
            end
        end
        12'h301: begin // MISA
            csr_readdata = csr_misa;
            csr_invalid = accesslevel_invalid;
            csr_misa_atomic_nxt = (!csr_invalid && csr_write) ? writedata[0] : csr_misa_atomic; 
            rmw_readdata = csr_readdata;
        end
        /*12'h302: begin

        end*/
        `DEFINE_ADDRESS_CSR_REG_COMB(12'h305, csr_mtvec, csr_mtvec_nxt)
        `DEFINE_SCRATCH_CSR_REG_COMB(12'h340, csr_mscratch, csr_mscratch_nxt)
        `DEFINE_ADDRESS_CSR_REG_COMB(12'h341, csr_mepc, csr_mepc_nxt)
        `DEFINE_SCRATCH_CSR_REG_COMB(12'h342, csr_mcause, csr_mcause_nxt)
        `DEFINE_SCRATCH_CSR_REG_COMB(12'h343, csr_mtval, csr_mtval_nxt)
        
        // Supervisor
        `DEFINE_ADDRESS_CSR_REG_COMB(12'h105, csr_stvec, csr_stvec_nxt)
        `DEFINE_SCRATCH_CSR_REG_COMB(12'h140, csr_sscratch, csr_sscratch_nxt)
        `DEFINE_ADDRESS_CSR_REG_COMB(12'h141, csr_sepc, csr_sepc_nxt)
        `DEFINE_SCRATCH_CSR_REG_COMB(12'h142, csr_scause, csr_scause_nxt)
        `DEFINE_SCRATCH_CSR_REG_COMB(12'h143, csr_stval, csr_stval_nxt)


        `DEFINE_SCRATCH_CSR_REG_COMB(12'hB00, csr_cycle, csr_cycle_nxt)
        `DEFINE_SCRATCH_CSR_REG_COMB(12'hB80, csr_cycleh, csr_cycleh_nxt)

        `DEFINE_SCRATCH_CSR_REG_COMB(12'hB02, csr_instret, csr_instret_nxt)
        `DEFINE_SCRATCH_CSR_REG_COMB(12'hB82, csr_instreth, csr_instreth_nxt)
        12'h180: begin // SATP
            csr_readdata = {csr_satp_mode, 9'h0, csr_satp_ppn};
            csr_invalid = accesslevel_invalid;
            rmw_readdata = csr_readdata;
            if(!csr_invalid && csr_write) begin
                csr_satp_mode_nxt = writedata[31];
                csr_satp_ppn_nxt = writedata[21:0];
            end
        end
        12'h302: begin // MEDELEG
            csr_invalid = accesslevel_invalid;
            csr_readdata = {16'h0, csr_medeleg};
            rmw_readdata = csr_readdata;
            if(csr_write && !csr_invalid)
                csr_medeleg_nxt = writedata[15:0];
            csr_medeleg_nxt[10] = 0;
            csr_medeleg_nxt[14] = 0;
        end

        12'h303: begin // MIDELEG
            csr_invalid = accesslevel_invalid;
            csr_readdata = {20'h0, csr_mideleg};
            rmw_readdata = csr_readdata;
            if(csr_write && !csr_invalid) begin
                csr_mideleg_nxt[9] = writedata[9];
                csr_mideleg_nxt[5] = writedata[5];
                csr_mideleg_nxt[1] = writedata[1];
            end
        end
        
        12'h304: begin // MIE
            csr_invalid = accesslevel_invalid;

            csr_readdata = 0;

            csr_readdata[11] = csr_mie_meie;
            csr_readdata [9] = csr_mie_seie;

            csr_readdata [7] = csr_mie_mtie;
            csr_readdata [5] = csr_mie_stie;

            csr_readdata [3] = csr_mie_msie;
            csr_readdata [1] = csr_mie_ssie;

            rmw_readdata = csr_readdata;
            if(csr_write && !csr_invalid) begin
                csr_mie_meie_nxt = writedata[11];
                csr_mie_seie_nxt = writedata [9];
                csr_mie_mtie_nxt = writedata [7];
                csr_mie_stie_nxt = writedata [5];
                csr_mie_msie_nxt = writedata [3];
                csr_mie_ssie_nxt = writedata [1];
            end
        end
        12'h104: begin // SIE
            csr_invalid = accesslevel_invalid;
            csr_readdata = 0;
            csr_readdata [9] = csr_mie_seie;

            csr_readdata [5] = csr_mie_stie;

            csr_readdata [1] = csr_mie_ssie;

            rmw_readdata = csr_readdata;
            if(!csr_invalid && csr_write) begin
                csr_mie_seie_nxt = writedata [9];
                csr_mie_stie_nxt = writedata [5];
                csr_mie_ssie_nxt = writedata [1];
            end
        end
        12'h100: begin // SSTATUS
            csr_invalid = accesslevel_invalid;
            csr_readdata = {
                        9'h0, // Padding SD, 8 empty bits
                        3'b000, // trap enable bits
                        csr_mstatus_mxr, csr_mstatus_sum, 1'b0, //mxr, sum, mprv
                        2'b00, 2'b00, // xs, fs
                        2'b00, 2'b00, csr_mstatus_spp, // MPP, 2 bits (reserved by spec), SPP
                        1'b0, 1'b0, csr_mstatus_spie, 1'b0,
                        1'b0, 1'b0, csr_mstatus_sie, 1'b0};
            rmw_readdata = csr_readdata;
            if(!csr_invalid && csr_write) begin
                csr_mstatus_mxr_nxt = writedata[19];
                csr_mstatus_sum_nxt = writedata[18];
                csr_mstatus_spp_nxt = writedata[8];
                csr_mstatus_spie_nxt = writedata[5];
                csr_mstatus_sie_nxt = writedata[1];
            end
        end
        12'h344: begin // MIP
            csr_invalid = accesslevel_invalid;
            rmw_readdata = 0;

            // EXTI
            // 11th bit, read only
            rmw_readdata[11] = irq_exti_i;

            // s*ip bit, read/write if mideleg, else zero
            // when read seip is logical or of this bit and external signal
            // when rmw then seip is this bit
            if(csr_mideleg_external_interrupt)
                rmw_readdata[9] = csr_mip_seip;
            

            // TIMER
            rmw_readdata[7] = irq_timer_i;
            
            if(csr_mideleg_timer_interrupt)
                rmw_readdata[5] = csr_mip_stip;
            
            // SWI
            rmw_readdata[3] = irq_swi_i;
            
            if(csr_mideleg_software_interrupt)
                rmw_readdata[1] = csr_mip_ssip;


            csr_readdata = rmw_readdata;

            // s*ip bit, read/write if mideleg, else zero
            // when read seip is logical or of this bit and external signal
            // when rmw then seip is this bit
            if(csr_mideleg_external_interrupt)
                csr_readdata[9] = csr_mip_seip || irq_exti_i;
            if(csr_mideleg_timer_interrupt)
                csr_readdata[5] = csr_mip_stip || irq_timer_i;
            if(csr_mideleg_software_interrupt)
                csr_readdata[1] = csr_mip_ssip || irq_swi_i;
            

            if(!csr_invalid && csr_write) begin
                // csr_mip_m*ip is read only

                if(csr_mideleg_external_interrupt)
                    csr_mip_seip_nxt = writedata[9];
                if(csr_mideleg_timer_interrupt)
                    csr_mip_stip_nxt = writedata[5];
                if(csr_mideleg_software_interrupt)
                    csr_mip_ssip_nxt = writedata[1];
            end
        end
        12'h144: begin // SIP
            csr_invalid = accesslevel_invalid;
            rmw_readdata = 0;

            // s*ip bit, read/write if mideleg, else zero
            // when read seip is logical or of this bit and external signal
            // when rmw then seip is this bit
            if(csr_mideleg_external_interrupt)
                rmw_readdata[9] = csr_mip_seip;
            
            if(csr_mideleg_timer_interrupt)
                rmw_readdata[5] = csr_mip_stip;
            
            if(csr_mideleg_software_interrupt)
                rmw_readdata[1] = csr_mip_ssip;


            csr_readdata = rmw_readdata;

            // s*ip bit, read/write if mideleg, else zero
            // when read seip is logical or of this bit and external signal
            // when rmw then seip is this bit
            if(csr_mideleg_external_interrupt)
                csr_readdata[9] = csr_mip_seip || irq_exti_i;
            if(csr_mideleg_timer_interrupt)
                csr_readdata[5] = csr_mip_stip || irq_timer_i;
            if(csr_mideleg_software_interrupt)
                csr_readdata[1] = csr_mip_ssip || irq_swi_i;
            
            if(!csr_invalid && csr_write) begin
                // csr_mip_m*ip is read only

                if(csr_mideleg_external_interrupt)
                    csr_mip_seip_nxt = writedata[9];
                if(csr_mideleg_timer_interrupt)
                    csr_mip_stip_nxt = writedata[5];
                if(csr_mideleg_software_interrupt)
                    csr_mip_ssip_nxt = writedata[1];
            end
        end


        default: begin
            csr_invalid = csr_read || csr_write;
        end
    endcase
    
    
    if(csr_cmd == `ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN) begin
        // TODO: Implement interrupt begin, don't forget about medeleg
    end else if(csr_cmd == `ARMLEOCPU_CSR_CMD_MRET) begin
        // TODO: Implement MRET
    end else if(csr_cmd == `ARMLEOCPU_CSR_CMD_SRET) begin
        // TODO: Implement SRET
    end
    
    // TODO: Implement mcounteren
    // TODO: scounteren
    // TODO: supervisor timers
    // TODO: user timers
end

// TODO: Do logging

endmodule