package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._

import armleocpu.bus_resp_t._


import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec



class BusMuxTesterModule(val baseAddr:UInt = "h40000000".asUInt, val bramWords: Int = 2048, val numRepeats: Int = 2000, val n: Int = 4) extends Module {
  val io = IO(new BRAMExerciserIO)

  val c = new CoreParams(busBytes = 8)
  val bram = Module(new BRAM(c, bramWords, baseAddr, instName = "bram0", verbose = true))
  val busmux = Module(new dbus_mux(bram.io, n = n, noise = true))

  
  
  val exercisers = Seq.tabulate(n) {num: Int => 
    val ex = Module(new BRAMExerciser(
      seed = (100 + n * 20),
      baseAddr = (baseAddr.litValue + (bramWords / n * c.busBytes * num)).U,
      bramWords = (bramWords / n),
      maxLen = 4,
      allowedBramWords = (bramWords / n) - 4, // We reduce it by 4 to make sure that BRAM Exerciser does not try to go out of bounds from one section of memory into another
      numRepeats = numRepeats,
      dut = bram, c = c))
    ex.dbus <> busmux.io.upstream(num)
    ex
  }

  
  bram.io <> busmux.io.downstream

  io.success := exercisers.map(_.io.success).reduce(_ && _)
  io.done := exercisers.map(_.io.done).reduce(_ && _)
  io.coverage := exercisers.map(_.io.coverage).reduce(_ + _)
}


class BusMuxTest extends AnyFlatSpec {
  it should "BusMux Stress test" in {
    simulate("StressBusMux", new BusMuxTesterModule()) { harness =>
      for (i <- 0 to 200 * 5) {
        harness.clock.step(100)
        if (harness.io.done.peek().litValue == 1) {
          harness.io.success.expect(true.B)
        }
        println("100 cycles done")
      }
      
      harness.io.done.expect(true.B)
      harness.io.success.expect(true.B)
      

    }
  }
}
