package armleocpu

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import chisel3.simulator.stimulus.{RunUntilFinished, RunUntilSuccess}
import chisel3.util._
import armleocpu.memory.l3cache._
import org.scalatest.funspec.AnyFunSpec
import svsim.{BackendSettingsModifications, CommonCompilationSettings, CommonSettingsModifications}
import svsim.CommonCompilationSettings.AvailableParallelism
import svsim.verilator.Backend.CompilationSettings.{TraceKind, TraceStyle}

class DataArraySVSimSpec extends AnyFunSpec with ChiselSim {

  describe("DataArray") {
    implicit val ccx: CCXParams = new CCXParams(
      l3 = new Params(
        cacheEntriesLog2 = 4,  // 16 entries
        cacheWaysLog2 = 2      // 4 ways
      )
    )
    implicit val cbp: CoherentBusParams = new CoherentBusParams(addrWidth = 32)

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

    it("should write and read back a cache entry") {
      simulate(new DataArray) { dut =>
        enableWaves()
        // Test 1: Write to way 0
        val addr = 0x1000
        val tag = addr >> ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2

        // Issue write request
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(true)
        dut.io.req.bits.wayMask.poke(0x1)  // Write to way 0 only
        // tag must match the address-derived cache tag (addr >> (entriesLog2 + lineLog2))
        dut.io.req.bits.wdata.tag.poke(tag.U)
        dut.io.req.bits.wdata.valid.poke(true)
        dut.io.req.bits.wdata.dirty.poke(false)
        dut.io.req.bits.wdata.unique.poke(false)
        dut.io.req.bits.wdata.sharer.poke(0x1.U)

        dut.clock.step(1)

        // Now issue a read to the same address
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(false)
        dut.io.req.bits.wayMask.poke(0)

        dut.clock.step(1)

        // Check response (delayed by 1 cycle due to pipeline)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
        dut.io.resp.bits.hitIdx.expect(0)
        dut.io.resp.bits.rdata(0).tag.expect(tag.U)
        dut.io.resp.bits.rdata(0).valid.expect(true)
      }
    }

    it("should detect cache hit when entry is valid") {
      simulate(new DataArray) { dut =>
        enableWaves()
        val addr = 0x2000.U
        val tag = (0x2000 >> (ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)).U

        // Write entry to way 1
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(true)
        dut.io.req.bits.wayMask.poke(0x2)  // Write to way 1
        dut.io.req.bits.wdata.tag.poke(tag)
        dut.io.req.bits.wdata.valid.poke(true)
        dut.io.req.bits.wdata.dirty.poke(true)
        dut.io.req.bits.wdata.unique.poke(true)
        dut.io.req.bits.wdata.sharer.poke(0x3)

        dut.clock.step(1)

        // Read from same address
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(false)
        dut.clock.step(1)

        // Verify hit on way 1
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
        dut.io.resp.bits.hitIdx.expect(1)
        dut.io.resp.bits.unique.expect(true)
        dut.io.resp.bits.dirty.expect(true)
        dut.io.resp.bits.sharer.expect(0x3.U)
      }
    }

    it("should detect cache miss when entry is invalid") {
      simulate(new DataArray) { dut =>
        enableWaves()
        // Read from an address without writing first
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(0x3000.U)
        dut.io.req.bits.write.poke(false)

        dut.clock.step(1)

        // Should be a miss (invalid entries)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(false)
      }
    }

    it("should handle masked writes to multiple ways") {
      simulate(new DataArray) { dut =>
        enableWaves()
        val addr = 0x4000

        // Write to ways 0, 1, and 3 (mask = 0b1011 = 11)
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(true)
        dut.io.req.bits.wayMask.poke(0xB)  // Write to ways 0, 1, 3
        dut.io.req.bits.wdata.tag.poke((0x4000 >> (ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)).U)
        dut.io.req.bits.wdata.valid.poke(true)
        dut.io.req.bits.wdata.dirty.poke(false)
        dut.io.req.bits.wdata.unique.poke(false)
        dut.io.req.bits.wdata.sharer.poke(0x1)

        dut.clock.step(1)

        // Read back
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(false)

        dut.clock.step(1)

        // Verify hit (should find entry in way 0)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
        dut.io.resp.bits.hitIdx.expect(0)
      }
    }

    it("should store and retrieve data with different tags") {
      simulate(new DataArray) { dut =>
        enableWaves()
        // Write multiple entries with different tags to different ways
        for (way <- 0 until 4) {
          val addr = (0x5000 + (way << 12)).U
          val tag = ((0x5000 + (way << 12)) >> (ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)).U

          dut.io.req.valid.poke(true)
          dut.io.req.bits.addr.poke(addr)
          dut.io.req.bits.write.poke(true)
          dut.io.req.bits.wayMask.poke(1 << way)
          dut.io.req.bits.wdata.tag.poke(tag)
          dut.io.req.bits.wdata.valid.poke(true)
          dut.io.req.bits.wdata.dirty.poke((way % 2) == 1)
          dut.io.req.bits.wdata.unique.poke((way % 2) == 0)
          dut.io.req.bits.wdata.sharer.poke(way.U(ccx.coreCount.W))

          dut.clock.step(1)
        }

        // Now read back the entries
        for (way <- 0 until 4) {
          val addr = (0x5000 + (way << 8)).U

          dut.io.req.valid.poke(true)
          dut.io.req.bits.addr.poke(addr)
          dut.io.req.bits.write.poke(false)

          dut.clock.step(1)

          dut.io.resp.valid.expect(true)
          dut.io.resp.bits.hit.expect(true)
          dut.io.resp.bits.hitIdx.expect(way)
          dut.io.resp.bits.rdata(way).tag.expect(((0x5000 + (way << 12)) >> (ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)).U)
          dut.io.resp.bits.rdata(way).valid.expect(true)
        }
      }
    }

    it("should prioritize lower way index on multiple hits") {
      simulate(new DataArray) { dut =>
        enableWaves()
        val addr = 0x6000.U
        val tag = (0x6000 >> (ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)).U

        // Write same tag to ways 0 and 2
        for (way <- Seq(0, 2)) {
          dut.io.req.valid.poke(true)
          dut.io.req.bits.addr.poke(addr)
          dut.io.req.bits.write.poke(true)
          dut.io.req.bits.wayMask.poke(1 << way)
          dut.io.req.bits.wdata.tag.poke(tag)
          dut.io.req.bits.wdata.valid.poke(true)
          dut.io.req.bits.wdata.dirty.poke(false)
          dut.io.req.bits.wdata.unique.poke(false)
          dut.io.req.bits.wdata.sharer.poke(0)

          dut.clock.step(1)
        }

        // Read back
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(false)

        dut.clock.step(1)

        // Should hit on way 0 (priority encoder selects lowest)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
        dut.io.resp.bits.hitIdx.expect(0)
      }
    }

    it("should invalidate entries on overwrite") {
      simulate(new DataArray) { dut =>
        enableWaves()
        val addr = 0x7000.U
        val tag1 = 0x7000 >> (ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)
        val tag2 = tag1 + 1

        // Write tag1 to way 0
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(true)
        dut.io.req.bits.wayMask.poke(0x1)
        dut.io.req.bits.wdata.tag.poke(tag1.U)
        dut.io.req.bits.wdata.valid.poke(true)
        dut.io.req.bits.wdata.dirty.poke(false)
        dut.io.req.bits.wdata.unique.poke(false)
        dut.io.req.bits.wdata.sharer.poke(0)

        dut.clock.step(1)

        // Overwrite with tag2 (still valid)
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(true)
        dut.io.req.bits.wayMask.poke(0x1)
        dut.io.req.bits.wdata.tag.poke(tag2.U)
        dut.io.req.bits.wdata.valid.poke(false)  // Mark as invalid
        dut.io.req.bits.wdata.dirty.poke(false)
        dut.io.req.bits.wdata.unique.poke(false)
        dut.io.req.bits.wdata.sharer.poke(0)

        dut.clock.step(1)

        // Read back
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(addr)
        dut.io.req.bits.write.poke(false)

        dut.clock.step(1)

        // Should be a miss (invalid)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(false)
      }
    }

    it("should handle back-to-back requests") {
      simulate(new DataArray) { dut =>
        enableWaves()
        // Write to address 1
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(0x8000.U)
        dut.io.req.bits.write.poke(true)
        dut.io.req.bits.wayMask.poke(0x1)
        dut.io.req.bits.wdata.tag.poke((0x8000 >> (ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)).U)
        dut.io.req.bits.wdata.valid.poke(true)
        dut.io.req.bits.wdata.dirty.poke(false)
        dut.io.req.bits.wdata.unique.poke(false)
        dut.io.req.bits.wdata.sharer.poke(0)

        dut.clock.step(1)

        // Back-to-back read different address while previous latches
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(0x9000.U)
        dut.io.req.bits.write.poke(false)

        dut.clock.step(1)
        dut.io.resp.valid.expect(true)

        // Another read immediately after
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(0x8000.U)
        dut.io.req.bits.write.poke(false)

        dut.clock.step(1)
        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
      }
    }

    it("should handle idle cycles (valid=0)") {
      simulate(new DataArray) { dut =>
        enableWaves()
        // Write an entry
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(0xA000.U)
        dut.io.req.bits.write.poke(true)
        dut.io.req.bits.wayMask.poke(0x1)
        dut.io.req.bits.wdata.tag.poke((0xA000 >> (ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)).U)
        dut.io.req.bits.wdata.valid.poke(true)
        dut.io.req.bits.wdata.dirty.poke(false)
        dut.io.req.bits.wdata.unique.poke(false)
        dut.io.req.bits.wdata.sharer.poke(0)

        dut.clock.step(1)

        // Idle for 5 cycles
        dut.io.req.valid.poke(false)
        for (_ <- 0 until 5) {
          dut.clock.step(1)
        }

        // Read back after idle
        dut.io.req.valid.poke(true)
        dut.io.req.bits.addr.poke(0xA000.U)
        dut.io.req.bits.write.poke(false)

        dut.clock.step(1)

        dut.io.resp.valid.expect(true)
        dut.io.resp.bits.hit.expect(true)
        dut.io.resp.bits.hitIdx.expect(0)
      }
    }

  }

}
