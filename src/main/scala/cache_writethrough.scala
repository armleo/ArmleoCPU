package armleocpu

import chisel3._
import chisel3.util._

import armleocpu.bus_const_t._

class CacheWriteThroughEntry(ccx: CCXParams, cp: CacheParams) extends Bundle {
  val addr      = UInt(ccx.apLen.W)
  val way       = UInt(cp.waysLog2.W)
  val data      = Vec(1 << ccx.cacheLineLog2, UInt(8.W))
}

class CacheWriteThroughIO(ccx: CCXParams, cp: CacheParams) extends Bundle {
  val queueEnq = Flipped(Decoupled(new CacheWriteThroughEntry(ccx, cp)))
  val bus      = new dbus_t(ccx)
  val busy     = Output(Bool())
}

class CacheWriteThrough(ccx: CCXParams, cp: CacheParams, depth: Int = 8) extends Module {
  val io = IO(new CacheWriteThroughIO(ccx, cp))

  val queue = Module(new Queue(new CacheWriteThroughEntry(ccx, cp), depth))
  queue.io.enq <> io.queueEnq

  val sIdle     = 0.U(2.W)
  val sWrite    = 1.U(2.W)
  val sWaitResp = 2.U(2.W)

  val state = RegInit(sIdle)
  val current = Reg(new CacheWriteThroughEntry(ccx, cp))

  // Default outputs
  io.bus.ax.valid := false.B
  io.bus.ax.bits := DontCare
  io.bus.r.ready := false.B
  io.busy := (state =/= sIdle)

  switch(state) {
    is(sIdle) {
      when(queue.io.deq.valid) {
        current := queue.io.deq.bits
        queue.io.deq.ready := true.B
        state := sWrite
      } .otherwise {
        queue.io.deq.ready := false.B
      }
    }
    is(sWrite) {
      io.bus.ax.valid := true.B
      io.bus.ax.bits.addr := current.addr
      io.bus.ax.bits.op := OP_WRITETHROUGH
      io.bus.ax.bits.strb := Fill(1 << ccx.cacheLineLog2, 1.U(1.W))
      io.bus.ax.bits.data := current.data.asUInt
      when(io.bus.ax.ready) {
        state := sWaitResp
      }
    }
    is(sWaitResp) {
      io.bus.r.ready := true.B
      when(io.bus.r.valid) {
        state := sIdle
      }
    }
  }
}