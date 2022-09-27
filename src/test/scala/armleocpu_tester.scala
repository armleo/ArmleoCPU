
package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


class ArmleoCPUSpec extends AnyFreeSpec with ChiselScalatestTester {

  "ArmleoCPU should fetch instructions" in {
    test(new ArmleoCPU).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.fetch_interface.initSink()
        dut.fetch_interface.setSinkClock(dut.clock)
        dut.response_interface.initSource()
        dut.response_interface.setSourceClock(dut.clock)
        
        
        fork {
            dut.fetch_interface.expectDequeue(0.U)
            dut.clock.step(1)
            dut.fetch_interface.expectDequeue(0.U)
        }.fork {
            dut.clock.step(5)
        }.join()
    }
  }
}