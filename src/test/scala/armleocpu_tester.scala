
package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


class ArmleoCPUSpec extends AnyFreeSpec with ChiselScalatestTester {

  "ArmleoCPU should fetch instructions" in {
    test(new ArmleoCPU).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.ireq_ready.poke(0)
        
        fork {
          dut.clock.step(1)
          dut.ireq_ready.poke(1)
          dut.ireq_data.poke(BigInt("10110111", 2))

          dut.clock.step(1)
          dut.ireq_ready.poke(0)
          dut.clock.step(10)
            
        }.join()
    }
  }
}