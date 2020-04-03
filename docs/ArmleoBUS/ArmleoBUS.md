transaction  
cmd[2:0]  
    0 - NONE  
    1 - READ  
    2 - WRITE  
    3 - RESERVED  
    4 - RESERVED  
    5 - RESERVED  
    6 - RESERVED  
    7 - RESERVED  
transaction_done  
address[33:0] - byte bassed address  
burstcount[] - shows amount of bursts (reduced by one)  
wdata[31:0]  
wbyte_enable[3:0]  
rdata[31:0]  

Examples:
![read_request](read_request.wavedrom.svg)  
![read_burst](read_burst.wavedrom.svg)  
![write_burst](write_burst.wavedrom.svg)  


