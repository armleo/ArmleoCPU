package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


class FetchSpec extends AnyFreeSpec with ChiselScalatestTester {

  "Basic Fetch functionality test" in {
    val c = new CoreParams(itlb = new TlbParams(entries = 4), bp = new BusParams(data_bytes = 16))

    test(new Fetch(c)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.clock.step(Math.max(c.icache.entries * c.icache.entry_bytes / c.bp.data_bytes, c.itlb.entries)) // Flush
      dut.clock.step(2) // goes to cache refill
      dut.ibus.ar.valid.expect(true)
      dut.ibus.ar.addr.expect(c.reset_vector)
      dut.uop_valid.expect(false)
      dut.clock.step(1)
      dut.ibus.ar.valid.expect(true)
      dut.ibus.ar.addr.expect(c.reset_vector)
      dut.ibus.ar.ready.poke(true)
      dut.uop_valid.expect(false)
      dut.clock.step(1)

      for(i <- 0 until 4) {
        dut.ibus.ar.ready.poke(false)
        dut.uop_valid.expect(false)
        dut.ibus.ar.valid.expect(false)
        dut.ibus.r.valid.poke(true)
        dut.ibus.r.data.poke(BigInt(f"F${i}E${i}D${i}C${i}B${i}A${i}9${i}8${i}7${i}6${i}5${i}4${i}3${i}2${i}1${i}0${i}", 16))
        if(i == 3)
          dut.ibus.r.last.poke(true)
        dut.clock.step(1)
      }
      dut.uop_valid.expect(false)
      dut.ibus.r.valid.poke(false)
      dut.ibus.r.last.poke(false)
      dut.ibus.r.data.poke(BigInt("DEADBEEFDEADBEEFDEADBEEFDEADBEEF", 16))
      dut.clock.step(1)

      dut.uop_valid.expect(true)
      dut.uop.pc.expect(c.reset_vector)
      dut.uop.instr.expect(BigInt("030201000", 16))
      dut.uop_accept.poke(true)
      dut.clock.step(1)

      dut.uop_valid.expect(true)
      dut.uop.pc.expect(c.reset_vector + 4)
      dut.uop.instr.expect(BigInt("070605040", 16))
      dut.uop_accept.poke(true)
      dut.clock.step(1)


      dut.uop_accept.poke(false)

      dut.clock.step(10)
    }
  }
}