package armleocpu

import chisel3._
import chisel3.util._

class CacheResetIO(ccx: CCXParams, cp: CacheParams) extends Bundle {
  val cplt  = Output(Bool())
  val req   = Decoupled(new CacheArrayReq(ccx, cp))
}

class CacheReset(ccx: CCXParams, cp: CacheParams) extends Module {
  val io = IO(new CacheResetIO(ccx, cp))

  val entryCount = cp.entries
  val wayCount   = cp.ways
  val idx        = RegInit(0.U(log2Ceil(entryCount).W))
  val busy       = RegInit(true.B) // Start busy after reset

  // Default meta: all invalid
  val invalidMeta = Wire(Vec(wayCount, new CacheMeta(ccx, cp)))
  for (w <- 0 until wayCount) {
    invalidMeta(w).valid  := false.B
    invalidMeta(w).ptag   := "hDEADBEEF".U
  }

  io.req.valid := busy
  io.req.bits.addr := idx << ccx.cacheLineLog2
  io.req.bits.metaWrite := true.B
  io.req.bits.metaWdata := invalidMeta
  io.req.bits.metaMask  := Fill(wayCount, 1.U(1.W))
  io.req.bits.dataWrite := true.B
  io.req.bits.dataWdata := VecInit(Seq.fill(1 << ccx.cacheLineLog2)("hDE".U(8.W)))
  io.req.bits.dataMask  := VecInit(Seq.fill(1 << ccx.cacheLineLog2)(true.B))

  io.cplt := !busy

  when(busy && io.req.ready) {
    when(idx === (entryCount-1).U) {
      busy := false.B
    } .otherwise {
      idx := idx + 1.U
    }
  }
}