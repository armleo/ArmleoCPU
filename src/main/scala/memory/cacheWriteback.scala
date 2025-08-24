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
  val cacheArrayRead        = Flipped(new CacheArrayIO)
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
  val checkQueue = Module(new Queue(new CacheWritebackEntry, depth))
  val invalidationQueue = Module(new Queue(new CacheWritebackEntry, depth))
  queue.io.enq <> io.queueEnq

  /**************************************************************************/
  /* Defaults                                                               */
  /**************************************************************************/
  

  /**************************************************************************/
  /* Fixed                                                                  */
  /**************************************************************************/
  
  /*
  // Default outputs
  io.bus.req.valid      := false.B
  // FIXME: THe addr need to be set properly
  io.bus.req.bits.op    := RELEASE_DATA
  io.bus.req.bits.strb  := queue.io.deq.bits.strb
  io.bus.req.bits.data  := queue.io.deq.bits.data
  io.bus.resp.ready := false.B


  io.cacheArrayRead.req.valid := false.B
  io.cacheArrayRead.req.bits.addr := checkQueue.io.deq.bits.addr
  io.cacheArrayRead.req.bits.metaWrite  := false.B
  io.cacheArrayRead.req.bits.metaWdata  := VecInit(Seq.fill(cp.ways)(0.U.asTypeOf(new CacheMeta)))
  io.cacheArrayRead.req.bits.metaMask   := 0.U
  io.cacheArrayRead.req.bits.dataWrite  := false.B
  io.cacheArrayRead.req.bits.dataWdata  := VecInit(Seq.fill(cacheLineBytes)(0.U(8.W)))
  io.cacheArrayRead.req.bits.dataMask   := 0.U

  io.cacheArrayInvalidate.req.valid := false.B
  io.cacheArrayInvalidate.req.bits.addr := invalidationQueue.io.deq.bits.addr
  io.cacheArrayInvalidate.req.bits.metaWrite := false.B
  io.cacheArrayInvalidate.req.bits.metaWdata := VecInit(Seq.fill(cp.ways)(0.U.asTypeOf(new CacheMeta)))
  io.cacheArrayInvalidate.req.bits.metaMask  := 0.U
  io.cacheArrayInvalidate.req.bits.dataWrite := false.B
  io.cacheArrayInvalidate.req.bits.dataWdata := VecInit(Seq.fill(cacheLineBytes)(0.U(8.W)))
  io.cacheArrayInvalidate.req.bits.dataMask  := 0.U


  respQueue.io.enq.bits := queue.io.deq.bits

  when(queue.io.deq.valid && respQueue.io.count < depth) {
    // There is a writeback request AND respQueue has free space
    io.bus.req.valid      := true.B

    when(io.bus.req.ready) {
      respQueue.io.enq.valid := true.B
    }
  }


  checkQueue.io.enq.bits := respQueue.io.deq.bits
  when(respQueue.io.deq.valid && io.bus.resp.valid && checkQueue.io.count < depth.U) {
    // We got a response
    io.bus.resp.ready := true.B
    respQueue.io.deq.ready := true.B
    invalidationQueue.io.enq.valid := true.B
    assert(io.bus.resp.bits.resp(1, 0) === OKAY)

  }

  when(checkQueue.io.deq.valid) {
    // FIXME: Cache array request and check that address tag matches
    io.cacheArrayRead.req.valid := true.B
    
  }

   // FIXME: Add the invalidation queue
   */
}