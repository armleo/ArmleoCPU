package armleocpu.memory.l3cache

import chisel3._
import chisel3.util._
import armleocpu._

class BankIO(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val up = Vec(ccx.coreCount, Flipped(new CoherentBus()))
  val down = new ReadWriteBus()(cbp)
}


class EntryFlags(tagWidth: Int)(implicit val ccx: CCXParams) extends Bundle {
  val tag = UInt(tagWidth.W)
  val valid = Bool() // Valid entry
  val dirty = Bool() // Before discarding needs to be written to downstream
  val unique = Bool() // Set only if one of the sharers is set to unique
  val sharer = UInt(ccx.coreCount.W) // Data may be available in either of these cores.
}

class Entry(tagWidth: Int)(implicit ccx: CCXParams, implicit val cbp: CoherentBusParams)
    extends EntryFlags(tagWidth = tagWidth)(ccx = ccx) {
  val data = UInt((ccx.cacheLineBytes * 8).W)
  require(ccx.cacheLineBytes == cbp.busBytes) // We only support snoops the size of cache line
}

class Req(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val addr = UInt(cbp.addrWidth.W)
  val core = UInt(log2Ceil(ccx.coreCount).W)
  val op = UInt(8.W)
}
