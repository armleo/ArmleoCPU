
package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation

/*
class ArmleoCPUSpec extends AnyFreeSpec with ChiselScalatestTester {

  "ArmleoCPU should fetch instructions" in {
    test(new ArmleoCPU).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.ireq_ready.poke(0)
        
        fork {
          dut.clock.step(1)
          dut.ireq_ready.poke(1)
          dut.ireq_data.poke(BigInt("10100000111100001111" + /*rd=*/"00001" + /*opcode*/"0110111", 2))

          dut.clock.step(1)
          dut.ireq_ready.poke(0)
          dut.clock.step(4)
          
          // TODO: Test JALR
          dut.ireq_data.poke(BigInt("010101101110" + "00001" + "000" + /*rd=*/"00010" + /*opcode*/"1100111", 2))
          dut.ireq_ready.poke(1)

          dut.clock.step(1)
          dut.ireq_ready.poke(0)
          dut.clock.step(3)
          // TODO: Test JAL
          
            
        }.join()
    }
  }
}
*/
