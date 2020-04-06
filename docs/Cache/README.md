# Cache
See cache_if.wavedrom.json for reference of cpu side of things

On each command
    for flush all:
        flush tlb
        write each lane in each way
        register csr_satp_mode and csr_satp_ppn
    for execute, read, or write:
        if not stalled -> accept
            request tlb read
            request cache read on each lane
on output stage
    if active (last cycle we accepted command)
        if tlb hit
            if bypass (phys address[31] is high) then
                bypass request to backstorage
            else
                if cache hit then
                    response = DONE
                    if input stage registered
                        write => then pass data thru storegen and then write to cache
                        read => then read data from cache thru loadgen and then read from cache
                else
                    response = WAIT
        else
            response = WAIT
            go to state Page Table Walk (PTW)
        
        