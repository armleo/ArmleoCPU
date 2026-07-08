package armleocpu.l3cache

import armleocpu._
import armleocpu.Consts._
import armleocpu.memory.l3cache._
import chisel3._
import chisel3.util._
import chisel3.util.random.{FibonacciLFSR, XNOR}

/** Small synthesizable randomized DataArray tester with a Mem reference model. */
class DataArraySynthRandom(
  operationCount: Int = 5000,
  seed: BigInt = 0x12345
)(implicit ccx: CCXParams, bp: BusParams) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done = Output(Bool())
    val error = Output(Bool())
  })

  private val entries = 1 << ccx.l3.cacheEntriesLog2
  private val ways = 1 << ccx.l3.cacheWaysLog2
  private val tagWidth = bp.addrWidth - ccx.l3.cacheEntriesLog2 - cacheLineLog2
  private val entryWidth = log2Ceil(entries)
  private val wayWidth = log2Ceil(ways)

  require(entries >= 4)
  require(ways >= 4)

  val dut = Module(new DataArray)
  val reference = Mem(entries, Vec(ways, new Entry(tagWidth)))

  val random = FibonacciLFSR.maxPeriod(64, reduction = XNOR, seed = Some(seed))
  val randomTag = random(tagWidth - 1, 0)
  val randomEntry = random(tagWidth + entryWidth - 1, tagWidth)
  val randomWay = random(tagWidth + entryWidth + wayWidth - 1, tagWidth + entryWidth)
  val randomWrite = random(40)

  val generatedEntry = Wire(new Entry(tagWidth))
  generatedEntry.tag := randomTag
  generatedEntry.valid := true.B
  generatedEntry.dirty := random(41)
  generatedEntry.unique := random(42)
  generatedEntry.sharer := random(ccx.coreCount - 1, 0)
  generatedEntry.data := Fill((cacheLineBytes * 8) / random.getWidth, random)

  val initCount = RegInit(0.U(log2Ceil(entries * ways).W))
  val operation = RegInit(0.U(log2Ceil(operationCount + 1).W))
  val checkPending = RegInit(false.B)
  val savedExpectedHit = RegInit(false.B)
  val savedExpectedHitIdx = RegInit(0.U(wayWidth.W))
  val savedExpectedEntry = RegInit(0.U.asTypeOf(new Entry(tagWidth)))
  val error = RegInit(false.B)

  val sIdle :: sInit :: sRun :: sDrain :: sDone :: Nil = Enum(5)
  val state = RegInit(sIdle)

  dut.io.req.valid := false.B
  dut.io.req.bits := 0.U.asTypeOf(dut.io.req.bits)

  val invalidEntry = 0.U.asTypeOf(new Entry(tagWidth))
  val initEntry = initCount(log2Ceil(entries * ways) - 1, wayWidth)
  val initWay = initCount(wayWidth - 1, 0)

  val responseMismatch = WireDefault(false.B)
  when(!dut.io.resp.valid || dut.io.resp.bits.hit =/= savedExpectedHit) {
    responseMismatch := true.B
  }
  when(savedExpectedHit) {
    when(dut.io.resp.bits.hitIdx =/= savedExpectedHitIdx) {
      responseMismatch := true.B
    }
    when(dut.io.resp.bits.rdata(savedExpectedHitIdx).asUInt =/= savedExpectedEntry.asUInt) {
      responseMismatch := true.B
    }
  }

  switch(state) {
    is(sIdle) {
      when(io.start) {
        initCount := 0.U
        operation := 0.U
        checkPending := false.B
        error := false.B
        state := sInit
      }
    }

    is(sInit) {
      val mask = UIntToOH(initWay, ways)
      dut.io.req.valid := true.B
      dut.io.req.bits.addr := initEntry << cacheLineLog2
      dut.io.req.bits.write := true.B
      dut.io.req.bits.wayMask := mask
      dut.io.req.bits.wdata := invalidEntry
      reference.write(initEntry, VecInit(Seq.fill(ways)(invalidEntry)), mask.asBools)

      when(initCount === (entries * ways - 1).U) {
        state := sRun
      }.otherwise {
        initCount := initCount + 1.U
      }
    }

    is(sRun) {
      val mask = UIntToOH(randomWay, ways)
      val modeledWays = reference(randomEntry)
      val requestTag = Mux(randomWrite, randomTag, modeledWays(randomWay).tag)
      val expectedHits = modeledWays.map(entry => entry.valid && entry.tag === requestTag)
      val expectedHit = VecInit(expectedHits).asUInt.orR
      val expectedHitIdx = PriorityEncoder(expectedHits)

      // Score request N-1 while issuing request N.
      when(checkPending && responseMismatch) {
        error := true.B
      }

      dut.io.req.valid := true.B
      dut.io.req.bits.addr := Cat(requestTag, randomEntry, 0.U(cacheLineLog2.W))
      dut.io.req.bits.write := randomWrite
      dut.io.req.bits.wayMask := mask
      dut.io.req.bits.wdata := generatedEntry

      checkPending := !randomWrite
      savedExpectedHit := expectedHit
      savedExpectedHitIdx := expectedHitIdx
      savedExpectedEntry := modeledWays(expectedHitIdx)
      when(randomWrite) {
        reference.write(randomEntry, VecInit(Seq.fill(ways)(generatedEntry)), mask.asBools)
      }

      when(operation === (operationCount - 1).U) {
        state := sDrain
      }.otherwise {
        operation := operation + 1.U
      }
    }

    is(sDrain) {
      when(checkPending && responseMismatch) {
        error := true.B
      }
      state := sDone
    }

    is(sDone) {}
  }

  io.done := state === sDone
  io.error := error
}
