package armleocpu.memory.l3cache

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.busConst._
import addressUtils._

class DownstreamReq(implicit val cbp: CoherentBusParams) extends Bundle {
  val addr = UInt(cbp.addrWidth.W)
  val op   = UInt(8.W)
}

class DownstreamResp(implicit val cbp: CoherentBusParams) extends Bundle {
  val addr = UInt(cbp.addrWidth.W)
  val data = UInt((cbp.coherentDataBytes * 8).W)
  val resp = UInt(8.W)
  val last = Bool()
}

/**
 * Start a downstream AR on request and when the R beat comes back
 * present a response containing addr + r.payload.
 */
class DownstreamRequester(implicit ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Module {
  val io = IO(new Bundle {
    val req  = Flipped(Decoupled(new DownstreamReq()(cbp)))
    val resp = Decoupled(new DownstreamResp()(cbp))
    val down = Flipped(new ReadWriteBus()(cbp))
  })

  // Simple FSM
  val sIdle :: sSent :: sWaitR :: Nil = Enum(3)
  val state = RegInit(sIdle)

  val savedAddr = Reg(UInt(cbp.addrWidth.W))

  // Default outputs
  io.req.ready := false.B
  io.down.ar.valid := false.B
  io.down.ar.bits := 0.U.asTypeOf(io.down.ar.bits)
  io.resp.valid := false.B
  io.resp.bits := 0.U.asTypeOf(io.resp.bits)

  // Pass through unused downstream channels
  io.down.aw.valid := false.B
  io.down.aw.bits := 0.U.asTypeOf(io.down.aw.bits)
  io.down.w.valid := false.B
  io.down.w.bits := 0.U.asTypeOf(io.down.w.bits)
  io.down.b.ready := true.B
  io.down.r.ready := true.B // we will sample down.r when valid

  switch(state) {
    is(sIdle) {
      io.req.ready := true.B
      when(io.req.valid) {
        savedAddr := io.req.bits.addr
        // build AR payload
        io.down.ar.valid := true.B
        io.down.ar.bits.addr := io.req.bits.addr
        io.down.ar.bits.op := io.req.bits.op
        io.down.ar.bits.len := 0.U
        io.down.ar.bits.id := 0.U
        when(io.down.ar.ready) {
          state := sWaitR
        } .otherwise {
          state := sSent
        }
      }
    }
    is(sSent) {
      // complete AR handshake
      io.down.ar.valid := true.B
      when(io.down.ar.ready) {
        state := sWaitR
      }
    }
    is(sWaitR) {
      when(io.down.r.valid) {
        // produce response (addr + r)
        io.resp.valid := true.B
        io.resp.bits.addr := savedAddr
        io.resp.bits.data := io.down.r.bits.data
        io.resp.bits.resp := io.down.r.bits.resp
        io.resp.bits.last := io.down.r.bits.last

        when(io.resp.ready) {
          state := sIdle
        } .otherwise {
          // hold until consumer accepts
          state := sWaitR
          // keep io.resp.valid asserted next cycle
        }
      }
    }
  }
}

