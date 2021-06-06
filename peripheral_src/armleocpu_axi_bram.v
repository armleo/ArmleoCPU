

module armleocpu_axi_bram(
    input wire          clk,
    input wire          rst_n,

    input wire          axi_awvalid,
    output reg          axi_awready,
    input wire  [33:0]  axi_awaddr,
    input wire          axi_awlock,
    input wire  [2:0]   axi_awprot,

    // AXI W Bus
    input wire          axi_wvalid,
    output reg          axi_wready,
    input wire  [31:0]  axi_wdata,
    input wire  [3:0]   axi_wstrb,
    input wire          axi_wlast,
    
    // AXI B Bus
    output reg          axi_bvalid,
    input wire          axi_bready,
    output reg [1:0]    axi_bresp,
    
    input wire          axi_arvalid,
    output reg          axi_arready,
    input wire  [33:0]  axi_araddr,
    input wire  [7:0]   axi_arlen,
    input wire  [1:0]   axi_arburst,
    input wire          axi_arlock,
    input wire  [2:0]   axi_arprot,
    

    output reg          axi_rvalid,
    input wire          axi_rready,
    output reg  [1:0]   axi_rresp,
    output reg          axi_rlast,
    output reg  [31:0]  axi_rdata
);


always @* begin
    axi_awready = 0;
    axi_wready = 0;

    
    if(!rst_n) begin
        w_stage_addr_nxt = 0;
        w_stage_active_nxt = 0;
        w_stage_lock_nxt = 0;

        r_stage_active = 0;
        r_stage_burst_remaning_nxt = 0;
        r_stage_addr_nxt = 0;
    end

    if(axi_awvalid) begin
        if(!w_stage_active) begin
            axi_awready = 1;
            w_stage_active_nxt = 1;
            w_stage_addr_nxt = axi_awaddr;
            w_stage_lock_nxt = axi_awlock;
        end
    end
    if(w_stage_active && axi_wvalid) begin

    end
end


endmodule