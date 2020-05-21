module armleocpu(
    input clk,
    input rst_n,

    output                  d_transaction,
    output       [2:0]      d_cmd,
    input                   d_transaction_done,
    input        [2:0]      d_transaction_response,
    output       [33:0]     d_address,
    output       [3:0]      d_burstcount,
    output       [31:0]     d_wdata,
    output       [3:0]      d_wbyte_enable,
    input        [31:0]     d_rdata,

    output                  i_transaction,
    output       [2:0]      i_cmd,
    input                   i_transaction_done,
    input        [2:0]      i_transaction_response,
    output       [33:0]     i_address,
    output       [3:0]      i_burstcount,
    output       [31:0]     i_wdata,
    output       [3:0]      i_wbyte_enable,
    input        [31:0]     i_rdata,

    input                   dbg_request,
    input        [3:0]      dbg_cmd,
    /*input        [31:0]     dbg_arg1,
    input        [31:0]     dbg_arg2,
    output       [31:0]     dbg_result,*/
    output                  dbg_mode,
    output                  dbg_done
);

parameter RESET_VECTOR = 32'h0000_0000;

parameter DCACHE_WAYS_W = 1;

parameter DCACHE_TLB_ENTRIES_W = 4;
parameter DCACHE_TLB_WAYS_W = 1;

parameter DCACHE_BYPASS_ENABLED = 1;

parameter ICACHE_WAYS_W = 1;

parameter ICACHE_TLB_ENTRIES_W = 4;
parameter ICACHE_TLB_WAYS_W = 1;

parameter ICACHE_BYPASS_ENABLED = 1;


