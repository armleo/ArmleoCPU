package armleocpu.l3cache

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import chisel3.simulator.stimulus.{RunUntilFinished, RunUntilSuccess}
import chisel3.util._
import armleocpu.memory.l3cache._
import org.scalatest.funspec.AnyFunSpec
import svsim.{BackendSettingsModifications, CommonCompilationSettings, CommonSettingsModifications}
import svsim.CommonCompilationSettings.AvailableParallelism
import svsim.verilator.Backend.CompilationSettings.{TraceKind, TraceStyle}
import armleocpu._
import armleocpu.Consts._

class DataArraySVSimSpec extends AnyFunSpec with ChiselSim {
  describe("DataArray") {
    implicit val ccx: CCXParams = new CCXParams(
      l3 = new Params(
        cacheEntriesLog2 = 4,  // 16 entries
        cacheWaysLog2 = 2      // 4 ways
      )
    )
    implicit val bp: BusParams = new BusParams(addrWidth = 32)

    // Unbounded parallelism triggers a Verilator thread-pool bug, while one worker
    // makes each generated simulator take about a minute to compile.
    implicit val commonSettingsModifications: CommonSettingsModifications =
      (settings: CommonCompilationSettings) =>
        settings.copy(availableParallelism = AvailableParallelism.UpTo(4))

    implicit val backendSettingsModifications: BackendSettingsModifications = {
      case settings: svsim.verilator.Backend.CompilationSettings =>
        settings.withTraceStyle(Some(TraceStyle(kind = TraceKind.Fst())))
      case settings => settings
    }

    it("should exercise the DataArray across several scenarios on one simulator instance") {
      simulate(new DataArray) { dut =>
        enableWaves()

        val ways = 1 << ccx.l3.cacheWaysLog2
        val entries = 1 << ccx.l3.cacheEntriesLog2
        val wayMaskWidth = (1 << ccx.l3.cacheWaysLog2).W

        def writeEntry(addr: Int, way: Int, tag: Int, valid: Boolean = true, dirty: Boolean = false, unique: Boolean = false, sharer: Int = 0): Unit = {
          dut.io.req.valid.poke(true)
          dut.io.req.bits.addr.poke(addr.U(bp.addrWidth.W))
          dut.io.req.bits.write.poke(true)
          dut.io.req.bits.wayMask.poke((1 << way).U(wayMaskWidth))
          dut.io.req.bits.wdata.tag.poke(tag.U)
          dut.io.req.bits.wdata.valid.poke(valid.B)
          dut.io.req.bits.wdata.dirty.poke(dirty.B)
          dut.io.req.bits.wdata.unique.poke(unique.B)
          dut.io.req.bits.wdata.sharer.poke(sharer.U(ccx.coreCount.W))
          dut.clock.step(1)
        }

        def readEntry(addr: Int): Unit = {
          dut.io.req.valid.poke(true)
          dut.io.req.bits.addr.poke(addr.U(bp.addrWidth.W))
          dut.io.req.bits.write.poke(false)
          dut.io.req.bits.wayMask.poke(0.U(wayMaskWidth))
          dut.clock.step(1)
        }

        def clearState(): Unit = {
          for (entry <- 0 until entries) {
            val entryAddr = entry << cacheLineLog2
            for (way <- 0 until ways) {
              writeEntry(entryAddr, way, 0, valid = false)
            }
          }
        }

        def expectHit(addr: Int, expectedWay: Int, expectedTag: Int): Unit = {
          readEntry(addr)
          dut.io.resp.valid.expect(true)
          dut.io.resp.bits.hit.expect(true)
          dut.io.resp.bits.hitIdx.expect(expectedWay.U)
          dut.io.resp.bits.rdata(expectedWay).tag.expect(expectedTag.U)
          dut.io.resp.bits.rdata(expectedWay).valid.expect(true)
        }

        // Scenario 1: write and read back a cache entry.
        clearState()
        val addr1 = 0x1000
        val tag1 = addr1 >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2)
        writeEntry(addr1, 0, tag1, valid = true, sharer = 1)
        expectHit(addr1, 0, tag1)

        // Scenario 2: detect a cache hit when the entry is valid.
        clearState()
        val addr2 = 0x2000
        val tag2 = addr2 >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2)
        writeEntry(addr2, 1, tag2, valid = true, dirty = true, unique = true, sharer = 3)
        readEntry(addr2)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
        dut.io.resp.bits.hitIdx.expect(1.U)
        dut.io.resp.bits.unique.expect(true)
        dut.io.resp.bits.dirty.expect(true)
        dut.io.resp.bits.sharer.expect(0x3.U)

        // Scenario 3: detect a cache miss when the entry is invalid.
        clearState()
        readEntry(0x3000)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(false)

        // Scenario 4: handle masked writes to multiple ways.
        clearState()
        val addr4 = 0x4000
        val tag4 = addr4 >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2)
        writeEntry(addr4, 0, tag4, valid = true, sharer = 1)
        writeEntry(addr4, 1, tag4, valid = true, sharer = 1)
        writeEntry(addr4, 3, tag4, valid = true, sharer = 1)
        readEntry(addr4)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
        dut.io.resp.bits.hitIdx.expect(0.U)

        // Scenario 5: store and retrieve data with different tags.
        clearState()
        for (way <- 0 until 4) {
          val addr = 0x5000 + (way << 12)
          val tag = addr >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2)
          writeEntry(addr, way, tag, valid = true, dirty = (way % 2) == 1, unique = (way % 2) == 0, sharer = way)
        }

        for (way <- 0 until 4) {
          val addr = 0x5000 + (way << 12)
          val tag = (0x5000 + (way << 12)) >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2)
          readEntry(addr)
          dut.io.resp.valid.expect(true)
          dut.io.resp.bits.hit.expect(true)
          dut.io.resp.bits.hitIdx.expect(way.U)
          dut.io.resp.bits.rdata(way).tag.expect(tag.U)
          dut.io.resp.bits.rdata(way).valid.expect(true)
        }

        // Scenario 6: prioritize the lower way index when multiple hits are present.
        clearState()
        val addr6 = 0x6000
        val tag6 = addr6 >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2)
        writeEntry(addr6, 0, tag6, valid = true)
        writeEntry(addr6, 2, tag6, valid = true)
        readEntry(addr6)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
        dut.io.resp.bits.hitIdx.expect(0.U)

        // Scenario 7: invalidate entries on overwrite.
        clearState()
        val addr7 = 0x7000
        val tag7a = addr7 >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2)
        val tag7b = tag7a + 1
        writeEntry(addr7, 0, tag7a, valid = true)
        writeEntry(addr7, 0, tag7b, valid = false)
        readEntry(addr7)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(false)

        // Scenario 8: handle back-to-back requests.
        clearState()
        writeEntry(0x8000, 0, (0x8000 >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2)), valid = true)
        readEntry(0x9000)
        dut.io.resp.valid.expect(true)
        readEntry(0x8000)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)

        // Scenario 9: handle idle cycles with valid low.
        clearState()
        writeEntry(0xA000, 0, (0xA000 >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2)), valid = true)
        dut.io.req.valid.poke(false)
        for (_ <- 0 until 5) {
          dut.clock.step(1)
        }
        readEntry(0xA000)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
        dut.io.resp.bits.hitIdx.expect(0.U)
      }
    }

  }

}
