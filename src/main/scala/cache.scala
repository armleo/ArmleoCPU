package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

class cache_cmd extends ChiselEnum {
    val cache_cmd_read, cache_cmd_fetch, cache_cmd_write = Value
}


class CacheRequestInterface extends Bundle {
    val ready = Output(Bool())
    val valid = Input(Bool())
    
    val bits = Input(new Bundle{
        cmd = new cache_cmd,
        memory_csr_regs = MemoryCSRBundle
        addr = UInt(xLen.w)
    })
}
