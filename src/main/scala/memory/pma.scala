package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._

// FIXME: Granulity of PMP/PMA needs to be at least max(i/dcache_entry_bytes)

class PmaConfig(
  val addrLow: BigInt,
  val addrHigh: BigInt,
  val memory: Boolean
) {
  
}


class PMA(implicit ccx: CCXParams) extends CCXModule {
  val paddr     = Input(UInt(ccx.apLen.W))
  val memory    = Output(Bool())
  val defined   = Output(Bool())

  // Require it to be aligned to entry_bytes of both caches
  val max_entry_bytes = 4096
  for (pmaConfig <- ccx.core.pmaConfig) {
    require(0 == (pmaConfig.addrLow  & (max_entry_bytes - 1)))
    require(0 == (pmaConfig.addrHigh & (max_entry_bytes - 1)))
    require(pmaConfig.addrHigh > pmaConfig.addrLow)
  }
  
  /**************************************************************************/
  /*                                                                        */
  /*                Iterate over PMA values in reverse order                */
  /*                                                                        */
  /**************************************************************************/
  memory    := false.B
  defined   := false.B

  for(i <- (ccx.core.pmaConfig.length - 1) to 0 by -1) {
    when(
      (paddr < ccx.core.pmaConfig(i).addrHigh.U) &&
      (paddr >= ccx.core.pmaConfig(i).addrLow.U)
    ) {
      memory    := ccx.core.pmaConfig(i).memory.B
      defined   := true.B
    }
  }
}
