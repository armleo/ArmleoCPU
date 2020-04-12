`timescale 1ns/1ns
module cache_testbench;

`include "../sync_clk_gen_template.svh"

`include "corevx_cache.svh"
`include "armleobus_defs.svh"
`include "ld_type.svh"
`include "st_type.svh"

wire [3:0] c_response;
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
	if(((pma_error[m_address >> 2] === 1) && m_transaction_done)
        || (m_address > (2**16)-1)) begin
		m_transaction_response = `ARMLEOBUS_UNKNOWN_ADDRESS;
	end else begin
		m_transaction_response = temp_m_transaction_response;
	end
end


corevx_cache cache(
    .*
);

// Remember: mem addressing is word based

task cache_writereq;
input [31:0] address;
input [31:0] store_data;
input [1:0] store_type;
begin
    c_cmd = `CACHE_CMD_STORE;
    c_address = address;
    c_store_type = store_type;
    c_store_data = store_data;
    do begin
        @(negedge clk);
    end while(c_response == `CACHE_RESPONSE_WAIT && c_response != `CACHE_RESPONSE_IDLE);
    @(negedge clk)
    c_cmd = `CACHE_CMD_NONE;
end
endtask

task cache_readreq;
input [31:0] address;
input [2:0] load_type;
begin
    c_cmd = `CACHE_CMD_LOAD;
    c_address = address;
    c_load_type = load_type;
    do begin
        @(negedge clk);
    end while(c_response == `CACHE_RESPONSE_WAIT && c_response != `CACHE_RESPONSE_IDLE);
    @(negedge clk)
    c_cmd = `CACHE_CMD_NONE;
end
endtask

integer seed = 32'h13ea9c83;

integer temp;

reg [31:0] addr, data;

initial begin
    // TODO: Test bypassed phys write
    // TODO: Test bypassed phys read
    // TODO: Test bypassed phys execute

    // TODO: Test bypassed virt write
    // TODO: Test bypassed virt write (w/ tlb cached address)
    // TODO: Test bypassed virt read
    // TODO: Test bypassed virt read (w/ tlb cached address)
    // TODO: Test bypassed virt execute
    // TODO: Test bypassed virt execute (w/ tlb cached address)
    @(posedge rst_n);
    csr_satp_mode = 0;
    csr_satp_ppn = 0;
    csr_mstatus_mprv = 0;
    csr_mstatus_mxr = 0;
    csr_mstatus_sum = 0;
    csr_mstatus_mpp = 0;
    csr_mcurrent_privilege = 0;
    
    @(negedge clk)
    //cache_writereq({20'h00000, 6'h4, 4'h1, 2'h0}, 32'd0, STORE_WORD);
    cache_writereq({20'h00000, 6'h4, 4'h1, 2'h0}, 32'd1, STORE_WORD);
    
    cache_readreq({20'h00000, 6'h4, 4'h1, 2'h0}, LOAD_WORD);
    `assert(c_load_data, 32'd1);
    cache_writereq({20'h00000, 6'h4, 4'h2, 2'h0}, 32'hFF, STORE_WORD);
    cache_readreq({20'h00000, 6'h4, 4'h2, 2'h0}, LOAD_WORD);
    `assert(c_load_data, 32'hFF);

    repeat(2) begin
        @(posedge clk);
    end
    $finish;
    $finish;

    
    // TODO: check for access that cycle thru victim_way:
    /*
    @(negedge clk)
    cache_writereq({20'h00000, 6'h4, 4'h1, 2'h0}, 32'd0);
    cache_writereq({20'h00001, 6'h4, 4'h1, 2'h0}, 32'd1);
    cache_writereq({20'h00002, 6'h4, 4'h1, 2'h0}, 32'd2);
    cache_writereq({20'h00003, 6'h4, 4'h1, 2'h0}, 32'd3);
    cache_writereq({20'h00004, 6'h4, 4'h1, 2'h0}, 32'd4);
    cache_writereq({20'h00005, 6'h4, 4'h1, 2'h0}, 32'd5);

    cache_readreq({20'h00000, 6'h4, 4'h1, 2'h0});
    cache_checkread(32'd0);
    cache_readreq({20'h00001, 6'h4, 4'h1, 2'h0});
    cache_checkread(32'd1);
    cache_readreq({20'h00002, 6'h4, 4'h1, 2'h0});
    cache_checkread(32'd2);
    cache_readreq({20'h00003, 6'h4, 4'h1, 2'h0});
    cache_checkread(32'd3);
    cache_readreq({20'h00004, 6'h4, 4'h1, 2'h0});
    cache_checkread(32'd4);
    cache_readreq({20'h00005, 6'h4, 4'h1, 2'h0});
    cache_checkread(32'd5);
    
    $display("[t=%d][TB] Known ordered accesses done", $time);
    
    // Random access test

    seed = 32'h13ea9c84;
    temp = $urandom(seed);
    addr = 0;
    //addr = ;
    //data = ;
    repeat(1000) begin
        addr = addr + 1;
        data = $urandom;
        cache_writereq((addr) << 5, data);
        saved_mem[addr] = data;
    end
    
    $display("[t=%d][TB] RNG Write done", $time);
    seed = 32'h13ea9c84;
    temp = $urandom(seed);
    addr = 0;
    repeat(1000) begin
        addr = addr + 1;
        data = $urandom;
        cache_readreq((addr) << 5);
        cache_checkread(saved_mem[addr]);
    end
        
    $display("[t=%d][TB] RNG Read done", $time);


    // TODO: check flush all

    
    */
end



/*

PTW Megapage Access fault
PTW Page access fault
Cache memory access fault

PTW Megapage pagefault
PTW Page pagefault
Cache memory pagefault for each case (read, write, execute, access, dirty, user)

For two independent lanes
    For each csr_satp_mode = 0 and csr_satp_mode = 1
        For address[33:32] = 0 and address[33:32] != 0
            For each load type and store type combination
                Bypassed load
                Bypassed load after load
                Bypassed store
                Bypassed load after store
                Bypassed store after store

                Cached load
                Cached load after load
                Cached store
                Cached load after store
                Cached store after store
        For each unknown type for load
            Bypassed load
            Cached load
        For each unknown type for store
            Bypassed store
            Cached store
        For each missaligned address for each store case
            Bypassed store
            Cached store
        For each missaligned address for each load case
            Bypassed load
            Cached load
    Flush

Generate random access pattern using GLFSR, check for validity
*/


endmodule
