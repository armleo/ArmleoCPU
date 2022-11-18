package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


class CacheSpec extends AnyFreeSpec with ChiselScalatestTester {
  val c = new coreParams(
      bus_data_bytes = 4,
      icache_ways = 2,
      icache_entries = 16,
      icache_entry_bytes = 8
    )
  "Basic Cache functionality test" in {
    test(new Cache(true, c)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      /**************************************************************************/
      /* Invalidate all                                                         */
      /**************************************************************************/
      for (i <- 0 until c.icache_entries) {
        dut.s0.cmd.poke(cache_cmd.invalidate)
        dut.s0.vaddr.poke(i << chisel3.util.log2Ceil(c.icache_entry_bytes))
        dut.clock.step(1)
      }
      
      
      /**************************************************************************/
      /* Test resolution after full reset                                       */
      /**************************************************************************/
      for(i <- 0 until 32) {
        dut.s0.cmd.poke(cache_cmd.request)
        dut.s0.vaddr.poke(i << 2)
        dut.clock.step(1)
        dut.s1.paddr.poke(i << 2)
        dut.s1.response.miss.expect(true)
      }
      
      /**************************************************************************/
      /* Test write                                                             */
      /**************************************************************************/
      dut.s0.cmd.poke(cache_cmd.write)
      dut.s0.vaddr.poke(BigInt(/*cptag*/"0001" + /*entry num*/"0000" + /*bus_num*/"0" + "00", 2))
      dut.s0.write_paddr.poke(BigInt(/*cptag*/"0001" + /*entry num*/"0000" + /*bus_num*/"0" + "00", 2))
      dut.s0.write_way_idx_in.poke(0)
      dut.s0.write_bus_aligned_data(0).poke(BigInt("8F", 16))
      dut.s0.write_bus_mask(0).poke(false)
      dut.s0.write_bus_aligned_data(1).poke(BigInt("CF", 16))
      dut.s0.write_bus_mask(1).poke(true)
      dut.s0.write_valid.poke(true)
      dut.clock.step(1)


      /**************************************************************************/
      /* Test Read                                                              */
      /**************************************************************************/
      dut.s0.cmd.poke(cache_cmd.request)
      dut.s0.vaddr.poke(BigInt(/*cptag*/"0001" + /*entry num*/"0000" + /*bus_num*/"0" + "00", 2))
      dut.clock.step(1)
      dut.s0.cmd.poke(cache_cmd.none)
      dut.s1.paddr.poke(BigInt(/*cptag*/"0001" + /*entry num*/"0000" + /*bus_num*/"0" + "00", 2))
      dut.clock.step(0)
      dut.s1.response.miss.expect(false)
      // TODO: Check the outputs

      

      /*
      dut.s0.virt_address.poke(BigInt("0000"+ "00", 2))
      dut.s0.write_data.meta.perm.dirty.poke(false)
      dut.s0.write_data.meta.perm.access.poke(false)
      dut.s0.write_data.meta.perm.global.poke(false)
      dut.s0.write_data.meta.perm.user.poke(false)
      dut.s0.write_data.meta.perm.execute.poke(false)
      dut.s0.write_data.meta.perm.write.poke(false)
      dut.s0.write_data.meta.perm.read.poke(true)
      dut.s0.write_data.meta.valid.poke(true)
      dut.s0.write_data.ptag.poke(100)*/
      //dut.clock.step(1)
      /*
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
      */
    }
  }
}
