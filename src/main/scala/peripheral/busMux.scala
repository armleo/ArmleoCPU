package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._

// Both muxes assume that downstream is okay with data changing
// It also assumes AW has to be accepted first

// TODO: Fair arbiter

class ReadBusMux[T <: ReadBus](t: T, n: Int, roundRobin: Boolean, depth: Int = 2, noise: Boolean = true)(implicit ccx: CCXParams) extends CCXModule {
  val masterBits = log2Ceil(n)

  val downstreamBp = new BusParams(
    idWidth = t.bp.idWidth + masterBits,
    addrWidth = t.bp.addrWidth,
    lenWidth = t.bp.lenWidth,
    busBytes = t.bp.busBytes
  )

  val io = IO(new Bundle {
    val upstream   = Vec(n, Flipped(t.cloneType)) // masters
    val downstream = new ReadBus()(downstreamBp)                  // slave
  })


  // === ID augmentation ===
  // Assume each upstream ID fits in (origIdBits).
  // We extend it by prefixing master index bits.
  val idBits     = t.ar.bits.id.getWidth
  def makeDownId(idx: Int, upId: UInt) = Cat(idx.U(masterBits.W), upId)
  def splitDownId(downId: UInt) = {
    val upIdWidth = idBits
    val mIdx = downId(idBits + masterBits - 1, idBits)
    val upId = downId(upIdWidth - 1, 0)
    (mIdx, upId)
  }

  /**************************************************************************/
  /* AR mux (Arbiter)                                                       */
  /**************************************************************************/
  val arb = if (roundRobin) Module(new RRArbiter(t.ar.bits.cloneType, n)).io else Module(new Arbiter(t.ar.bits.cloneType, n)).io
  for (i <- 0 until n) {
    arb.in(i) <> io.upstream(i).ar

    // Modify ARID before sending downstream
    arb.in(i).bits.id := makeDownId(i, io.upstream(i).ar.bits.id)
  }

  io.downstream.ar <> arb.out

  /**************************************************************************/
  /* R demux (based on RID)                                                 */
  /**************************************************************************/
  for (i <- 0 until n) {
    io.upstream(i).r.valid := false.B
    io.upstream(i).r.bits  := (if (noise)
      FibonacciLFSR.maxPeriod(io.upstream(i).r.bits.asUInt.getWidth, reduction = XNOR)
    else (0.U).asTypeOf(io.upstream(i).r.bits))
  }

  io.downstream.r.ready := false.B

  // Decode downstream RID into (masterIdx, origId)
  println(io.downstream.r.bits.id.getWidth)
  val (mIdx, origId) = splitDownId(io.downstream.r.bits.id)

  // Default: downstream ready only when selected master ready
  io.downstream.r.ready := io.upstream(mIdx).r.ready

  // Route to correct master
  io.upstream(mIdx).r.valid     := io.downstream.r.valid
  io.upstream(mIdx).r.bits      := io.downstream.r.bits
  io.upstream(mIdx).r.bits.id   := origId
}


class WriteBusMux[T <: WriteBus](t: T, n: Int, depth: Int = 2, noise: Boolean = true)(implicit ccx: CCXParams) extends CCXModule {
  val masterBits = log2Ceil(n)

  val downstreamBp = new BusParams(
    idWidth = t.bp.idWidth + masterBits,
    addrWidth = t.bp.addrWidth,
    lenWidth = t.bp.lenWidth,
    busBytes = t.bp.busBytes
  )

  val io = IO(new Bundle {
    val upstream   = Vec(n, Flipped(t.cloneType)) // masters
    val downstream = new WriteBus()(downstreamBp)                  // slave
  })



  val awSelQ = Module(new Queue(UInt(masterBits.W), depth))



  // === ID augmentation ===
  val idBits     = t.aw.bits.id.getWidth
  

  def makeDownId(idx: Int, upId: UInt) = Cat(idx.U(masterBits.W), upId)
  def splitDownId(downId: UInt) = {
    val mIdx   = downId(idBits + masterBits - 1, idBits)
    val upId   = downId(idBits - 1, 0)
    (mIdx, upId)
  }

  /**************************************************************************/
  /* AW mux                                                                 */
  /**************************************************************************/
  val awArb = Module(new Arbiter(t.aw.bits.cloneType, n))
  for (i <- 0 until n) {
    awArb.io.in(i) <> io.upstream(i).aw
    awArb.io.in(i).bits.id := makeDownId(i, io.upstream(i).aw.bits.id)

    // Block upstream if awSelQ is full
    awArb.io.in(i).valid := io.upstream(i).aw.valid && awSelQ.io.enq.ready
  }
  io.downstream.aw <> awArb.io.out

  // Track which master index each AW came from
  
  awSelQ.io.enq.valid := awArb.io.out.valid && awArb.io.out.ready
  awSelQ.io.enq.bits  := awArb.io.chosen

  // downstream.aw.ready must also depend on awSelQ availability
  awArb.io.out.ready := io.downstream.aw.ready && awSelQ.io.enq.ready


  /**************************************************************************/
  /* W mux (coupled with AW)                                                */
  /**************************************************************************/
  val wSel = RegInit(0.U(masterBits.W))
  val wActive = RegInit(false.B)

  // Start W burst when AW is accepted
  when(awSelQ.io.deq.valid && !wActive) {
    wSel    := awSelQ.io.deq.bits
    wActive := true.B
  }

  // Default downstream W inactive
  io.downstream.w.valid := false.B
  io.downstream.w.bits  := 0.U.asTypeOf(t.w.bits)

  for (i <- 0 until n) {
    io.upstream(i).w.ready := false.B
  }

  awSelQ.io.deq.ready := false.B

  when(awSelQ.io.deq.valid) {
    val sel = awSelQ.io.deq.bits
    io.downstream.w <> io.upstream(sel).w

    // End of burst -> release slot
    when(io.downstream.w.valid && io.downstream.w.ready && io.downstream.w.bits.last) {
      awSelQ.io.deq.ready := true.B
    }
  }

  /**************************************************************************/
  /* B demux (ID-based)                                                     */
  /**************************************************************************/
  for (i <- 0 until n) {
    io.upstream(i).b.valid := false.B
    io.upstream(i).b.bits  := (if (noise)
      FibonacciLFSR.maxPeriod(io.upstream(i).b.bits.asUInt.getWidth, reduction = XNOR)
    else 0.U).asTypeOf(io.upstream(i).b.bits)
  }
  io.downstream.b.ready := false.B

  val (bMidx, bOrigId) = splitDownId(io.downstream.b.bits.id)

  io.downstream.b.ready     := io.upstream(bMidx).b.ready
  io.upstream(bMidx).b.valid := io.downstream.b.valid
  io.upstream(bMidx).b.bits  := io.downstream.b.bits
  io.upstream(bMidx).b.bits.id := bOrigId
}


class ReadWriteBusMux[T <: ReadWriteBus](t: T, n: Int, depth: Int = 2, noise: Boolean = true)(implicit ccx: CCXParams) extends CCXModule {

}

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


import chisel3.stage._
object dbusMux_generator extends App {
  implicit val ccx:CCXParams = new CCXParams
  // Temorary disable memory configs as yosys does not know what to do with them
  // (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  ChiselStage.emitSystemVerilogFile(
  new ReadBusMux(new ReadBus()(new BusParams(2, 1, 2, 1)), 2, roundRobin = false, noise = false),
    Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
    Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}