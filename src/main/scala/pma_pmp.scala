package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

class pma_config_default_t(
  val addr_low: BigInt,
  val addr_high: BigInt,
  val cacheable: Boolean
) {
  
}

class pma_config_t(c: coreParams) {
  val addr_low  = UInt(c.apLen.W)
  val addr_high = UInt(c.apLen.W)
  val cacheable = Bool()
}


class PMA_PMP(is_isntr: Boolean = true, is_ptw: Boolean = true, c: coreParams) extends Module {
}