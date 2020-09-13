module armleocpu_lsu(
    input                               clk,
    input                               rst_n,

    input [31:0]                        address,
    input [1:0]                         privilege,

    input                               load_valid,
    output                              load_ready,
    input [2:0]                         load_type,
    input                               load_lock,
    // Response
    output [31:0]                       load_data,
    output [2:0]                        load_resp,
    // none, unknown_type, amo_unsupported == store_conditional_fail, pagefault, missaligned, address error, device error
    

    input                               store_valid,
    output                              store_ready,
    input [2:0]                         store_type,
    input                               store_conditional,
    input [31:0]                        store_data,
    // Response
    output [2:0]                        store_resp,
    // none, unknown_type, store_conditional_fail, pagefault, missaligned, address error, device error

    
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
    output  reg                         M_AXI_ARUSER, // If set, then flush request
    // R-Bus
    input	wire				        M_AXI_RVALID,
    output	reg				            M_AXI_RREADY,
    input	wire	[31:0]	            M_AXI_RDATA,
    input	wire				        M_AXI_RLAST,
    input	wire	[1:0]			    M_AXI_RRESP,
    input   wire    [1:0]               M_AXI_RUSER // [0] shows pagefault, [1] shows flush error RRESP should be 2'b11
);

assign M_AXI_AWSIZE = 3'b010;
assign M_AXI_ARSIZE = 3'b010;
assign M_AXI_ARLEN = 0;
assign M_AXI_AWLEN = 0;
assign M_AXI_WLAST = 1; // Only one word is possible
assign M_AXI_AWBURST = 2'b01; // INCR
assign M_AXI_ARBURST = 2'b01; // INCR

assign M_AXI_ARADDR = address;
assign M_AXI_AWADDR = address;

