package armleocpu.memory.l3cache

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.Consts._


class VictimAvailabilityLookup(implicit val ccx: CCXParams, implicit val bp: BusParams) extends Bundle {
  private val tagWidth = bp.addrWidth - ccx.l3.cacheEntriesLog2 - cacheLineLog2

  val entries = Vec(1 << ccx.l3.cacheWaysLog2, new Entry(tagWidth))
}

class VictimAvailabilityResult(implicit val ccx: CCXParams) extends Bundle {
  val availableWays = UInt((1 << ccx.l3.cacheWaysLog2).W)
  val available = Bool()
  val availableIdx = UInt(ccx.l3.cacheWaysLog2.W)
}

class VictimAvailabilityIO(implicit val ccx: CCXParams, implicit val bp: BusParams) extends Bundle {
  val lookup = Input(new VictimAvailabilityLookup)
  val result = Output(new VictimAvailabilityResult)
}

class VictimAvailability(implicit ccx: CCXParams, bp: BusParams) extends Module {
  val io = IO(new VictimAvailabilityIO)

  val availableWays = VecInit(io.lookup.entries.map(entry => !entry.valid || (entry.valid && !entry.dirty))).asUInt

  io.result.availableWays := availableWays
  io.result.available := availableWays.orR
  io.result.availableIdx := PriorityEncoder(availableWays)
}
