package armleocpu.memory.l3cache

import chisel3._
import chisel3.util._
import armleocpu._
import busConst._


class SnoopResponseCommand(implicit val ccx: CCXParams) extends Bundle {
  val targets = UInt(ccx.coreCount.W)
}

class SnoopResponseStatus(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val busy = Bool()
  val done = Bool()
  val responded = UInt(ccx.coreCount.W)
  val dataExpected = UInt(ccx.coreCount.W)
  val dataReceived = UInt(ccx.coreCount.W)
  val dirty = UInt(ccx.coreCount.W)
  val hasData = Bool()
  val data = UInt((cbp.coherentDataBytes * 8).W)
}

class SnoopResponseIO(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val command = Flipped(Decoupled(new SnoopResponseCommand))
  val cresp = Flipped(Vec(ccx.coreCount, Decoupled(new CoherenceResponse)))
  val cdata = Flipped(Vec(ccx.coreCount, Decoupled(new CoherenceData)))
  val status = Output(new SnoopResponseStatus)
}

class SnoopResponse(implicit ccx: CCXParams, cbp: CoherentBusParams) extends Module {
  val io = IO(new SnoopResponseIO)

  val active = RegInit(false.B)
  val targets = RegInit(0.U(ccx.coreCount.W))
  val responded = RegInit(0.U(ccx.coreCount.W))
  val dataExpected = RegInit(0.U(ccx.coreCount.W))
  val dataReceived = RegInit(0.U(ccx.coreCount.W))
  val dirty = RegInit(0.U(ccx.coreCount.W))
  val data = RegInit(0.U((cbp.coherentDataBytes * 8).W))
  val hasData = RegInit(false.B)

  val allResponsesReceived = (responded & targets) === targets
  val expectedDataTargets = dataExpected & targets
  val allDataReceived = (dataReceived & expectedDataTargets) === expectedDataTargets
  val done = active && allResponsesReceived && allDataReceived

  val selected = Wire(Vec(ccx.coreCount, Bool()))
  val responsePending = Wire(Vec(ccx.coreCount, Bool()))
  val dataPending = Wire(Vec(ccx.coreCount, Bool()))
  val responseFireMask = Wire(Vec(ccx.coreCount, Bool()))
  val dataFireMask = Wire(Vec(ccx.coreCount, Bool()))
  val responseDataExpectedMask = Wire(Vec(ccx.coreCount, Bool()))
  val responseDirtyMask = Wire(Vec(ccx.coreCount, Bool()))

  io.command.ready := !active

  when(io.command.fire) {
    active := true.B
    targets := io.command.bits.targets
    responded := 0.U
    dataExpected := 0.U
    dataReceived := 0.U
    dirty := 0.U
    hasData := false.B
  } .elsewhen(done) {
    active := false.B
  } .elsewhen(active) {
    responded := responded | responseFireMask.asUInt
    dataExpected := dataExpected | responseDataExpectedMask.asUInt
    dataReceived := dataReceived | dataFireMask.asUInt
    dirty := dirty | responseDirtyMask.asUInt
  }

  for (idx <- 0 until ccx.coreCount) {
    selected(idx) := targets(idx)
    responsePending(idx) := selected(idx) && !responded(idx)
    dataPending(idx) := selected(idx) && dataExpected(idx) && !dataReceived(idx)

    io.cresp(idx).ready := active && responsePending(idx)
    io.cdata(idx).ready := active && dataPending(idx)

    responseFireMask(idx) := io.cresp(idx).fire
    dataFireMask(idx) := io.cdata(idx).fire
    responseDataExpectedMask(idx) := io.cresp(idx).fire && io.cresp(idx).bits.resp(RETURNDATABITNUM)
    responseDirtyMask(idx) := io.cresp(idx).fire && io.cresp(idx).bits.resp(DIRTYBITNUM)

    when(io.cdata(idx).fire) {
      data := io.cdata(idx).bits.data
      hasData := true.B
    }
  }

  io.status.busy := active
  io.status.done := done
  io.status.responded := responded
  io.status.dataExpected := dataExpected
  io.status.dataReceived := dataReceived
  io.status.dirty := dirty
  io.status.hasData := hasData
  io.status.data := data
}
