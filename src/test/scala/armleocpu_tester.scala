package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation
import java.io._
import java.nio.ByteBuffer


class ArmleoCPUSpec extends AnyFreeSpec with ChiselScalatestTester {

  val c = new coreParams(itlb_entries = 4, itlb_ways = 2, bus_data_bytes = 4, reset_vector = 0)
  "ArmleoCPU should run example programs" in {
    test(new ArmleoCPU(c)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val bis = new BufferedInputStream(new FileInputStream("tests/verif_tests/verif_isa_tests/output/add.bin"))
        val bArray = LazyList.continually(bis.read).takeWhile(i => -1 != i).map(_.toByte).toArray

        
        
        dut.clock.step(Math.max(c.icache_entries, c.itlb_entries)) // Flush
        dut.clock.step(2) // goes to cache refill
        for(i <- 0 until 100) {
            if(dut.ibus.ar.valid.peek().litValue != 0) {
                dut.ibus.ar.valid.expect(true)
                dut.clock.step(1)
                dut.ibus.ar.valid.expect(true)
                dut.ibus.ar.ready.poke(true)
                var addr = dut.ibus.ar.addr.peek().litValue
                val len = dut.ibus.ar.len.peek().litValue +  1
                dut.clock.step(1)

                for(i <- 0 until len.toInt) {
                    dut.ibus.ar.ready.poke(false)
                    dut.ibus.ar.valid.expect(false)
                    dut.ibus.r.ready.expect(true)
                    dut.ibus.r.valid.poke(true)
                    dut.ibus.r.data.poke(ByteBuffer.wrap(bArray.slice(addr.toInt, addr.toInt + 4).toSeq.reverse.toArray).getInt())
                    addr = addr + 4
                    if(i == len.toInt - 1)
                        dut.ibus.r.last.poke(true)
                    dut.clock.step(1)
                }

                dut.ibus.r.valid.poke(false)
            } else {
                dut.clock.step(1)
            }
        }
    }
  }
}
