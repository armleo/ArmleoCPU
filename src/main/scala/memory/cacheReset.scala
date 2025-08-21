package armleocpu

import chisel3._
import chisel3.util._

class CacheResetIO(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  val cplt  = Output(Bool())
  val req   = Input(Bool())
  val array   = Decoupled(new CacheArrayReq)
}

class CacheReset(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Module {
  val io = IO(new CacheResetIO)

  val entryCount = cp.entries
  val wayCount   = cp.ways
  val idx        = RegInit(0.U(log2Ceil(entryCount).W))
  val busy       = RegInit(true.B) // Start busy after reset

  when(io.req) {
    busy := true.B
  }

  // Default meta: all invalid
  val invalidMeta = Wire(Vec(wayCount, new CacheMeta))
  for (w <- 0 until wayCount) {
    invalidMeta(w).valid  := false.B
    invalidMeta(w).ptag   := "hDDEADDEADBEEF".U
  }

  io.array.valid := busy
  io.array.bits.addr := idx << ccx.cacheLineLog2
  io.array.bits.metaWrite := true.B
  io.array.bits.metaWdata := invalidMeta
  io.array.bits.metaMask  := Fill(wayCount, 1.U(1.W))
  io.array.bits.dataWrite := true.B
  io.array.bits.dataWdata := VecInit(Seq.fill(1 << ccx.cacheLineLog2)("hDE".U(8.W)))
  io.array.bits.dataMask  := VecInit(Seq.fill(1 << ccx.cacheLineLog2)(true.B))

  io.cplt := false.B
  
  when(busy && io.array.ready) {
    when(idx === (entryCount-1).U) {
      busy := false.B
      io.cplt := true.B
    } .otherwise {
      idx := idx + 1.U
    }
  }
}