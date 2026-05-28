package armleocpu

import chisel3._
import chisel3.util._

class L3CacheVictimLookup(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  private val tagWidth = cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2

  val entries = Vec(1 << ccx.l3.cacheWaysLog2, new L3CacheEntry(tagWidth))
}

class L3CacheVictimCommand extends Bundle {
  val increment = Bool()
  val clear = Bool()
}

class L3CacheVictimStatus(implicit val ccx: CCXParams) extends Bundle {
  val victimWay = UInt(ccx.l3.cacheWaysLog2.W)
}

class L3CacheVictimKeeperIO(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val command = Input(new L3CacheVictimCommand)
  val status = Output(new L3CacheVictimStatus)
}

class L3CacheVictimKeeper(implicit ccx: CCXParams, cbp: CoherentBusParams) extends Module {
  val io = IO(new L3CacheVictimKeeperIO)

  private val ways = 1 << ccx.l3.cacheWaysLog2

  val victimWay = RegInit(0.U(ccx.l3.cacheWaysLog2.W))

  when(io.command.clear) {
    victimWay := 0.U
  } .elsewhen(io.command.increment) {
    victimWay := Mux(victimWay === (ways - 1).U, 0.U, victimWay + 1.U)
  }

  io.status.victimWay := victimWay
}
