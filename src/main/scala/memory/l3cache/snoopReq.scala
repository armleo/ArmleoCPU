package armleocpu

import chisel3._
import chisel3.util._
import busConst._

class SnoopRequestCommand(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val addr = UInt(cbp.addrWidth.W)
  val targets = UInt(ccx.coreCount.W)
  val invalidate = Bool()
}

class SnoopRequestStatus(implicit val ccx: CCXParams) extends Bundle {
  val busy = Bool()
  val done = Bool()
  val sent = UInt(ccx.coreCount.W)
}

class SnoopRequestIO(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val command = Flipped(Decoupled(new SnoopRequestCommand))
  val creq = Vec(ccx.coreCount, Decoupled(new CoherenceRequest))
  val status = Output(new SnoopRequestStatus)
}

class SnoopRequest(implicit ccx: CCXParams, cbp: CoherentBusParams) extends Module {
  val io = IO(new SnoopRequestIO)

  val active = RegInit(false.B)
  val addr = Reg(UInt(cbp.addrWidth.W))
  val targets = Reg(UInt(ccx.coreCount.W))
  val sent = RegInit(0.U(ccx.coreCount.W))
  val invalidate = RegInit(false.B)

  val allSent = (sent & targets) === targets
  val done = active && allSent
  val selected = Wire(Vec(ccx.coreCount, Bool()))
  val alreadySent = Wire(Vec(ccx.coreCount, Bool()))
  val fireMask = Wire(Vec(ccx.coreCount, Bool()))

  io.command.ready := !active

  when(io.command.fire) {
    active := true.B
    addr := io.command.bits.addr
    targets := io.command.bits.targets
    sent := 0.U
    invalidate := io.command.bits.invalidate
  } .elsewhen(done) {
    active := false.B
  } .elsewhen(active) {
    sent := sent | fireMask.asUInt
  }

  for (idx <- 0 until ccx.coreCount) {
    selected(idx) := targets(idx)
    alreadySent(idx) := sent(idx)

    io.creq(idx).valid := active && selected(idx) && !alreadySent(idx)
    io.creq(idx).bits.addr := addr
    io.creq(idx).bits.op := Mux(invalidate, Invalidate, ReadUnique)
    fireMask(idx) := io.creq(idx).fire
  }

  io.status.busy := active
  io.status.done := done
  io.status.sent := sent
}
