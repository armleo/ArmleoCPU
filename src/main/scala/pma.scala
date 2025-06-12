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


class PMA(ccx: CCXParams) extends CCXModule(ccx) {
  val paddr     = Input(UInt(ccx.apLen.W))
  val memory    = Output(Bool())
  val defined   = Output(Bool())

  // Require it to be aligned to entry_bytes of both caches
  val max_entry_bytes = 4096
  for (pma_config <- ccx.core.pma_config) {
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

  for(i <- (ccx.core.pma_config.length - 1) to 0 by -1) {
    when(
      (paddr < ccx.core.pma_config(i).addrHigh.U) &&
      (paddr >= ccx.core.pma_config(i).addrLow.U)
    ) {
      memory    := ccx.core.pma_config(i).memory.B
      defined   := true.B
    }
  }
}
