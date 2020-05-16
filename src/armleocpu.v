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
    input        [31:0]     dbg_arg1,
    input        [31:0]     dbg_arg2,
    output       [31:0]     dbg_result,
    output                  dbg_mode,
    output                  dbg_done
);
/*
// Debug signals
wire        dbg_mode;
wire [4:0]  dbg_rs1_addr;
wire [4:0]  dbg_rd_addr;
wire        dbg_rd_write;
wire [31:0] dbg_rd_wdata;


// regfile signals
wire [4:0]      ex_rs1_addr;
wire [4:0]      ex_rd_addr;
wire [31:0]     ex_rd_wdata;
wire            ex_rd_write;

wire [4:0]      rs2_addr;

wire [4:0]      rs1_addr = dbg_mode ? dbg_rs1_addr : ex_rs1_addr;

wire [31:0]     rs1_rdata;
wire [31:0]     rs2_rdata;

wire [4:0]      rd_addr = dbg_mode ? dbg_rd_addr : ex_rd_addr;
wire [31:0]     rd_wdata = dbg_mode ? dbg_rd_wdata : ex_rd_wdata;
wire            rd_write = dbg_mode ? dbg_rd_write : ex_rd_write;

// D-Cache signals, Multiplex to debug if dbg_mode, else multiplex to execute
wire  [3:0]     dc_response;
wire            dc_reset_done;
wire  [3:0]     dc_cmd;
wire [31:0]     dc_address;
wire  [2:0]     dc_load_type;
wire [31:0]     dc_load_data;
wire  [1:0]     dc_store_type;
wire [31:0]     dc_store_data;

// I-Cache signals
wire  [3:0]     dc_response;
wire            dc_reset_done;
wire  [3:0]     dc_cmd;
wire [31:0]     dc_address;
wire  [2:0]     dc_load_type;
wire [31:0]     dc_load_data;
wire  [1:0]     dc_store_type;
wire [31:0]     dc_store_data;

// CSR  Signals
wire            csr_satp_mode;
wire            csr_satp_ppn;

wire            csr_mstatus_mprv;
wire            csr_mstatus_mxr;
wire            csr_mstatus_sum;
wire  [1:0]     csr_mstatus_mpp;
wire  [1:0]     csr_mcurrent_privilege;

wire [31:0]     csr_mtvec;


// Fetch signals
wire [1:0]      fetch_c_csr_mstatus_mpp;
wire [1:0]      fetch_c_csr_mcurrent_privilege;

// debug instance

// D-Cache

armleocpu_cache dcache(
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

    .csr_satp_mode          (csr_satp_mode),
    .csr_satp_ppn           (csr_satp_ppn),
    .csr_mstatus_mprv       (csr_mstatus_mprv),
    .csr_mstatus_mxr        (csr_mstatus_mxr),
    .csr_mstatus_sum        (csr_mstatus_sum),
    .csr_mstatus_mpp        (csr_mstatus_mpp),
    .csr_mcurrent_privilege (csr_mcurrent_privilege),

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

armleocpu_cache icache(
    .clk                    (clk),
    .rst_n                  (rst_n),

    .c_response             (ic_response),
    .c_reset_done           (ic_reset_done),

    .c_cmd                  (ic_cmd),
    .c_address              (ic_address),
    .c_load_type            (ic_load_type),
    .c_load_data            (ic_load_data),
    .c_store_type           (0),
    .c_store_data           (0),

    .csr_satp_mode          (csr_satp_mode),
    .csr_satp_ppn           (csr_satp_ppn),

    .csr_mstatus_mprv       (csr_mstatus_mprv),
    .csr_mstatus_mxr        (csr_mstatus_mxr),
    .csr_mstatus_sum        (csr_mstatus_sum),

    .csr_mstatus_mpp        (csr_mstatus_mpp),
    .csr_mcurrent_privilege (csr_mcurrent_privilege),
    

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

// Fetch
armleocpu_fetch fetch(
    .clk                    (clk),
    .rst_n                  (rst_n),

    .mtvec                  (csr_mtvec),

    .dbg_request            (dbg_request),

    .dbg_mode               (dbg_mode),
    .dbg_done               (fetch_dbg_done),

    .c_response             (ic_response),
    .c_reset_done           (dc_reset_done && ic_reset_done),
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
*/

endmodule