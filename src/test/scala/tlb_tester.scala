package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


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
      dut.s1.read_data.ptag.expect(100)
      // TODO: Check the rest values

      dut.s0.cmd.poke(tlb_cmd.resolve)
      dut.s0.virt_address.poke(BigInt("0001"+ "00", 2))
      dut.clock.step(1)
      dut.s1.miss.expect(false)
      dut.s1.read_data.ptag.expect(104)
      // TODO: Check the rest values

      /**************************************************************************/
      /* Test overwriting                                                       */
      /**************************************************************************/
      dut.s0.cmd.poke(tlb_cmd.write)
      dut.s0.virt_address.poke(BigInt("0011"+ "00", 2))
      dut.s0.write_data.meta.perm.dirty.poke(true)
      dut.s0.write_data.meta.perm.access.poke(false)
      dut.s0.write_data.meta.perm.global.poke(false)
      dut.s0.write_data.meta.perm.user.poke(false)
      dut.s0.write_data.meta.perm.execute.poke(false)
      dut.s0.write_data.meta.perm.write.poke(false)
      dut.s0.write_data.meta.perm.read.poke(true)
      dut.s0.write_data.meta.valid.poke(true)
      dut.s0.write_data.ptag.poke(108)
      dut.clock.step(1)

      dut.s0.cmd.poke(tlb_cmd.resolve)
      dut.s0.virt_address.poke(BigInt("0000"+ "00", 2))
      dut.clock.step(1)
      dut.s1.miss.expect(true)
      //dut.s1.read_data.ptag.expect(108)
      // TODO: Check the rest values

      // TODO: Test many writes and reads
      // TODO: Like a lot
    }
  }
}
