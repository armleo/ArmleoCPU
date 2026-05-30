package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._
import armleocpu._

class Reader(addrWidth: Int, busBytes: Int)(implicit bp: BusParams) extends Module {
  val io = IO(new ReaderIO(addrWidth, busBytes))

  val active = RegInit(false.B)
  val rValid = RegInit(false.B)
  val done = io.r.fire && io.requestKeeper.last
  val aligned = if (busBytes == 1) true.B else io.ar.bits.addr(log2Ceil(busBytes) - 1, 0) === 0.U
  val start = io.ar.valid && !io.writePriority && aligned && !active
  val readNext = io.r.fire && !io.requestKeeper.last

  io.ar.ready := !io.writePriority && aligned && !active

  io.r.valid := rValid
  io.r.bits := 0.U.asTypeOf(io.r.bits)
  io.r.bits.data := io.dataArrayResp.rdata
  io.r.bits.resp := io.resp
  io.r.bits.id := io.requestKeeper.id
  io.r.bits.last := io.requestKeeper.last

  io.dataArrayReq.addr := Mux(start, io.ar.bits.addr, io.burstManager.incrementedAddr)
  io.dataArrayReq.read := start || readNext
  io.dataArrayReq.write := false.B
  io.dataArrayReq.wdata := 0.U
  io.dataArrayReq.wmask := 0.U

  io.requestKeeper.stage.start := start
  io.requestKeeper.request := 0.U.asTypeOf(io.requestKeeper.request)
  io.requestKeeper.request.id := io.ar.bits.id
  io.requestKeeper.request.len := io.ar.bits.len
  io.requestKeeper.decrement := io.r.fire

  io.burstManager.stage.start := start
  io.burstManager.requestAddr := io.ar.bits.addr
  io.burstManager.saveIncrementedAddr := readNext
  io.burstManager.finish := done

  when(start) {
    active := true.B
    rValid := true.B
  } .elsewhen(done) {
    active := false.B
    rValid := false.B
  }

  io.starting := start
  io.active := active
  io.done := done
}
