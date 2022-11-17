package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


class FetchSpec extends AnyFreeSpec with ChiselScalatestTester {

  "Basic Fetch functionality test" in {
    test(new Fetch(new coreParams(itlb_entries = 4, itlb_ways = 2, bus_data_bytes = 16))).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.clock.step(10)
    }
  }
}