
module armleocpu_axi_exclusive_monitor(
    clk,
    rst_n,

    s_axi_awvalid, s_axi_awready, s_axi_awaddr, s_axi_awlen, s_axi_awburst, s_axi_awsize, s_axi_awid, s_axi_awlock,
    s_axi_wvalid, s_axi_wready, s_axi_wdata, s_axi_wstrb, s_axi_wlast,
    s_axi_bvalid, s_axi_bready, s_axi_bresp, s_axi_bid,
    
    s_axi_arvalid, s_axi_arready, s_axi_araddr, s_axi_arlen, s_axi_arsize, s_axi_arburst, s_axi_arid, s_axi_arlock,
    s_axi_rvalid, s_axi_rready, s_axi_rresp, s_axi_rlast, s_axi_rdata, s_axi_rid,
    
    m_axi_awvalid, m_axi_awready, m_axi_awaddr, m_axi_awlen, m_axi_awburst, m_axi_awsize, m_axi_awid,
    m_axi_wvalid, m_axi_wready, m_axi_wdata, m_axi_wstrb, m_axi_wlast,
    m_axi_bvalid, m_axi_bready, m_axi_bresp, m_axi_bid,
    
    m_axi_arvalid, m_axi_arready, m_axi_araddr, m_axi_arlen, m_axi_arsize, m_axi_arburst, m_axi_arid,
    m_axi_rvalid, m_axi_rready, m_axi_rresp, m_axi_rlast, m_axi_rdata, m_axi_rid
    );


parameter ADDR_WIDTH = 32; // Determines the size of addr bus. If memory outside this peripheral is accessed BRESP/RRESP is set to DECERR
parameter ID_WIDTH = 4;
localparam SIZE_WIDTH = 3;
parameter DATA_WIDTH = 32; // 32 or 64
localparam DATA_STROBES = DATA_WIDTH / 8;

    input wire          clk;
    input wire          rst_n;

    // client port, connects to cpu's host port
    input wire          s_axi_awvalid;
    output reg          s_axi_awready;
    input wire  [ID_WIDTH-1:0]
                        s_axi_awid;
    input wire  [ADDR_WIDTH-1:0]
                        s_axi_awaddr;
    input wire  [7:0]   s_axi_awlen;
    input wire  [SIZE_WIDTH-1:0]
                        s_axi_awsize;
    input wire  [1:0]   s_axi_awburst;
    input wire          s_axi_awlock;
    

    // AXI W Bus
    input wire          s_axi_wvalid;
    output reg          s_axi_wready;
    input wire  [DATA_WIDTH-1:0]
                        s_axi_wdata;
    input wire  [DATA_STROBES-1:0]
                        s_axi_wstrb;
    input wire          s_axi_wlast;
    
    // AXI B Bus
    output reg          s_axi_bvalid;
    input wire          s_axi_bready;
    output reg [1:0]    s_axi_bresp;
    output reg [ID_WIDTH-1:0]
                        s_axi_bid;
    
    
    input wire          s_axi_arvalid;
    output reg          s_axi_arready;
    input wire  [ID_WIDTH-1:0]
                        s_axi_arid;
    input wire  [ADDR_WIDTH-1:0]
                        s_axi_araddr;
    input wire  [7:0]   s_axi_arlen;
    input wire  [SIZE_WIDTH-1:0]
                        s_axi_arsize;
    input wire  [1:0]   s_axi_arburst;
    input wire          s_axi_arlock;
    
    

    output reg          s_axi_rvalid;
    input wire          s_axi_rready;
    output reg  [1:0]   s_axi_rresp;
    output reg          s_axi_rlast;
    output reg  [DATA_WIDTH-1:0]
                        s_axi_rdata;
    output reg [ID_WIDTH-1:0]
                        s_axi_rid;
    

    // Host port, connectes to memory or peripheral device
    // TODO: Add to port list
    output reg          m_axi_awvalid;
    input wire          m_axi_awready;
    output reg  [ID_WIDTH-1:0]
                        m_axi_awid;
    output reg  [ADDR_WIDTH-1:0]
                        m_axi_awaddr;
    output reg  [7:0]   m_axi_awlen;
    output reg  [SIZE_WIDTH-1:0]
                        m_axi_awsize;
    output reg  [1:0]   m_axi_awburst;
    

    // AXI W Bus
    output reg          m_axi_wvalid;
    input wire          m_axi_wready;
    output reg  [DATA_WIDTH-1:0]
                        m_axi_wdata;
    output reg  [DATA_STROBES-1:0]
                        m_axi_wstrb;
    output reg          m_axi_wlast;
    
    // AXI B Bus
    input wire          m_axi_bvalid;
    output reg          m_axi_bready;
    input wire [1:0]    m_axi_bresp;
    input wire [ID_WIDTH-1:0]
                        m_axi_bid;
    
    
    output reg          m_axi_arvalid;
    input wire          m_axi_arready;
    output reg  [ID_WIDTH-1:0]
                        m_axi_arid;
    output reg  [ADDR_WIDTH-1:0]
                        m_axi_araddr;
    output reg  [7:0]   m_axi_arlen;
    output reg  [SIZE_WIDTH-1:0]
                        m_axi_arsize;
    output reg  [1:0]   m_axi_arburst;
    
    

    input wire          m_axi_rvalid;
    output reg          m_axi_rready;
    input wire  [1:0]   m_axi_rresp;
    input wire          m_axi_rlast;
    input wire  [DATA_WIDTH-1:0]
                        m_axi_rdata;
    input wire [ID_WIDTH-1:0]
                        m_axi_rid;


