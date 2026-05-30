package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._
import armleocpu._

class Writer(addrWidth: Int, busBytes: Int)(implicit bp: BusParams) extends Module {
  val io = IO(new WriterIO(addrWidth, busBytes))

  val active = RegInit(false.B)
  val bValid = RegInit(false.B)
  val aligned = if (busBytes == 1) true.B else io.aw.bits.addr(log2Ceil(busBytes) - 1, 0) === 0.U
  val start = io.aw.valid && aligned && !active
  val writeBeat = active && io.w.fire
  val writeLast = writeBeat && io.requestKeeper.last
  val done = io.b.fire

  io.aw.ready := aligned && !active
  io.w.ready := active && !bValid

  io.b.valid := bValid
  io.b.bits := 0.U.asTypeOf(io.b.bits)
  io.b.bits.resp := io.resp
  io.b.bits.id := io.requestKeeper.id

  io.dataArrayReq.addr := io.burstManager.addr
  io.dataArrayReq.read := false.B
  io.dataArrayReq.write := writeBeat
  io.dataArrayReq.wdata := io.w.bits.data
  io.dataArrayReq.wmask := io.w.bits.strb

  io.requestKeeper.stage.start := start
  io.requestKeeper.request := 0.U.asTypeOf(io.requestKeeper.request)
  io.requestKeeper.request.id := io.aw.bits.id
  io.requestKeeper.request.len := io.aw.bits.len
  io.requestKeeper.decrement := writeBeat

  io.burstManager.stage.start := start
  io.burstManager.requestAddr := io.aw.bits.addr
  io.burstManager.saveIncrementedAddr := writeBeat && !io.requestKeeper.last
  io.burstManager.finish := done

  when(start) {
    active := true.B
  }

  when(writeLast) {
    bValid := true.B
  }

  when(done) {
    active := false.B
    bValid := false.B
  }

  io.starting := start
  io.active := active
  io.done := done
}
