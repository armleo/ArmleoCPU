package memory.l3cache

import chisel3._
import chisel3.util._

class L3CacheVictimAvailabilityResult(implicit val ccx: CCXParams) extends Bundle {
  val availableWays = UInt((1 << ccx.l3.cacheWaysLog2).W)
  val available = Bool()
  val availableIdx = UInt(ccx.l3.cacheWaysLog2.W)
}

class L3CacheVictimAvailabilityIO(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val lookup = Input(new L3CacheVictimLookup)
  val result = Output(new L3CacheVictimAvailabilityResult)
}

class L3CacheVictimAvailability(implicit ccx: CCXParams, cbp: CoherentBusParams) extends Module {
  val io = IO(new L3CacheVictimAvailabilityIO)

  val availableWays = VecInit(io.lookup.entries.map(entry => !entry.valid || (entry.valid && !entry.dirty))).asUInt

  io.result.availableWays := availableWays
  io.result.available := availableWays.orR
  io.result.availableIdx := PriorityEncoder(availableWays)
}