// TODO: Calculate PROT based on mcurrent_privilege
wire [2:0] prot = {1'b0, 1'b0, 1'b0};

assign M_AXI_AWPROT = prot;
assign M_AXI_ARPROT = prot;

wire loadgen_missaligned;
wire loadgen_unknowntype;

armleocpu_loadgen loadgen(
    .inwordOffset(address[1:0]),
    .loadType(load_type),
    .LoadGenDataIn(M_AXI_RDATA),
    .LoadGenDataOut(load_data),
    .LoadMissaligned(loadgen_missaligned),
    .LoadUnknownType(loadgen_unknowntype)
);

wire storegen_missaligned;
wire storegen_unknowntype;

armleocpu_storegen storegen(
    .inwordOffset(address[1:0]),
    .storegenType(store_type),

    .storegenDataIn(store_data),

    .storegenDataOut(M_AXI_WDATA),
    .storegenDataMask(M_AXI_WSTRB),
    .storegenMissAligned(storegen_missaligned),
    .storegenUnknownType(storegen_unknowntype)
);

reg address_done;
reg address_done_nxt;

reg data_done;
reg data_done_nxt;


always @(posedge clk) begin
    if(!rst_n) begin
        address_done <= 0;
        data_done <= 0;
    end else begin
        address_done <= address_done_nxt;
        data_done <= data_done_nxt;
    end
end


always @* begin
    load_ready = 0;
    load_resp = `ARMLEOCPU_LSU_RESP_NONE;
    store_ready = 0;
    store_resp = `ARMLEOCPU_LSU_RESP_NONE;
    address_done_nxt = address_done;
    data_done_nxt = data_done;
    M_AXI_RREADY = 0;
    M_AXI_ARVALID = 0;
    if(load_valid) begin
        if(loadgen_missaligned)
            load_resp = `ARMLEOCPU_LSU_RESP_MISSALIGNED;
            load_ready = 1;
        else if(loadgen_unknowntype)
            load_resp = `ARMLEOCPU_LSU_RESP_UNKNOWN_TYPE;
            load_ready = 1;
        else begin
            load_ready = 0;
            M_AXI_ARVALID = !address_done;
            M_AXI_RREADY = 0;
            if(M_AXI_ARVALID && M_AXI_ARREADY) begin
                address_done_nxt = 1;
            end
            if(((M_AXI_ARREADY) || address_done) && (M_AXI_RVALID) begin
                M_AXI_RREADY = 1;
                address_done_nxt = 0;
                load_ready = 1;
                if(M_AXI_RRESP != 0) begin
                    if(M_AXI_RUSER[0]) begin // Load Pagefault
                        load_resp = `ARMLEOCPU_LSU_RESP_PAGEFAULT;
                        load_ready = 1;
                    end else begin
                        load_resp = `ARMLEOCPU_LSU_RESP_ACCESSFAULT;
                        load_ready = 1;
                    end
                end else begin
                    load_resp = `ARMLEOCPU_LSU_RESP_NONE;
                    load_ready = 1;
                end
            end
        end
    end
end



`ifdef DEBUG_LSU
reg reseted = 0;
reg addressed = 0;

task decoupled_check;
input valid;
input ready;
begin
    if(!ready && !$past(ready) && $past(valid))
        assert(valid);
    if(ready && past(valid))
        assert(valid);
end
endtask

always @(posedge clk) begin
    if(!rst_n) reseted <= 1;
    if(rst_n && reseted) begin
        // Make sure both signals are not asserted at the same time
        // Code is not ready for this case
        assert(!(load_valid && store_valid));

        // Make sure that if ready is not asserted,
            // then valid can't be de-asserted
        decoupled_check(load_valid, load_ready);
        decoupled_check(store_valid, store_ready);

        decoupled_check(M_AXI_AWVALID, M_AXI_AWREADY);
        decoupled_check(M_AXI_WVALID, M_AXI_WREADY);
        decoupled_check(M_AXI_BVALID, M_AXI_BREADY);

        decoupled_check(M_AXI_ARVALID, M_AXI_ARREADY);
        decoupled_check(M_AXI_RVALID, M_AXI_RREADY);


        // New transactions should not start until done
        if(M_AXI_ARVALID && M_AXI_ARREADY) begin
            assert(!addressed);
            addressed <= 1;
        end
        if(M_AXI_RVALID && M_AXI_RREADY) begin
            assert(addressed || M_AXI_ARVALID && M_AXI_ARREADY);
            addressed <= 0;
        end

        // Load bus
        // Make sure that input does not change when ready is not asserted
        if(load_valid && $past(load_valid) && !$past(load_ready)) begin
            assert($stable(load_data));
            assert($stable(load_lock));
            assert($stable(load_type));
            assert($stable(address));
            assert($stable(privilege));
        end
        // Store bus
        // Make sure that input does not change when ready is not asserted
        if($past(store_valid) && store_valid && !$past(store_ready)) begin
            assert($stable(store_type));
            assert($stable(store_conditional));
            assert($stable(store_data));
            assert($stable(address));
            assert($stable(privilege));
        end

        
        // AW-Bus
        // Make sure that output does not change when ready is not asserted
        if($past(M_AXI_AWVALID) && M_AXI_AWVALID && !$past(M_AXI_AWREADY)) begin
            assert($stable(M_AXI_AWADDR));
            assert($stable(M_AXI_AWLOCK));
            assert($stable(M_AXI_AWPROT));
        end
        
        // W-Bus
        // Make sure that output does not change when ready is not asserted
        if($past(M_AXI_WVALID) && M_AXI_WVALID && !$past(M_AXI_WREADY)) begin
            assert($stable(M_AXI_WDATA));
            assert($stable(M_AXI_WSTRB));
            assert($stable(M_AXI_WLAST));
        end
        // B-Bus
        // Make sure that input does not change when ready is not asserted
        if($past(M_AXI_BVALID) && M_AXI_BVALID && !$past(M_AXI_BREADY)) begin
            assert($stable(M_AXI_BRESP));
            assert($stable(M_AXI_BUSER));
        end

        // AR-Bus
        // Make sure that output does not change when ready is not asserted
        if($past(M_AXI_ARVALID) && M_AXI_ARVALID && !$past(M_AXI_ARREADY)) begin
            assert($stable(M_AXI_ARADDR));
            assert($stable(M_AXI_ARLOCK));
            assert($stable(M_AXI_ARPROT));
        end
        // R-Bus
        // Make sure that input does not change when ready is not asserted
        if($past(M_AXI_RVALID) && M_AXI_RVALID && !$past(M_AXI_RREADY)) begin
            assert($stable(M_AXI_RDATA));
            assert($stable(M_AXI_RLAST));
            assert($stable(M_AXI_RRESP));
            assert($stable(M_AXI_RUSER));
        end
        
    end
end
`endif
endmodule