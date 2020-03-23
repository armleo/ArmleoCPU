module corevx_cache_bypass(
    input clk,
    input rst_n,

    input                   os_load,
    input                   os_store,
    
    input                   s_bypass,

    output                  accessfault,

    output logic            os_bypass_done,

    input                   m_waitrequest,
    input        [1:0]      m_response,
    
    output logic            m_read
    input                   m_readdatavalid,
    
    output logic            m_write
);





endmodule