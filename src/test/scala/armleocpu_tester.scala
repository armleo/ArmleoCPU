package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation
import java.io._
import java.nio.ByteBuffer


class ArmleoCPUSpec extends AnyFreeSpec with ChiselScalatestTester {

  val c = new CoreParams(
    itlb = new TlbParams(entries = 4, ways = 2),
    icache = new CacheParams(entries = 8, entry_bytes = 32),
    bp = new BusParams(data_bytes = 16),
    reset_vector = 0
  )
  "ArmleoCPU should run example programs" in {
    test(new ArmleoCPU(c)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val bis = new BufferedInputStream(new FileInputStream("tests/verif_tests/verif_isa_tests/output/addi.bin"))
      val bArray = LazyList.continually(bis.read).takeWhile(i => -1 != i).map(_.toByte).toArray

      for(i <- 0 until 600) {
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
            val arr = Array.concat(bArray.slice(addr.toInt, addr.toInt + c.bp.data_bytes), new Array[Byte](1))
            
            dut.ibus.r.data.poke(BigInt(arr.toSeq.reverse.toArray))
            addr = addr + c.bp.data_bytes
            if(i == len.toInt - 1)
              dut.ibus.r.last.poke(true)
            dut.clock.step(1)
          }

          dut.ibus.r.valid.poke(false)
          dut.ibus.r.last.poke(false)
        } else {
          dut.clock.step(1)
        }
        // FIXME: Take advantage of state machines instead so both the dbus and ibus can be processed simultanously
        
      }
      // FIXME: Add the dbus interface
      // FIXME: Add the check at the end for the fail/pass value in memory
      // FIXME: Load memory once from binary file into memory
    }
  }
}
