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

import scala.util.Random
import armleocpu._
import armleocpu.Consts._

class DataArraySVSimRandomSpec extends AnyFunSpec with ChiselSim {
  describe("DataArray randomized stress") {
    implicit val ccx: CCXParams = new CCXParams(
      l3 = new Params(
        cacheEntriesLog2 = 4,  // 16 entries
        cacheWaysLog2 = 2      // 4 ways
      )
    )
    implicit val bp: BusParams = new BusParams(addrWidth = 32, busBytes = cacheLineBytes)

    implicit val commonSettingsModifications: CommonSettingsModifications =
      (settings: CommonCompilationSettings) =>
        settings.copy(availableParallelism = AvailableParallelism.UpTo(4))

    implicit val backendSettingsModifications: BackendSettingsModifications = {
      case settings: svsim.verilator.Backend.CompilationSettings =>
        settings.withTraceStyle(Some(TraceStyle(kind = TraceKind.Fst())))
      case settings => settings
    }

    it("should pass a sequence of randomized writes and reads") {
      simulate(new DataArray) { dut =>
        enableWaves()

        val rng = new Random(12345)
        val ways = 1 << ccx.l3.cacheWaysLog2
        val entries = 1 << ccx.l3.cacheEntriesLog2
        val wayMaskWidth = (1 << ccx.l3.cacheWaysLog2).W

        case class ModelEntry(tag: Int, valid: Boolean, dirty: Boolean, unique: Boolean, sharer: Int)

        val model = Array.fill(entries, ways)(ModelEntry(0, valid = false, dirty = false, unique = false, sharer = 0))

        def writeEntry(addr: Int, way: Int, tag: Int, valid: Boolean = true, dirty: Boolean = false, unique: Boolean = false, sharer: Int = 0): Unit = {
          dut.io.req.valid.poke(true)
          dut.io.req.bits.addr.poke(addr.U(bp.addrWidth.W))
          dut.io.req.bits.write.poke(true)
          dut.io.req.bits.wayMask.poke((1 << way).U(wayMaskWidth))
          dut.io.req.bits.wdata.tag.poke(tag.U)
          dut.io.req.bits.wdata.valid.poke(valid.B)
          dut.io.req.bits.wdata.dirty.poke(dirty.B)
          dut.io.req.bits.wdata.unique.poke(unique.B)
          dut.io.req.bits.wdata.sharer.poke(sharer.U)
          dut.clock.step(1)
        }

        def readEntry(addr: Int): Unit = {
          dut.io.req.valid.poke(true)
          dut.io.req.bits.addr.poke(addr.U(bp.addrWidth.W))
          dut.io.req.bits.write.poke(false)
          dut.io.req.bits.wayMask.poke(0.U(wayMaskWidth))
          dut.clock.step(1)
        }

        // Randomized sequence
        val ops = 300
        for (_ <- 0 until ops) {
          val doWrite = rng.nextBoolean()
          if (doWrite) {
            val entry = rng.nextInt(entries)
            val way = rng.nextInt(ways)
            val tag = rng.nextInt(32)
            val valid = rng.nextBoolean()
            val dirty = rng.nextBoolean()
            val unique = rng.nextBoolean()
            val sharer = 0
            val addr = (tag << (ccx.l3.cacheEntriesLog2 + cacheLineLog2)) + (entry << cacheLineLog2)
            writeEntry(addr, way, tag, valid = valid, dirty = dirty, unique = unique, sharer = sharer)
            model(entry)(way) = ModelEntry(tag, valid, dirty, unique, sharer)
          } else {
            val entry = rng.nextInt(entries)
            // occasionally probe with a tag that exists, sometimes random
            val useExistingTag = rng.nextDouble() < 0.6
            val addr = if (useExistingTag) {
              // pick a way that is valid to read back, if any
              val validWays = (0 until ways).filter(w => model(entry)(w).valid)
              if (validWays.nonEmpty) {
                val w = validWays(rng.nextInt(validWays.length))
                val tag = model(entry)(w).tag
                (tag << (ccx.l3.cacheEntriesLog2 + cacheLineLog2)) + (entry << cacheLineLog2)
              } else {
                // no valid entries, use random tag
                val tag = rng.nextInt(32)
                (tag << (ccx.l3.cacheEntriesLog2 + cacheLineLog2)) + (entry << cacheLineLog2)
              }
            } else {
              val tag = rng.nextInt(32)
              (tag << (ccx.l3.cacheEntriesLog2 + cacheLineLog2)) + (entry << cacheLineLog2)
            }

            readEntry(addr)
            dut.io.resp.valid.expect(true)

            val expectedTag = (addr >> (ccx.l3.cacheEntriesLog2 + cacheLineLog2))
            val hitWayOpt = (0 until ways).find(w => model(entry)(w).valid && model(entry)(w).tag == expectedTag)
            if (hitWayOpt.isDefined) {
              val hitWay = hitWayOpt.get
              dut.io.resp.bits.hit.expect(true)
              dut.io.resp.bits.hitIdx.expect(hitWay.U)
              dut.io.resp.bits.rdata(hitWay).tag.expect(expectedTag.U)
              dut.io.resp.bits.rdata(hitWay).valid.expect(true)
            } else {
              dut.io.resp.bits.hit.expect(false)
            }
          }
        }
      }
    }

  }

}
