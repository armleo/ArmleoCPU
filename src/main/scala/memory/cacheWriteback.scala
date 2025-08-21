package armleocpu

import chisel3._
import chisel3.util._

import armleocpu.busConst._

class CacheWritebackEntry(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  import ccx._, cp._
  val addr      = UInt(apLen.W)
  val wayIdx    = UInt(waysLog2.W)
}

class CacheWritebackIO(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  val queueEnq    = Flipped(Decoupled(new CacheWritebackEntry))
  val cacheArrayRead  = Flipped(new CacheArrayIO)
  val cacheArrayInvalidate  = Flipped(new CacheArrayIO)
  val bus         = new Bus
  val busy        = Output(Bool())
}

class CacheWriteback(depth: Int = 8)(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Module {
  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/
  
  val io = IO(new CacheWritebackIO)

  /**************************************************************************/
  /* Queues                                                                 */
  /**************************************************************************/
  
  val queue = Module(new Queue(new CacheWritebackEntry, depth))
  val respQueue = Module(new Queue(new CacheWritebackEntry, depth))
  val invalidationQueue = Module(new Queue(new CacheWritebackEntry, depth))
  queue.io.enq <> io.queueEnq

  /**************************************************************************/
  /* Defaults                                                               */
  /**************************************************************************/
  

  /**************************************************************************/
  /* Fixed                                                                  */
  /**************************************************************************/
  
  // Default outputs
  io.bus.req.valid      := false.B
  io.bus.req.bits.op    := RELEASE_DATA
  io.bus.req.bits.strb  := queue.io.deq.bits.strb
  io.bus.req.bits.data  := queue.io.deq.bits.data
  io.bus.resp.ready := false.B


  io.cacheArray.req.valid := false.B
  io.cacheArray.req.bits := DontCare


  when(queue.io.deq.valid && respQueue.io.count < depth) {
    // There is a writeback request AND respQueue has free space
    io.bus.req.valid      := true.B

    when(io.bus.req.ready) {
      respQueue.io.enq.valid := true.B
      // FIXME: respQueue.io.enq.bits := 
    }
  }



  when(respQueue.io.deq.valid && io.bus.resp.valid && invalidationQueue.io.count) {
    // We got a response
  }

  when(invalidationQueue.io.deq.valid) {
    // FIXME: Cache array request


  }

    is(sWaitResp) {
      io.bus.r.ready := true.B
      when(io.bus.r.valid) {
        // On response, check tag from cache array
        io.cacheArray.req.valid := true.B
        io.cacheArray.req.bits.addr := current.addr
        io.cacheArray.req.bits.metaWrite := false.B
        io.cacheArray.req.bits.metaWdata := VecInit(Seq.fill(cp.ways)(0.U.asTypeOf(new CacheMeta(ccx, cp))))
        io.cacheArray.req.bits.metaMask  := 0.U
        io.cacheArray.req.bits.dataWrite := false.B
        io.cacheArray.req.bits.dataWdata := VecInit(Seq.fill(cacheLineBytes)(0.U(8.W)))
        io.cacheArray.req.bits.dataMask  := 0.U
        when(io.cacheArray.req.ready) {
          cacheRespReg := io.cacheArray.resp.bits
          state := sInvalidate
        }
      }
    }
    is(sInvalidate) {
      // Invalidate only if tag matches
      val cacheMeta = cacheRespReg.metaRdata(current.wayIdx)
      when(cacheMeta.ptag === current.tag && cacheMeta.valid) {
        io.cacheArray.req.valid := true.B
        io.cacheArray.req.bits.addr := current.addr
        io.cacheArray.req.bits.metaWrite := true.B
        val newMeta = Wire(Vec(cp.ways, new CacheMeta(ccx, cp)))
        for (w <- 0 until cp.ways) {
          newMeta(w) := cacheRespReg.metaRdata(w)
          when(w.U === current.wayIdx) {
            newMeta(w).valid := false.B
            newMeta(w).dirty := false.B
          }
        }
        io.cacheArray.req.bits.metaWdata := newMeta
        io.cacheArray.req.bits.metaMask := (1.U << current.wayIdx)
        io.cacheArray.req.bits.dataWrite := false.B
        io.cacheArray.req.bits.dataWdata := VecInit(Seq.fill(cacheLineBytes)(0.U(8.W)))
        io.cacheArray.req.bits.dataMask := 0.U
        when(io.cacheArray.req.ready) {
          state := sIdle
        }
      } .otherwise {
        // Tag mismatch, skip invalidation
        state := sIdle
      }
    }
  }
}