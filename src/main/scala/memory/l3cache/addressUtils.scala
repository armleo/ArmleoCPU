package armleocpu.memory.l3cache

import chisel3._
import armleocpu._

object addressUtils {
  def getCacheEntryIdx(addr: UInt)(implicit ccx: CCXParams): UInt = {
    addr(ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2 - 1, ccx.cacheLineLog2)
  }

  def getCacheTag(addressProvider: AddressProvider)(implicit ccx: CCXParams, cbp: CoherentBusParams): UInt = {
    getCacheTag(addressProvider.addr)
  }

  def getCacheTag(addr: UInt)(implicit ccx: CCXParams, cbp: CoherentBusParams): UInt = {
    addr(addr.getWidth - 1, ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)
  }
}
