# Cache
See cache_if.wavedrom.json for reference of cpu side of things

On each command
    if reset then
        write lanestate to invalid
    for flush all:
        on entrance:
            register csr_satp_mode and csr_satp_ppn
            flush tlb
        for each lane for each way do:
            go to state flush
    for execute, read, or write:
        if not stalled -> accept
            request tlb read
            request cache read on each lane
            request cache ptag read
            request lanestate read
on output stage
    if active (process accepted command)
        if tlb hit
            if bypass (phys address[31] is high) then
                bypass request to back memory bus
            else
                if cache hit then
                    response = DONE
                    if input stage registered
                        write => then pass data thru storegen and then write to cache, write lanestate to dirty
                        read => then read data from cache thru loadgen and then read from cache
                else
                    response = WAIT
                    if(victim vaid and dirty) then
                        go to flush and then return to refill
                    else go to refill begin

                    end
        else
            response = WAIT
            os_active stays high, so when PTW completes we can go to tlb hit case
            go to state Page Table Walk (PTW)
Page table Walker:
    Map m_* signals to PTW
    Wait for completion and go to state idle
        also:
            issue tlb read (so that we don't need to accept request once more, but to process already accepted one)
Refill:
```
sync signals:
    csr_matp_mode_r
    csr_matp_ppn_r
    victim_way
map:
    os_valid_per_way
    os_dirty_per_way
async signals:
    c_response
    m_transaction
    m_cmd
    m_address
    m_burstcount
    m_wdata
    m_wbyteenable
    ptag_read
    ptag_readlane
    ptag_write
    ptag_writelane = os_address_lane
    ptag_writedata
    lanestate_read
    lanestate_readlane
    lanestate_write
    lanestate_writelane = os_address_lane
    lanestate_writedata
    storage_read
    storage_readlane
    storage_readoffset
    storage_writedata
    storage_writelane
    storage_writeoffset
    ptw_resolve_request
    ptw_resolve_vtag = os_address_vtag
    loadgen_datain = m_readdata || os_readdata
    
```     