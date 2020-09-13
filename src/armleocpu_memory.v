module armleocpu_memory (
    input clk,
    input rst_n,

    input      [1:0]                    csr_mcurrent_privilege,
    // Execute <-> memory
    input                               e2m_instr_valid,
    input      [31:0]                   e2m_instr,
    input      [DECODE_IS_WIDTH-1:0]    e2m_instr_decode,
    input      [31:0]                   e2m_pc,
    input      [31:0]                   e2m_memory_address,

    // Memory -> writeback
    output reg                          m2wb_rd_write,
    output      [4:0]                   m2wb_rd_waddr,
    output reg [31:0]                   m2wb_rd_wdata,
    


    // Pipeline I/O
    output                              stall_o,
    output                              kill,
    output reg [3:0]                    m2p_cmd,
    output reg [31:0]                   m2p_exception,

    // Memory <-> Cache / AXI4 Bus in no cache configuration
    // AW Bus
    output	reg  				        M_AXI_AWVALID,
    input	wire				        M_AXI_AWREADY,
    output	reg 	[31:0]	            M_AXI_AWADDR,
    output	wire	[7:0]			    M_AXI_AWLEN,
    output	wire	[2:0]			    M_AXI_AWSIZE,
    output	wire	[1:0]			    M_AXI_AWBURST,
    output	wire				        M_AXI_AWLOCK,
    output	wire	[2:0]			    M_AXI_AWPROT, // Read documentation about this signal value represantation
    // W Bus
    output	wire				        M_AXI_WVALID,
    input	wire				        M_AXI_WREADY,
    output	wire	[31:0]	            M_AXI_WDATA,
    output	wire	[3:0]               M_AXI_WSTRB,
    output	wire				        M_AXI_WLAST,
    
    // B-bus
    input	wire				        M_AXI_BVALID,
    output	wire				        M_AXI_BREADY,
    input	wire	[1:0]			    M_AXI_BRESP,
    input   wire                        M_AXI_BUSER, // [0] shows pagefault, BRESP should be 2'b11

    // AR-Bus
    output	reg				            M_AXI_ARVALID,
    input	wire				        M_AXI_ARREADY,
    output	reg	[31:0]	                M_AXI_ARADDR,
    output	reg	[7:0]			        M_AXI_ARLEN,
    output	reg	[2:0]			        M_AXI_ARSIZE,
    output	reg	[1:0]			        M_AXI_ARBURST,
    output	reg				            M_AXI_ARLOCK,
    output	reg	[2:0]			        M_AXI_ARPROT, // Read documentation about this signal value represantation

    // R-Bus
    input	wire				        M_AXI_RVALID,
    output	reg				            M_AXI_RREADY,
    input	wire	[31:0]	            M_AXI_RDATA,
    input	wire				        M_AXI_RLAST,
    input	wire	[1:0]			    M_AXI_RRESP,
    input   wire                        M_AXI_RUSER // [0] shows pagefault RRESP should be 2'b11
);
/*
`ifdef FORMAL
    always @(posedge clk) begin
        if($past(stall_o) && stall_o && $past(e2m_instr_valid))
            assume($stable(e2m_instr) && e2m_instr_valid);
    end
    
`endif
*/

`include "armleocpu_includes.vh"

assign M_AXI_AWSIZE = 3'b010;
assign M_AXI_ARSIZE = 3'b010;
assign M_AXI_ARLEN = 0;
assign M_AXI_AWLEN = 0;
assign M_AXI_WLAST = 1; // Only one word is possible
assign M_AXI_AWBURST = 2'b01; // INCR
assign M_AXI_ARBURST = 2'b01; // INCR

assign M_AXI_ARADDR = e2m_memory_address;
assign M_AXI_AWADDR = e2m_memory_address;

assign m2wb_rd_waddr = e2m_instr[11:7];


wire [2:0]  funct3 = e2m_instr[14:12];
task exception_begin;
input [31:0] exception;
begin
    m2p_exception = exception;
    m2p_cmd = `M2P_START_EXCEPTION;
    kill = 1;
    stall_o = 0;
end
endtask

