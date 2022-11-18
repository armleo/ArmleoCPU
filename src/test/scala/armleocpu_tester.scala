
package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


class ArmleoCPUSpec extends AnyFreeSpec with ChiselScalatestTester {

  val c = new coreParams(itlb_entries = 4, itlb_ways = 2, bus_data_bytes = 16)

  "ArmleoCPU should fetch instructions" in {
    test(new ArmleoCPU(c)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(Math.max(c.icache_entries, c.itlb_entries)) // Flush
        dut.clock.step(2) // goes to cache refill
        dut.ibus.ar.valid.expect(true)
        dut.ibus.ar.addr.expect(c.reset_vector)
        dut.clock.step(1)
        dut.ibus.ar.valid.expect(true)
        dut.ibus.ar.addr.expect(c.reset_vector)
        dut.ibus.ar.ready.poke(true)
        dut.clock.step(1)

        for(i <- 0 until 4) {
          dut.ibus.ar.ready.poke(false)
          dut.ibus.ar.valid.expect(false)
          dut.ibus.r.valid.poke(true)
          dut.ibus.r.data.poke(BigInt(f"F${i}E${i}D${i}C${i}B${i}A${i}9${i}8${i}7${i}6${i}5${i}4${i}3${i}2${i}1${i}0${i}", 16))
          if(i == 3)
            dut.ibus.r.last.poke(true)
          dut.clock.step(1)
        }
        dut.ibus.r.valid.poke(false)
        dut.ibus.r.last.poke(false)
        dut.ibus.r.data.poke(BigInt("DEADBEEFDEADBEEFDEADBEEFDEADBEEF", 16))
        dut.clock.step(1)

        dut.clock.step(1)
        
        dut.clock.step(1)

        dut.clock.step(10)
    }
  }
}

