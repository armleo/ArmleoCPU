package armleocpu.memory.l3cache

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.busConst._
import addressUtils._

class WritebackReq(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val addr = UInt(cbp.addrWidth.W)
  val entry = new Entry(cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2)
}

class WritebackResp(implicit val cbp: CoherentBusParams) extends Bundle {
  val resp = UInt(8.W)
}

class Writebacker(implicit ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Valid(new WritebackReq))
    val resp = Valid(new WritebackResp)
    val down = new ReadWriteBus()(cbp)
  })

  val sIdle :: sSend :: sWaitB :: Nil = Enum(3)
  val state = RegInit(sIdle)

  val savedReq = Reg(new WritebackReq)
  val awDone = RegInit(false.B)
  val wDone = RegInit(false.B)

  val activeReq = Wire(new WritebackReq)
  activeReq := savedReq
  when(state === sIdle) {
    activeReq := io.req.bits
  }

  val writebackAddr = Cat(
    activeReq.entry.tag,
    getCacheEntryIdx(activeReq.addr),
    0.U(ccx.cacheLineLog2.W)
  )

  when(state =/= sIdle) {
    assert(!io.req.valid)
  }

  io.resp.valid := state === sWaitB && io.down.b.valid
  io.resp.bits.resp := io.down.b.bits.resp

  io.down.ar.valid := false.B
  io.down.ar.bits := 0.U.asTypeOf(io.down.ar.bits)
  io.down.r.ready := true.B

  io.down.aw.valid := (state === sIdle && io.req.valid) || (state === sSend && !awDone)
  io.down.aw.bits := 0.U.asTypeOf(io.down.aw.bits)
  io.down.aw.bits.addr := writebackAddr
  io.down.aw.bits.op := WriteOnce

  io.down.w.valid := (state === sIdle && io.req.valid) || (state === sSend && !wDone)
  io.down.w.bits := 0.U.asTypeOf(io.down.w.bits)
  io.down.w.bits.data := activeReq.entry.data
  io.down.w.bits.strb := Fill(cbp.busBytes, 1.U(1.W))
  io.down.w.bits.last := true.B

  io.down.b.ready := state === sWaitB

  switch(state) {
    is(sIdle) {
      when(io.req.valid) {
        savedReq := io.req.bits
        awDone := io.down.aw.fire
        wDone := io.down.w.fire
        when(io.down.aw.fire && io.down.w.fire) {
          state := sWaitB
        } .otherwise {
          state := sSend
        }
      }
    }

    is(sSend) {
      when(io.down.aw.fire) {
        awDone := true.B
      }
      when(io.down.w.fire) {
        wDone := true.B
      }
      when((awDone || io.down.aw.fire) && (wDone || io.down.w.fire)) {
        state := sWaitB
      }
    }

    is(sWaitB) {
      when(io.down.b.fire) {
        state := sIdle
      }
    }
  }
}
