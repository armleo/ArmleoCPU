package armleocpu.l3cache

import armleocpu._
import armleocpu.Consts._
import armleocpu.memory.l3cache.Params
import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.funspec.AnyFunSpec
import svsim.{BackendSettingsModifications, CommonCompilationSettings, CommonSettingsModifications}
import svsim.CommonCompilationSettings.AvailableParallelism
import svsim.verilator.Backend.CompilationSettings.{TraceKind, TraceStyle}

class DataArraySVSimRandomSpec extends AnyFunSpec with ChiselSim {
  describe("DataArray randomized synthesizable stress") {
    implicit val ccx: CCXParams = new CCXParams(
      l3 = new Params(
        cacheEntriesLog2 = 2, // 4 entries
        cacheWaysLog2 = 2     // 4 ways
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

    it("should pass randomized writes and reads") {
      val operationCount = 5000
      simulate(new DataArraySynthRandom(operationCount = operationCount)) { harness =>
        enableWaves()
        harness.io.start.poke(true.B)
        harness.clock.step()
        harness.io.start.poke(false.B)

        var cycles = 1
        while (!harness.io.done.peek().litToBoolean && cycles < operationCount + 100) {
          harness.clock.step()
          cycles += 1
        }

        harness.io.done.expect(true.B)
        harness.io.error.expect(false.B)
      }
    }
  }
}
