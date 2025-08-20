package armleocpu

import chisel3._
import chisel3.util._

import armleocpu.busConst._

class CacheWriteThroughEntry(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  import ccx._, cp._
  val addr      = UInt(apLen.W)
  val way       = UInt(waysLog2.W)
  val data      = Vec(cacheLineBytes, UInt(8.W))
  val mask      = Vec(cacheLineBytes, Bool())
}

class CacheWriteThroughIO(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  val queueEnq = Flipped(Decoupled(new CacheWriteThroughEntry))
  val bus      = new Bus
  val busy     = Output(Bool())
}

class CacheWriteThrough(depth: Int = 8)(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Module {
  val io = IO(new CacheWriteThroughIO)

  val queue = Module(new Queue(new CacheWriteThroughEntry, depth))
  queue.io.enq <> io.queueEnq

  val sIdle     = 0.U(2.W)
  val sWrite    = 1.U(2.W)
  val sWaitResp = 2.U(2.W)

  val state = RegInit(sIdle)
  val current = Reg(new CacheWriteThroughEntry)

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
      io.bus.ax.bits.strb := current.mask
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