package armleocpu.peripheral.bram

import chisel3._
import armleocpu._

class RequestKeeper(implicit bp: BusParams) extends Module {
  val io = IO(new RequestKeeperIO)

  val active = RegInit(false.B)
  val id = Reg(UInt(bp.idWidth.W))
  val burstRemaining = Reg(UInt((bp.lenWidth + 1).W))
  val last = burstRemaining === 0.U
  val finishing = active && io.decrement && last

  when(io.stage.start) {
    active := true.B
    id := io.request.id
    burstRemaining := io.request.len
  } .elsewhen(finishing) {
    active := false.B
  } .elsewhen(active && io.decrement) {
    burstRemaining := burstRemaining - 1.U
  }

  io.stage.active := active
  io.stage.done := finishing
  io.id := id
  io.burstRemaining := burstRemaining
  io.last := last
}
