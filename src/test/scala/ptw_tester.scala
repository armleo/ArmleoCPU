package armleocpu

import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation


trait CatUtil {
    def Cat(l: Seq[Bits]): UInt = (l.tail foldLeft l.head.asUInt){(x, y) =>
        assert(x.isLit && y.isLit)
        (x.litValue << y.getWidth | y.litValue).U((x.getWidth + y.getWidth).W)
    }
    def Cat(x: Bits, l: Bits*): UInt = Cat(x :: l.toList)
}


class PtwSpec extends AnyFreeSpec with ChiselScalatestTester with CatUtil {

  "Basic PTW functionality test" in {
    test(new ptw(new coreParams(
      bus_data_bytes = 16,
    ))).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val RWXV = "h0F".U(10.W)
      val POINTER = "h01".U(10.W)
      val ppn = 4.U(22.W)
      val aligned_Megapage = Cat(5.U(12.W), 0.U(10.W))
      val Megapage_toleafpte = Cat(5.U(12.W), 4.U(10.W))
      val Megapage_toleafpte_addr = Cat(5.U(12.W), 4.U(10.W), 19.U(10.W), 0.U(2.W))
      // set to default
      dut.bus.ar.ready.poke   (false.B)
      dut.bus.r.valid.poke    (false.B)

      dut.bus.r.data.poke     (0.U)
      dut.bus.r.resp.poke     (bus_resp_t.DECERR)

      
      dut.bus.r.valid.poke    (false.B)
      dut.mem_priv.ppn.poke   (ppn)
      dut.mem_priv.mode.poke  (true.B)
      dut.resolve_req.poke    (false.B)

      // Test for megapage leaf
      // Test for page leaf
      //                      w/ rwx
      //                      w/ rw
      //                      w/ rx
      //                      w/ r
      //                      w/ x
      val access_bits_valid_combs = Seq(
        //  X         W         R         V
        Cat(1.U(1.W), 1.U(1.W), 1.U(1.W), 1.U(1.W)),
        Cat(0.U(1.W), 1.U(1.W), 1.U(1.W), 1.U(1.W)),
        Cat(1.U(1.W), 0.U(1.W), 1.U(1.W), 1.U(1.W)),
        Cat(0.U(1.W), 0.U(1.W), 1.U(1.W), 1.U(1.W)),
        Cat(1.U(1.W), 0.U(1.W), 0.U(1.W), 1.U(1.W))
      )
      for (comb <- access_bits_valid_combs) {
        println("Requesting megapage")
        val addr = requestMegapage(dut)
        bus_read_cplt(dut, addr, Cat(aligned_Megapage, Cat(0.U(6.W), comb)))
        expectSuccessfullResolve(dut, Cat(5.U(12.W), 0.U(10.W)), Cat(0.U(4.W), comb))
        expectIdle(dut)
        
        println("Requesting page")
        val addr1 = requestPage(dut)
        bus_read_cplt(dut, addr1, Cat(Megapage_toleafpte, POINTER))
        expectIdle(dut)
        
        println("Requesting page's direct PTE")
        bus_read_cplt(dut, Megapage_toleafpte_addr, Cat(800.U(22.W), 0.U(6.W), comb))
        expectSuccessfullResolve(dut, 800.U(22.W), Cat(0.U(4.W), comb))
        expectIdle(dut)
      }
      
      // Test for PMA error in megapage leaf
      println("Testing megapage PMA Error")
      val addr = requestMegapage(dut)
      bus_read_cplt_access_fault(dut, addr, Cat(aligned_Megapage, Cat(0.U(6.W), RWXV)))
      expectPMAError(dut)
      expectIdle(dut)


      // Test for PMA error in leaf
      println("Testing PMAError page (resolving with pointer)")
      val addr1 = requestPage(dut)
      bus_read_cplt(dut, addr1, Cat(Megapage_toleafpte, POINTER))
      expectIdle(dut)
      println("Testing PMA Error Page")
      bus_read_cplt_access_fault(dut, Megapage_toleafpte_addr, Cat(800.U(22.W), 0.U(6.W), RWXV))
      expectPMAError(dut)
      expectIdle(dut)

      // Test for missaligned megapage
      // Test for missaligned page (should always be false)

      // Test for invalid leaf
      //                        w/ i
      //                        w/ w
      //                        w/ xw
      
      // Test for invalid megapage leaf
      //                        w/ i
      //                        w/ w
      //                        w/ xw
      def expectIdle(dut: ptw) {
        dut.clock.step(1)
        dut.bus.r.valid.poke(false.B)
        dut.cplt.expect(false.B)
        dut.page_fault.expect(false.B)
        dut.access_fault.expect(false.B)
      }
      def expectSuccessfullResolve(dut: ptw, physical_address_top: UInt, access_bits: UInt) = {
        dut.physical_address_top.expect(physical_address_top)
        dut.meta.expect(access_bits.asTypeOf(new tlbmeta_t))
        dut.cplt.expect(true.B)
        dut.page_fault.expect(false.B)
        dut.access_fault.expect(false.B)
      }

      def expectPMAError(dut: ptw) {
        dut.cplt.expect(true.B)
        dut.page_fault.expect(false.B)
        dut.access_fault.expect(true.B)
      }

      def request_resolve(dut: ptw, vaddr: UInt) = {
        dut.resolve_req.poke(true.B)
        dut.vaddr.poke(vaddr)
        dut.cplt.expect(false.B)
        dut.page_fault.expect(false.B)
        dut.access_fault.expect(false.B)
        dut.clock.step(1)
        dut.resolve_req.poke(false.B)
        dut.cplt.expect(false.B)
        dut.page_fault.expect(false.B)
        dut.access_fault.expect(false.B)
      }

      def bus_read_cplt(dut: ptw, expectedAddress: UInt, readdata: UInt) = {
        dut.bus.ar.valid.expect(true.B)
        dut.bus.ar.addr.expect(expectedAddress.litValue)
        dut.bus.r.valid.poke(true.B)
        dut.bus.r.ready.poke(false.B)
        dut.bus.r.resp.poke(bus_resp_t.OKAY)
        dut.bus.r.data.poke(readdata)
        //step(1)
        //poke(dut.bus.r.datavalid, false.B)
      }
      def bus_read_cplt_access_fault(dut: ptw, expectedAddress:UInt, readdata: UInt) {
        bus_read_cplt(dut, expectedAddress, readdata)
        dut.bus.r.resp.poke(bus_resp_t.DECERR)
      }

      def requestMegapage(dut: ptw): UInt = { // always request second (index = 1) pte from table
        request_resolve(dut, Cat(1.U(10.W), 0.U(10.W), 0.U(12.W)))
        return Cat(ppn, 1.U(10.W), 0.U(2.W))
      }
      def requestPage(dut: ptw): UInt = {
        request_resolve(dut, Cat(16.U(10.W), 19.U(10.W), 0.U(12.W)))
        return Cat(ppn, 16.U(10.W), 0.U(2.W))
      }
      
    }
  }
}
