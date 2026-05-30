package armleocpu.memory.l3cache

import chisel3._
import armleocpu._

object addressUtils {
  def getCacheEntryIdx(addr: UInt)(implicit ccx: CCXParams): UInt = {
    addr(ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2 - 1, ccx.cacheLineLog2)
  }

  def getCacheTag(addr: UInt)(implicit ccx: CCXParams, cbp: CoherentBusParams): UInt = {
    addr(cbp.addrWidth - 1, ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)
  }
}