`include "ld_type.inc"

// Debug signals
wire [4:0]  dbg_rs1_addr;
wire [4:0]  dbg_rd_addr;
wire        dbg_rd_write = dbg_cmd == 1;
wire [31:0] dbg_rd_wdata;
wire        dbg_set_pc = dbg_cmd == 2;
wire        dbg_exit_request = dbg_cmd == 3;
wire        dbg_fetch_cmd_done;
wire [31:0] dbg_pc;

// regfile signals
wire [4:0]      ex_rs1_addr;
wire [4:0]      ex_rd_addr;
wire [31:0]     ex_rd_wdata;
wire            ex_rd_write;

wire [4:0]      rs2_addr;

wire [4:0]      rs1_addr = /*dbg_mode ? dbg_rs1_addr : */ex_rs1_addr;

wire [31:0]     rs1_rdata;
wire [31:0]     rs2_rdata;

wire [4:0]      rd_addr = /*dbg_mode ? dbg_rd_addr : */ex_rd_addr;
wire [31:0]     rd_wdata = /*dbg_mode ? dbg_rd_wdata : */ex_rd_wdata;
wire            rd_write = /*dbg_mode ? dbg_rd_write : */ex_rd_write;

// D-Cache signals, Multiplex to debug if dbg_mode, else multiplex to execute
/* verilator lint_off UNOPTFLAT */
wire  [3:0]     dc_response;
/* verilator lint_on UNOPTFLAT */
wire            dc_reset_done;
wire  [3:0]     dc_cmd;
wire [31:0]     dc_address;
wire  [2:0]     dc_load_type;
wire [31:0]     dc_load_data;
wire  [1:0]     dc_store_type;
wire [31:0]     dc_store_data;

// I-Cache signals
wire  [3:0]     ic_response;
wire            ic_reset_done;
wire  [3:0]     ic_cmd;
wire [31:0]     ic_address;
wire  [2:0]     ic_load_type;
wire [31:0]     ic_load_data;

// CSR  Signals
wire            csr_satp_mode;
wire            csr_satp_ppn;

wire            csr_mstatus_mprv;
wire            csr_mstatus_mxr;
wire            csr_mstatus_sum;
wire  [1:0]     csr_mstatus_mpp;
wire  [1:0]     csr_mcurrent_privilege;

wire            csr_mstatus_tsr;
wire            csr_mstatus_tw;
wire            csr_mstatus_tvm;

wire [31:0]     csr_mtvec;

wire  [2:0]     csr_cmd;
wire [11:0]     csr_address;
wire [31:0]     csr_writedata;

wire  [1:0]     csr_exc_cmd;
wire [31:0]     csr_exc_cause;
wire [31:0]     csr_exc_epc;


// F2E
wire [31:0]     f2e_instr;
wire [31:0]     f2e_pc;
/* verilator lint_off UNOPTFLAT */
wire            f2e_exc_start;
wire [31:0]     f2e_cause;
/* verilator lint_on UNOPTFLAT */

// E2F
wire            e2f_ready;
wire            e2f_exc_start;
wire            e2f_exc_return;
wire [31:0]     e2f_exc_epc;
wire            e2f_flush;
wire            e2f_branchtaken;
wire [31:0]     e2f_branchtarget;

// IRQs
wire            irq_timer;
wire            irq_exti;

wire            e2debug_machine_ebreak;

// debug instance


// D-Cache

armleocpu_cache #(
    .WAYS_W(DCACHE_WAYS_W),
    .TLB_ENTRIES_W(DCACHE_TLB_ENTRIES_W),
    .TLB_WAYS_W(DCACHE_TLB_WAYS_W),
    .BYPASS_ENABLED(DCACHE_BYPASS_ENABLED)
) dcache(
    .clk                    (clk),
    .rst_n                  (rst_n),

    .c_response             (dc_response),
    .c_reset_done           (dc_reset_done),

    .c_cmd                  (dc_cmd),
    .c_address              (dc_address),
    .c_load_type            (dc_load_type),
    .c_load_data            (dc_load_data),
    .c_store_type           (dc_store_type),
    .c_store_data           (dc_store_data),
/*
    .csr_satp_mode          (csr_satp_mode),
    .csr_satp_ppn           (csr_satp_ppn),
    .csr_mstatus_mprv       (csr_mstatus_mprv),
    .csr_mstatus_mxr        (csr_mstatus_mxr),
    .csr_mstatus_sum        (csr_mstatus_sum),
    .csr_mstatus_mpp        (csr_mstatus_mpp),
    .csr_mcurrent_privilege (csr_mcurrent_privilege),
*/
    .csr_satp_mode          (0),
    .csr_satp_ppn           (0),
    .csr_mstatus_mprv       (0),
    .csr_mstatus_mxr        (0),
    .csr_mstatus_sum        (0),
    .csr_mstatus_mpp        (0),
    .csr_mcurrent_privilege (3),

    .m_transaction          (d_transaction),
    .m_cmd                  (d_cmd),
    .m_transaction_done     (d_transaction_done),
    .m_transaction_response (d_transaction_response),
    .m_address              (d_address),
    .m_burstcount           (d_burstcount),
    .m_wdata                (d_wdata),
    .m_wbyte_enable         (d_wbyte_enable),
    .m_rdata                (d_rdata)
);

// I-Cache

armleocpu_cache #(
    .WAYS_W(ICACHE_WAYS_W),
    .TLB_ENTRIES_W(ICACHE_TLB_ENTRIES_W),
    .TLB_WAYS_W(ICACHE_TLB_WAYS_W),
    .BYPASS_ENABLED(ICACHE_BYPASS_ENABLED)
) icache(
    .clk                    (clk),
    .rst_n                  (rst_n),

    .c_response             (ic_response),
    .c_reset_done           (ic_reset_done),

    .c_cmd                  (ic_cmd),
    .c_address              (ic_address),
    .c_load_type            (`LOAD_WORD),
    .c_load_data            (ic_load_data),
    .c_store_type           (0),
    .c_store_data           (0),

    /*
    .csr_satp_mode          (csr_satp_mode),
    .csr_satp_ppn           (csr_satp_ppn),

    .csr_mstatus_mprv       (csr_mstatus_mprv),
    .csr_mstatus_mxr        (csr_mstatus_mxr),
    .csr_mstatus_sum        (csr_mstatus_sum),

    .csr_mstatus_mpp        (csr_mstatus_mpp),
    .csr_mcurrent_privilege (csr_mcurrent_privilege),
    */

    .csr_satp_mode          (0),
    .csr_satp_ppn           (0),
    .csr_mstatus_mprv       (0),
    .csr_mstatus_mxr        (0),
    .csr_mstatus_sum        (0),
    .csr_mstatus_mpp        (0),
    .csr_mcurrent_privilege (3),

    .m_transaction          (i_transaction),
    .m_cmd                  (i_cmd),
    .m_transaction_done     (i_transaction_done),
    .m_transaction_response (i_transaction_response),
    .m_address              (i_address),
    .m_burstcount           (i_burstcount),
    .m_wdata                (i_wdata),
    .m_wbyte_enable         (i_wbyte_enable),
    .m_rdata                (i_rdata)
);

