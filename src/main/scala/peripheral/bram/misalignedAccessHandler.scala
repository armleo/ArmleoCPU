package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.busConst._

class MisalignedAccessHandler(implicit bp: BusParams) extends Module {
  val io = IO(new MisalignedAccessHandlerIO)

  val sIdle :: sRead :: sWrite :: sWriteResp :: Nil = Enum(4)
  val state = RegInit(sIdle)

  val id = Reg(UInt(bp.idWidth.W))
  val burstRemaining = Reg(UInt((bp.lenWidth + 1).W))
  val active = state =/= sIdle
  val awMisaligned = if (bp.busBytes == 1) false.B else io.aw.bits.addr(log2Ceil(bp.busBytes) - 1, 0) =/= 0.U
  val arMisaligned = if (bp.busBytes == 1) false.B else io.ar.bits.addr(log2Ceil(bp.busBytes) - 1, 0) =/= 0.U
  val startingWrite = state === sIdle && io.aw.valid && awMisaligned
  val startingRead = state === sIdle && !io.aw.valid && io.ar.valid && arMisaligned
  val readDone = io.r.fire && burstRemaining === 0.U
  val writeDone = io.w.fire && io.w.bits.last
  val bDone = io.b.fire

  io.ar.ready := startingRead
  io.aw.ready := startingWrite
  io.w.ready := state === sWrite

  io.r.valid := state === sRead
  io.r.bits := 0.U.asTypeOf(io.r.bits)
  io.r.bits.resp := DECERR
  io.r.bits.id := id
  io.r.bits.last := burstRemaining === 0.U

  io.b.valid := state === sWriteResp
  io.b.bits := 0.U.asTypeOf(io.b.bits)
  io.b.bits.resp := DECERR
  io.b.bits.id := id

  when(io.aw.fire) {
    id := io.aw.bits.id
    burstRemaining := io.aw.bits.len
    state := sWrite
  } .elsewhen(io.ar.fire) {
    id := io.ar.bits.id
    burstRemaining := io.ar.bits.len
    state := sRead
  } .elsewhen(state === sRead && io.r.fire) {
    burstRemaining := burstRemaining - 1.U
    when(readDone) {
      state := sIdle
    }
  } .elsewhen(state === sWrite && io.w.fire) {
    burstRemaining := burstRemaining - 1.U
    when(writeDone) {
      state := sWriteResp
    }
  } .elsewhen(state === sWriteResp && bDone) {
    state := sIdle
  }

  io.starting := startingWrite || startingRead
  io.active := active
  io.done := readDone || bDone
}
