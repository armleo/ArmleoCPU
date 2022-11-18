
package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


class ArmleoCPUSpec extends AnyFreeSpec with ChiselScalatestTester {

  "ArmleoCPU should fetch instructions" in {
    test(new ArmleoCPU).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(10)
          
    }
  }
}