`ifdef DEBUG_EXCLUSIVE_MONITOR
`include "assert.vh"
`endif


`include "armleocpu_defines.vh"

`DEFINE_REG_REG_NXT(4, state, state_nxt, clk)
`DEFINE_REG_REG_NXT(4, is_current_transaction_locking, is_current_transaction_locking_nxt, clk)

`DEFINE_REG_REG_NXT(1, atomic_lock_valid, atomic_lock_valid_nxt, clk)
`DEFINE_REG_REG_NXT(ADDR_WIDTH, atomic_lock_addr, atomic_lock_addr_nxt, clk)

always @* begin
    state_nxt = state;
    atomic_lock_addr_nxt = atomic_lock_addr;
    atomic_lock_valid_nxt = atomic_lock_valid;

    m_axi_awvalid = 0;
    s_axi_awready = 0;

    m_axi_awid = s_axi_awid;
    m_axi_awaddr = s_axi_awaddr;
    m_axi_awlen = s_axi_awlen;
    m_axi_awsize = s_axi_awsize;
    m_axi_awburst = s_axi_awburst;


    m_axi_wvalid = 0;
    s_axi_wready = 0;

    m_axi_wdata = s_axi_wdata;
    m_axi_wstrb = s_axi_wstrb; // Overwritten with zero when locking write is not EXOKAY
    m_axi_wlast = s_axi_wlast; // Assumed to be always one


    s_axi_bvalid = 0;
    m_axi_bready = 0;

    s_axi_bresp = 0;
    s_axi_bid = m_axi_bid;




    m_axi_arvalid = 0;
    s_axi_arready = 0;

    m_axi_arid = s_axi_arid;
    m_axi_araddr = s_axi_araddr;
    m_axi_arlen = s_axi_arlen;
    m_axi_arsize = s_axi_arsize;
    m_axi_arburst = s_axi_arburst;


    s_axi_rvalid = 0;
    m_axi_rready = 0;

    s_axi_rresp = 0;
    s_axi_rlast = m_axi_rlast;
    s_axi_rdata = m_axi_rdata;
    s_axi_rid = m_axi_rid;

    if(!rst_n) begin
        state_nxt = STATE_IDLE;
        atomic_lock_addr_nxt = 0;
        atomic_lock_valid_nxt = 0;

        ar_done_nxt = 1;
        aw_done_nxt = 1;
        w_done_nxt = 1;
    end else begin
        if(state == STATE_WRITE || (s_axi_awvalid && (state == STATE_IDLE))) begin
            state_nxt = STATE_WRITE;
            
            
            is_current_transaction
            m_axi_awvalid = !aw_done;
            if(!aw_done) begin
                is_current_transaction_locking_nxt = s_axi_awlock;

                if(is_current_transaction_locking_nxt)
            end
            if(s_axi_awready) begin
                aw_done_nxt = 1;

            end
            // If not locking, dont mask anything
            //  -> if write to reserved address, OKAY and invalidate reservation
            //  -> else OKAY nothing
            // else if locking
            //  -> if to reserved address, EXOKAY and invalidate reservation, DO the write
            //  -> if not to reserved address, OKAY and mask the written data
            // pass by the AW and W until last one
            // Only non-burst atomic transactions are supported
            
        end else if(state == STATE_READ || (s_axi_arvalid && (state == STATE_IDLE)))
            is_current_transaction_locking_nxt = s_axi_arlock;
            
            // Pass by the AR request
            // Pass the R response and on last return to idle
            // Only non-burst atomic transactions are supported, so all transactions that is atomic is last
            // If locking atomic_lock_valid_nxt = 1; atomic_lock_addr_nxt = araddr;
        endcase
    end
end

endmodule

    