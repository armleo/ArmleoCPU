package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

// FIXME: Granulity of PMP/PMA needs to be at least max(i/dcache_entry_bytes)

class pma_config_default_t(
  val addr_low: BigInt,
  val addr_high: BigInt,
  val cacheable: Boolean
) {
  
}

class pma_config_t(c: CoreParams) {
  val addr_low  = UInt(c.apLen.W)
  val addr_high = UInt(c.apLen.W)
  val cacheable = Bool()
}


class PMA_PMP(is_isntr: Boolean = true, is_ptw: Boolean = true, c: CoreParams) extends Module {
}