
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

class TlbSpec extends AnyFreeSpec with ChiselScalatestTester {

  "Basic TLB functionality test" in {
    test(new TLB(true, new coreParams(itlb_entries = 4, itlb_ways = 2))).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      /**************************************************************************/
      /* Invalidate all                                                         */
      /**************************************************************************/
      
      dut.s0.cmd.poke(tlb_cmd.invalidate_all)
      dut.clock.step(1)
      
      /**************************************************************************/
      /* Test resolution after full reset                                       */
      /**************************************************************************/
      for(i <- 0 until 4) {
        dut.s0.cmd.poke(tlb_cmd.resolve)
        dut.s0.virt_address.poke(i)
        dut.clock.step(1)
        dut.s1.miss.expect(true)
      }

      /**************************************************************************/
      /* Test write                                                             */
      /**************************************************************************/
      dut.s0.cmd.poke(tlb_cmd.write)
      dut.s0.virt_address.poke(BigInt("0000"+ "00", 2))
      dut.s0.write_data.meta.perm.dirty.poke(false)
      dut.s0.write_data.meta.perm.access.poke(false)
      dut.s0.write_data.meta.perm.global.poke(false)
      dut.s0.write_data.meta.perm.user.poke(false)
      dut.s0.write_data.meta.perm.execute.poke(false)
      dut.s0.write_data.meta.perm.write.poke(false)
      dut.s0.write_data.meta.perm.read.poke(true)
      dut.s0.write_data.meta.valid.poke(true)
      dut.s0.write_data.ptag.poke(100)
      dut.clock.step(1)

      /**************************************************************************/
      /* Test write to the different way                                        */
      /**************************************************************************/
      dut.s0.cmd.poke(tlb_cmd.write)
      dut.s0.virt_address.poke(BigInt("0001"+ "00", 2))
      dut.s0.write_data.meta.perm.dirty.poke(true)
      dut.s0.write_data.meta.perm.access.poke(false)
      dut.s0.write_data.meta.perm.global.poke(false)
      dut.s0.write_data.meta.perm.user.poke(false)
      dut.s0.write_data.meta.perm.execute.poke(false)
      dut.s0.write_data.meta.perm.write.poke(false)
      dut.s0.write_data.meta.perm.read.poke(true)
      dut.s0.write_data.meta.valid.poke(true)
      dut.s0.write_data.ptag.poke(104)
      dut.clock.step(1)

      dut.s0.cmd.poke(tlb_cmd.resolve)
      dut.s0.virt_address.poke(BigInt("0000"+ "00", 2))
      dut.clock.step(1)
      dut.s1.miss.expect(false)
      // TODO: Check the rest values
    }
  }
}