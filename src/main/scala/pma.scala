package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._

// FIXME: Granulity of PMP/PMA needs to be at least max(i/dcache_entry_bytes)

class pma_config_t(
  val addrLow: BigInt,
  val addrHigh: BigInt,
  val memory: Boolean
) {
  
}


object PMA {
  def apply(c: CoreParams, paddr: UInt): (Bool, Bool) = {
    val memory = Wire(Bool())
    val defined   = Wire(Bool())

    // Require it to be aligned to entry_bytes of both caches
    val max_entry_bytes = 4096
    for (pma_config <- c.pma_config) {
      require(0 == (pma_config.addrLow  & (max_entry_bytes - 1)))
      require(0 == (pma_config.addrHigh & (max_entry_bytes - 1)))
      require(pma_config.addrHigh > pma_config.addrLow)
    }
    
    /**************************************************************************/
    /*                                                                        */
    /*                Iterate over PMA values in reverse order                */
    /*                                                                        */
    /**************************************************************************/
    memory    := false.B
    defined   := false.B

    for(i <- (c.pma_config.length - 1) to 0 by -1) {
      when(
        (paddr < c.pma_config(i).addrHigh.U) &&
        (paddr >= c.pma_config(i).addrLow.U)
      ) {
        memory    := c.pma_config(i).memory.B
        defined   := true.B
      }
    }
    return (defined, memory)
  }
}

