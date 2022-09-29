
package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


class ArmleoCPUSpec extends AnyFreeSpec with ChiselScalatestTester {

  "ArmleoCPU should fetch instructions" in {
    test(new ArmleoCPU).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.request_interface.initSink()
        dut.request_interface.setSinkClock(dut.clock)
        dut.response_interface.initSource()
        dut.response_interface.setSourceClock(dut.clock)
        dut.kill.poke(0)
        
        fork {
            dut.clock.step(1)
            dut.request_interface.expectPeek(0.U)
            dut.clock.step(1)
            dut.request_interface.expectPeek(0.U)
            dut.clock.step(1)
            dut.request_interface.expectDequeue(0.U)
            dut.clock.step(1)
            dut.response_interface.enqueueNow(BigInt("FF00FF00", 16).U)
            dut.request_interface.expectDequeue(4.U)
            dut.clock.step(1)

        }.join()
    }
  }
}