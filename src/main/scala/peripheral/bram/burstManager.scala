package armleocpu.peripheral.bram

import chisel3._
import armleocpu._

class BurstManager(implicit bp: BusParams) extends Module {
  val io = IO(new BurstManagerIO)

  val active = RegInit(false.B)
  val addr = Reg(UInt(bp.addrWidth.W))
  val incrementedAddr = addr + bp.busBytes.U

  when(io.stage.start) {
    active := true.B
    addr := io.requestAddr
  } .elsewhen(io.finish) {
    active := false.B
  } .elsewhen(active && io.saveIncrementedAddr) {
    addr := incrementedAddr
  }

  io.stage.active := active
  io.stage.done := io.finish
  io.addr := Mux(io.stage.start, io.requestAddr, addr)
  io.incrementedAddr := Mux(io.stage.start, io.requestAddr + bp.busBytes.U, incrementedAddr)
}
