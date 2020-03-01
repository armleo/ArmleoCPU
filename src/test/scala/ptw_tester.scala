package armleocpu


import chisel3._
import chisel3.util._


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


class PTWUnitTester(c: PTW) extends PeekPokeTester(c) with CatUtil {
    val RWXV = "h0F".U(10.W)
    val POINTER = "h01".U(10.W)
    val matp_ppn = 4.U(22.W)
    val aligned_Megapage = Cat(5.U(12.W), 0.U(10.W))
    val Megapage_toleafpte = Cat(5.U(12.W), 4.U(10.W))
    val Megapage_toleafpte_addr = Cat(5.U(12.W), 4.U(10.W), 19.U(10.W), 0.U(2.W))
    // set to default
    poke(c.io.memory.waitrequest, false.B)
    poke(c.io.memory.readdata, 0.U)
    poke(c.io.memory.response, MemHostIfResponse.DECODEERROR)
    poke(c.io.memory.readdatavalid, false.B)
    poke(c.io.matp_ppn, matp_ppn)
    poke(c.io.matp_mode, true.B)
    poke(c.io.resolve_req, false.B)

    // Test for read going low after read handshake for megapage
    println("Requesting megapage")
    val addr = requestMegapage(c)
    bus_read_done(c, addr, Cat(aligned_Megapage, RWXV))
    expect(c.io.done, true.B)
    expect(c.io.pagefault, false.B)
    expect(c.io.access_fault, false.B)
    step(1)
    poke(c.io.memory.readdatavalid, false.B)
    
    println("Requesting page")
    val addr1 = requestPage(c)
    bus_read_done(c, addr1, Cat(Megapage_toleafpte, POINTER))
    step(1)
    poke(c.io.memory.readdatavalid, false.B)
    println("Requesting page's direct PTE")
    bus_read_done(c, Megapage_toleafpte_addr, Cat(800.U(22.W), RWXV))
    expect(c.io.done, true.B)
    expect(c.io.pagefault, false.B)
    expect(c.io.access_fault, false.B)
    expect(c.io.physical_address_top, 800.U(22.W))
    expect(c.io.access_bits, "h0F".U(8.W))
    step(1)
    poke(c.io.memory.readdatavalid, false.B)
    
    // Test for read going low after read handshake for page
    
    // Test for PMA error in megapage leaf
    // Test for PMA error in leaf
    // Test for missaligned megapage
    // Test for missaligned page (should always be false)

    // Test for megapage leaf
    //                      w/ rwx
    //                      w/ rw
    //                      w/ rx
    //                      w/ r
    //                      w/ x

    // Test for valid leaf  w/ rwx
    //                      w/ rw
    //                      w/ rx
    //                      w/ r
    //                      w/ x
    
    // Test for invalid leaf
    //                        w/ i
    //                        w/ w
    //                        w/ xw
    
    // Test for invalid megapage leaf
    //                        w/ i
    //                        w/ w
    //                        w/ xw
    
    def request_resolve(c: PTW, virtual_address: UInt) = {
      poke(c.io.resolve_req, true.B)
      poke(c.io.virtual_address, virtual_address)
      expect(c.io.resolve_ack, true.B)
      step(1)
      expect(c.io.resolve_ack, false.B)
      poke(c.io.resolve_req, false.B)
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

    def requestMegapage(c: PTW): UInt = { // always request second (index = 1) pte from table
      request_resolve(c, Cat(1.U(10.W), 0.U(10.W), 0.U(12.W)))
      return Cat(matp_ppn, 1.U(10.W), 0.U(2.W))
    }
    def requestPage(c: PTW): UInt = {
      request_resolve(c, Cat(16.U(10.W), 19.U(10.W), 0.U(12.W)))
      return Cat(matp_ppn, 16.U(10.W), 0.U(2.W))
    }
}


class PTWTester extends ChiselFlatSpec {
  "PTW Test" should s"PTW (with firrtl)" in {
    Driver.execute(Array("--fint-write-vcd", "--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/PTWtest", "--top-name", "armleocpu_ptw"), () => new PTW(true)) {
      c => new PTWUnitTester(c)
    } should be (true)
  }
  "PTW Test" should s"PTW (with verilator)" in {
    Driver.execute(Array("--fint-write-vcd", "--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/PTWtest", "--top-name", "armleocpu_ptw"), () => new PTW(true)) {
      c => new PTWUnitTester(c)
    } should be (true)
  }
}
