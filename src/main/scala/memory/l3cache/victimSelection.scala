package armleocpu.memory.l3cache

import armleocpu._
import chisel3._
import chisel3.util._


class VictimSelectionCommand extends Bundle {
  val increment = Bool()
  val clear = Bool()
}

class VictimSelectionStatus(implicit val ccx: CCXParams) extends Bundle {
  val victimWay = UInt(ccx.l3.cacheWaysLog2.W)
}

class VictimSelectionIO(implicit val ccx: CCXParams, implicit val bp: BusParams) extends Bundle {
  val command = Input(new VictimSelectionCommand)
  val status = Output(new VictimSelectionStatus)
}

class VictimSelection(implicit ccx: CCXParams, bp: BusParams) extends Module {
  val io = IO(new VictimSelectionIO)

  private val ways = 1 << ccx.l3.cacheWaysLog2

  val victimWay = RegInit(0.U(ccx.l3.cacheWaysLog2.W))

  when(io.command.clear) {
    victimWay := 0.U
  } .elsewhen(io.command.increment) {
    victimWay := Mux(victimWay === (ways - 1).U, 0.U, victimWay + 1.U)
  }

  io.status.victimWay := victimWay
}
