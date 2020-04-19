`timescale 1ns/1ns
module cache_testbench;

`include "../sync_clk_gen_template.svh"

`include "corevx_cache.svh"
`include "armleobus_defs.svh"
`include "ld_type.svh"
`include "st_type.svh"

wire [3:0] c_response;
wire c_reset_done;
reg [3:0] c_cmd;
reg [31:0] c_address;
reg [2:0] c_load_type;
wire [31:0] c_load_data;
reg [1:0] c_store_type;
reg [31:0] c_store_data;
reg csr_satp_mode;
reg [21:0] csr_satp_ppn;
reg csr_mstatus_mprv;
reg csr_mstatus_mxr;
reg csr_mstatus_sum;
reg [1:0] csr_mstatus_mpp;
reg [1:0] csr_mcurrent_privilege;


wire m_transaction;
wire [2:0] m_cmd;
wire m_transaction_done;
reg [2:0] m_transaction_response;
wire [33:0] m_address;
wire [3:0] m_burstcount;
wire [31:0] m_wdata;
wire [3:0] m_wbyte_enable;
wire [31:0] m_rdata;

reg [2:0] temp_m_transaction_response;

armleobus_scratchmem #(16, 2) scratchmem(
	.clk(clk),
	.transaction(m_transaction),
	.cmd(m_cmd),
	.transaction_done(m_transaction_done),
	.transaction_response(temp_m_transaction_response),
	.address(m_address[17:0]),
	.wdata(m_wdata),
	.wbyte_enable(m_wbyte_enable),
	.rdata(m_rdata)
);

reg [(2**16)-1:0] pma_error = 0;
always @* begin
	if(((pma_error[m_address[17:2]] === 1) && m_transaction_done)
        || (m_address[17:2] > (2**16)-1)) begin
		m_transaction_response = `ARMLEOBUS_UNKNOWN_ADDRESS;
	end else begin
		m_transaction_response = temp_m_transaction_response;
	end
end


corevx_cache cache(
    .*
);

initial begin
    #10000 `assert(0, 1);
end

integer seed = 32'h13ea9c83;

reg [2:0] counter;
wire cached = counter[0];
// 0 - Cached
// 1 - Leaf Page
// 2 - Virtual


always @* begin
    c_cmd = `CACHE_CMD_NONE;
    STATE_STORE_WORD: begin
        c_cmd = (c_reponse == `CACHE_RESPONSE_WAIT || c_response == `CACHE_RESPONSE_IDLE) ? `CACHE_CMD_LOAD : `CACHE_CMD_NONE;
        c_address = {!cached, 19'h00000, 6'h00, 4'h00, 2'b00};
        c_store_type = LOAD_WORD;
    end
    STATE_DONE: begin
        counter <= counter + 1;
        if(counter == 3'b111) begin
            $display("Testbench done");
            $finish;
        end
    end
end

always @(posedge clk) begin
    STATE_LOAD: begin
        state <= STATE_STORE;

    end
    STATE_STORE: begin

    end
end

/*
Phys access / virt access
    Megapage / Leaf page
        Bypasse/Cached
            Store Word
            Load Word
            Execute Word

            Half Store for 2 cases
            Half Load for 2 cases

            Byte Store for 4 cases
            Byte Load for 4 cases

            Store Word missaligned for 4 cases
            Load Word missaligned for 4 cases
            Execute Word missaligned for 4 cases

            Store Half missaligned for 2 cases
            Load Half missaligned for 2 cases

            !!Byte cannot be missaligned

            Unknown access for all store cases
            Unknown access for all Load/Execute cases

            if virt
                PTW Access fault
                Refill Access fault
                Flush Access Fault

                PTW Megapage Pagefault
                PTW Leaf Pagefault
                Cache memory pagefault for each case (read, write, execute, access, dirty, user) for megaleaf and leaf


Generate random access pattern using GLFSR, check for validity
*/


endmodule
