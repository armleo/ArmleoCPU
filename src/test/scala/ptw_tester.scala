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


class PTWSpec extends AnyFreeSpec with ChiselScalatestTester with CatUtil {

  "Basic PTW functionality test" in {
    test(new ptw(new coreParams(
      bus_data_bytes = 16,
    ))).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

    
    val RWXV = "h0F".U(10.W)
    val POINTER = "h01".U(10.W)
    val matp_ppn = 4.U(22.W)
    val aligned_Megapage = Cat(5.U(12.W), 0.U(10.W))
    val Megapage_toleafpte = Cat(5.U(12.W), 4.U(10.W))
    val Megapage_toleafpte_addr = Cat(5.U(12.W), 4.U(10.W), 19.U(10.W), 0.U(2.W))
    // set to default
    dut.bus.ar.ready.poke(false.B)
    dut.bus.r.valid.poke(false.B)

    poke(c.io.memory.readdata, 0.U)
    poke(c.io.memory.response, MemHostIfResponse.DECODEERROR)
    poke(c.io.memory.readdatavalid, false.B)
    poke(c.io.matp_ppn, matp_ppn)
    poke(c.io.matp_mode, true.B)
    poke(c.io.resolve_req, false.B)

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
      val addr = requestMegapage(c)
      bus_read_done(c, addr, Cat(aligned_Megapage, Cat(0.U(6.W), comb)))
      expectSuccessfullResolve(c, Cat(5.U(12.W), 0.U(10.W)), Cat(0.U(4.W), comb))
      expectIdle(c)
      
      println("Requesting page")
      val addr1 = requestPage(c)
      bus_read_done(c, addr1, Cat(Megapage_toleafpte, POINTER))
      expectIdle(c)
      
      println("Requesting page's direct PTE")
      bus_read_done(c, Megapage_toleafpte_addr, Cat(800.U(22.W), 0.U(6.W), comb))
      expectSuccessfullResolve(c, 800.U(22.W), Cat(0.U(4.W), comb))
      expectIdle(c)
    }
    
    // Test for PMA error in megapage leaf
    println("Testing megapage PMA Error")
    val addr = requestMegapage(c)
    bus_read_done_access_fault(c, addr, Cat(aligned_Megapage, Cat(0.U(6.W), RWXV)))
    expectPMAError(c)
    expectIdle(c)


    // Test for PMA error in leaf
    println("Testing PMAError page (resolving with pointer)")
    val addr1 = requestPage(c)
    bus_read_done(c, addr1, Cat(Megapage_toleafpte, POINTER))
    expectIdle(c)
    println("Testing PMA Error Page")
    bus_read_done_access_fault(c, Megapage_toleafpte_addr, Cat(800.U(22.W), 0.U(6.W), RWXV))
    expectPMAError(c)
    expectIdle(c)

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
    def expectIdle(c: PTW) {
      step(1)
      poke(c.io.memory.readdatavalid, false.B)
      expect(c.io.done, false.B)
      expect(c.io.pagefault, false.B)
      expect(c.io.access_fault, false.B)
    }
    def expectSuccessfullResolve(c: PTW, physical_address_top: UInt, access_bits: UInt) = {
      expect(c.io.physical_address_top, physical_address_top)
      expect(c.io.access_bits, access_bits)
      expect(c.io.done, true.B)
      expect(c.io.pagefault, false.B)
      expect(c.io.access_fault, false.B)
    }

    def expectPMAError(c: PTW) {
      expect(c.io.done, true.B)
      expect(c.io.pagefault, false.B)
      expect(c.io.access_fault, true.B)
    }

    def request_resolve(c: PTW, virtual_address: UInt) = {
      poke(c.io.resolve_req, true.B)
      poke(c.io.virtual_address, virtual_address)
      expect(c.io.resolve_ack, true.B)
      expect(c.io.done, false.B)
      expect(c.io.pagefault, false.B)
      expect(c.io.access_fault, false.B)
      step(1)
      expect(c.io.resolve_ack, false.B)
      poke(c.io.resolve_req, false.B)
      expect(c.io.done, false.B)
      expect(c.io.pagefault, false.B)
      expect(c.io.access_fault, false.B)
    }

    def bus_read_done(c: PTW, expectedAddress:UInt, readdata: UInt) = {
      expect(c.io.memory.read, true.B)
      expect(c.io.memory.address, expectedAddress)
      poke(c.io.memory.readdatavalid, true.B)
      poke(c.io.memory.waitrequest, false.B)
      poke(c.io.memory.response, MemHostIfResponse.OKAY)
      poke(c.io.memory.readdata, readdata)
      //step(1)
      //poke(c.io.memory.readdatavalid, false.B)
    }
    def bus_read_done_access_fault(c: PTW, expectedAddress:UInt, readdata: UInt) {
      bus_read_done(c, expectedAddress, readdata)
      poke(c.io.memory.response, MemHostIfResponse.DECODEERROR)
    }

    def requestMegapage(c: PTW): UInt = { // always request second (index = 1) pte from table
      request_resolve(c, Cat(1.U(10.W), 0.U(10.W), 0.U(12.W)))
      return Cat(matp_ppn, 1.U(10.W), 0.U(2.W))
    }
    def requestPage(c: PTW): UInt = {
      request_resolve(c, Cat(16.U(10.W), 19.U(10.W), 0.U(12.W)))
      return Cat(matp_ppn, 16.U(10.W), 0.U(2.W))
    }*/
  }
  }
}