always @* begin
    
    M_AXI_ARVALID = 0;
    M_AXI_RREADY = 0;
    M_AXI_AWVALID = 0;
    M_AXI_WREADY = 0;
    M_AXI_BREADY = 0;

    address_done_nxt = address_done;
    data_done_nxt = data_done;

    rd_write = 0;
    
    m2p_cmd = ;
    m2p_exception = ;
    kill = 0;
    stall_o = 0;
    if(e2m_instr_valid) begin
        if(e2m_instr_complete) begin
            if(e2m_instr_decode[`DECODE_IS_LOAD]) begin
                if(loadgen_missaligned)
                    exception_begin(`EXCEPTION_CODE_LOAD_ADDRESS_MISSALIGNED);
                else if(loadgen_unknowntype)
                    exception_begin(`EXCEPTION_CODE_ILLEGAL_INSTRUCTION);
                else begin
                    stall_o = 1;
                    M_AXI_ARVALID = !address_done;
                    M_AXI_RREADY = 0;
                    if(M_AXI_ARVALID && M_AXI_ARREADY) begin
                        address_done_nxt = 1;
                    end

                    if(((M_AXI_ARREADY) || address_done) && (M_AXI_RVALID) begin
                        M_AXI_RREADY = 1;
                        address_done_nxt = 0;
                        stall_o = 0;
                        if(M_AXI_RRESP != 0) begin
                            if(M_AXI_RUSER[0]) begin // Load Pagefault
                                exception_begin(`EXCEPTION_CODE_LOAD_PAGE_FAULT);
                            end else begin
                                exception_begin(`EXCEPTION_CODE_LOAD_ACCESS_FAULT);
                            end
                        end else
                            m2wb_rd_write = (rd_addr != 0);
                    end
                end
            end else if(e2m_instr_decode[`DECODE_IS_STORE]) begin
                if(storegen_missaligned)
                    exception_begin(`EXCEPTION_CODE_STORE_ADDRESS_MISSALIGNED);
                else if(storegen_unknowntype || funct3[2])
                    exception_begin(`EXCEPTION_CODE_ILLEGAL_INSTRUCTION);
                else begin
                    rd_write = 0;
                    stall_o = 1;
                    M_AXI_AWVALID = !address_done;
                    if(M_AXI_AWREADY) begin
                        address_done_nxt = 1;
                    end
                    M_AXI_WVALID = !data_done;
                    if(M_AXI_WREADY) begin
                        data_done_nxt = 1;
                    end
                    if((address_done || M_AXI_ARREADY) && (M_AXI_WREADY || data_done) && M_AXI_BVALID) begin
                        M_AXI_BREADY = 1;
                        address_done_nxt = 0;
                        data_done_nxt = 0;
                        stall_o = 0;
                        if(M_AXI_BRESP != 0) begin
                            if(M_AXI_BUSER[0]) begin // Store Pagefault
                                exception_begin(`EXCEPTION_CODE_STORE_PAGE_FAULT);
                            end else begin
                                exception_begin(`EXCEPTION_CODE_STORE_ACCESS_FAULT);
                            end
                        end
                    end
                end
            end //else if(//) Implement atomic read and write
        end else begin
            // Instruction is complete, nothing to do
            m2wb_rd_write = (m2wb_rd_addr != 0) && e2m_rd_write;
            m2wb_rd_wdata = e2m_rd_wdata;

        end
    end
end

`ifdef FORMAL
initial @(posedge clk) assume(rst_n == 0);
`endif

always @(posedge clk) begin
    if(rst_n) begin
        if($past(M_AXI_AWVALID) && M_AXI_AWVALID && !$past(M_AXI_AWREADY)) begin
            assert($stable(M_AXI_AWADDR));
            assert($stable(M_AXI_AWLOCK));
            assert($stable(M_AXI_AWPROT));
        end

        if($past(M_AXI_WVALID) && M_AXI_WVALID && !$past(M_AXI_WREADY)) begin
            assert($stable(M_AXI_WDATA));
            assert($stable(M_AXI_WSTRB));
            assert($stable(M_AXI_WLAST));
        end

        if($past(M_AXI_BVALID) && M_AXI_BVALID && !$past(M_AXI_BREADY)) begin
            `ifdef FORMAL
            assume($stable(M_AXI_BRESP));
            assume($stable(M_AXI_BUSER));
            `endif
            assert($stable(M_AXI_BRESP));
            assert($stable(M_AXI_BUSER));
        end

        if($past(M_AXI_ARVALID) && M_AXI_ARVALID && !$past(M_AXI_ARREADY)) begin
            assert($stable(M_AXI_ARADDR));
            assert($stable(M_AXI_ARLOCK));
            assert($stable(M_AXI_ARPROT));
        end
        
        if($past(M_AXI_RVALID) && M_AXI_RVALID && !$past(M_AXI_RREADY)) begin
            `ifdef FORMAL
            assume($stable(M_AXI_RDATA));
            assume($stable(M_AXI_RLAST));
            assume($stable(M_AXI_RRESP));
            assume($stable(M_AXI_RUSER));
            `endif
            assert($stable(M_AXI_RDATA));
            assert($stable(M_AXI_RLAST));
            assert($stable(M_AXI_RRESP));
            assert($stable(M_AXI_RUSER));
        end
        if(e2m_instr_valid) begin
            `ifdef FORMAL
            assume(!(e2m_instr_decode[`DECODE_IS_STORE] && e2m_instr_decode[`DECODE_IS_LOAD]));
            `endif
            assert(!(e2m_instr_decode[`DECODE_IS_STORE] && e2m_instr_decode[`DECODE_IS_LOAD]));
            // Make sure that it's impossible for both to be one at the same time
        end

        if($past(e2m_instr_valid) && e2m_instr_valid && $past(stall_o)) begin
            `ifdef FORMAL
            assume($stable(e2m_instr));
            assume($stable(e2m_instr_decode));
            assume($stable(e2m_pc));
            assume($stable(e2m_memory_address));
            `endif

            assert($stable(e2m_instr));
            assert($stable(e2m_instr_decode));
            assert($stable(e2m_pc));
            assert($stable(e2m_memory_address));
        end
        if(m2wb_rd_write)
            assert(!stall_o);
        assert(csr_mcurrent_privilege != 10);
    end
end

endmodule