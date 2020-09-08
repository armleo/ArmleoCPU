module armleocpu_memory (
    input clk,
    input rst_n,

    // Execute <-> memory
    input                               e2m_instr_valid,
    input      [31:0]                   e2m_instr,
    input      [DECODE_IS_WIDTH-1:0]    e2m_instr_decode,
    input      [31:0]                   e2m_pc,
    input      [31:0]                   e2m_memory_address,

    // Memory -> Fetch
    output reg [3:0]                    m2f_cmd,
    output reg [31:0]                   m2f_exception,

    // Memory -> writeback
    output reg                          m2wb_rd_write,
    output      [4:0]                   m2wb_rd_waddr,
    output reg [31:0]                   m2wb_rd_wdata,
    


    // Pipeline I/O
    output                              stall_o,
    output                              kill,

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
    input	wire	[1:0]			    M_AXI_RRESP
);

assign M_AXI_AWSIZE = 3'b010;
assign M_AXI_ARSIZE = 3'b010;
assign M_AXI_ARLEN = 0;
assign M_AXI_AWLEN = 0;
assign M_AXI_WLAST = M_AXI_WVALID; // Only one word is possible
assign M_AXI_AWBURST = 2'b01; // INCR

assign M_AXI_ARADDR = e2m_memory_address;
assign M_AXI_AWADDR = e2m_memory_address;

assign m2wb_rd_waddr = e2m_instr[];

assign M_AXI_AWPROT = ;
assign M_AXI_ARPROT = ;

assign m2wb_rd_wdata = loadgen_cpu_rdata;

assign M_AXI_WDATA = storegen_bus_wdata;

reg address_done;
reg data_done;
reg burst_done;

always @(posedge clk) begin
    if(!rst_n) begin
        address_done <= 0;
        data_done <= 0;
        burst_done <= 0;
    end else begin
        address_done <= address_done_nxt;
        data_done <= data_done_nxt;
        burst_done <= burst_done_nxt;
    end
end

always @* begin
    stall_o = 0;
    M_AXI_ARVALID = 0;
    M_AXI_RREADY = 0;
    address_done_nxt = address_done;
    burst_done_nxt = burst_done;
    data_done_nxt = data_done;
    rd_write = 0;
    M_AXI_AWVALID = 0;
    M_AXI_WREADY = 0;
    M_AXI_BREADY = 0;
    if(e2m_instr_valid) begin
        if(e2m_instr_decode[`DECODE_IS_LOAD]) begin
            stall_o = 1;
            M_AXI_ARVALID = !address_done;
            M_AXI_RREADY = 0;
            if(M_AXI_ARVALID && M_AXI_ARREADY) begin
                address_done_nxt = 1;
            end

            if(((M_AXI_ARREADY) || address_done) && (M_AXI_RVALID) begin
                address_done_nxt = 0;
                stall_o = 0;
                rd_write = (rd_addr != 0);
                M_AXI_RREADY = 1;
            end
        end else if(e2m_instr_decode[`DECODE_IS_STORE]) begin
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
                burst_done_nxt = 1;
                M_AXI_BREADY = 1;
            end
        end
    end
end

endmodule