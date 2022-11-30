package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

// FIXME: Granulity of PMP/PMA needs to be at least max(i/dcache_entry_bytes)

class pma_config_default_t(
  val addrLow: BigInt,
  val addrHigh: BigInt,
  val cacheable: Boolean
) {
  
}

class pma_config_t(c: ArchParams) {
  val addrLow  = UInt(c.apLen.W)
  val addrHigh = UInt(c.apLen.W)
  val cacheable = Bool()
}


class PMA_PMP(
  verbose: Boolean = true, instanceName: String = "pmamp",
  is_isntr: Boolean = true, is_ptw: Boolean = true,
  c: CoreParams) extends Module {

}