// Execute
armleocpu_execute execute(
    .clk                    (clk),
    .rst_n                  (rst_n),


    .e2debug_machine_ebreak (e2debug_machine_ebreak),

    .c_response             (dc_response),
    .c_reset_done           (dc_reset_done && ic_reset_done),

    .c_cmd                  (dc_cmd),
    .c_address              (dc_address),

    .c_load_data            (dc_load_data),
    .c_load_type            (dc_load_type),
    .c_store_type           (dc_store_type),
    .c_store_data           (dc_store_data),

    // CSR Interface for exceptions
    .csr_exc_cmd            (csr_exc_cmd),
    .csr_exc_cause          (csr_exc_cause),
    .csr_exc_epc            (csr_exc_epc),

    // CSR Interface for csr class instructions,
    .csr_cmd                (csr_cmd),
    .csr_address            (csr_address),
    .csr_invalid            (0),
    .csr_readdata           (0),
    .csr_writedata          (csr_writedata),

    // CSR Interface for csr read
    .csr_mcurrent_privilege (3),
    .csr_mepc               (0),
    .csr_sepc               (0),
    .csr_mstatus_tsr        (0),
    .csr_mstatus_tvm        (0),
    .csr_mstatus_tw         (0),
    
    // Regfile
    .rs1_addr               (ex_rs1_addr),
    .rs1_data               (rs1_rdata),

    .rs2_addr               (rs2_addr),
    .rs2_data               (rs2_rdata),

    .rd_addr                (ex_rd_addr),
    .rd_wdata               (ex_rd_wdata),
    .rd_write               (ex_rd_write),

    // from fetch
    .f2e_instr              (f2e_instr),
    .f2e_pc                 (f2e_pc),
    .f2e_exc_start          (f2e_exc_start),
    .f2e_cause              (f2e_cause),

    // to fetch
    .e2f_ready              (e2f_ready),
    .e2f_exc_start          (e2f_exc_start),
    .e2f_exc_return         (e2f_exc_return),
    .e2f_exc_epc            (e2f_exc_epc),
    .e2f_flush              (e2f_flush),
    .e2f_branchtaken        (e2f_branchtaken),
    .e2f_branchtarget       (e2f_branchtarget)
);

// Fetch
armleocpu_fetch #(RESET_VECTOR) fetch(
    .clk                    (clk),
    .rst_n                  (rst_n),

    .csr_mtvec              (csr_mtvec),

    .dbg_request            (dbg_request),
    
    .dbg_set_pc             (dbg_set_pc),
    .dbg_pc                 (dbg_pc),
    .dbg_exit_request       (dbg_exit_request),


    .dbg_mode               (dbg_mode),
    .dbg_done               (dbg_fetch_cmd_done),

    .c_response             (ic_response),
    .c_reset_done           (dc_reset_done && ic_reset_done),

    .c_cmd                  (ic_cmd),
    .c_address              (ic_address),
    .c_load_data            (ic_load_data),
    .irq_timer              (irq_timer),
    .irq_exti               (irq_exti),

    // to execute
    .f2e_instr              (f2e_instr),
    .f2e_pc                 (f2e_pc),
    .f2e_exc_start          (f2e_exc_start),
    .f2e_cause              (f2e_cause),

    // from execute
    .e2f_ready              (e2f_ready),
    .e2f_exc_start          (e2f_exc_start),
    .e2f_exc_return         (e2f_exc_return),
    .e2f_exc_epc            (e2f_exc_epc),
    .e2f_flush              (e2f_flush),
    .e2f_branchtaken        (e2f_branchtaken),
    .e2f_branchtarget       (e2f_branchtarget)
);

// CSR

// Regfile
armleocpu_regfile regfile(
    .clk(clk),
    .rst_n(rst_n),

    .rs1_addr(rs1_addr),
    .rs1_rdata(rs1_rdata),
	
	.rs2_addr(rs2_addr),
	.rs2_rdata(rs2_rdata),
	
	.rd_addr(rd_addr),
	.rd_wdata(rd_wdata),
	.rd_write(rd_write)
);


endmodule