package armleocpu


import chisel3._
import chisel3.util._


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


class PTWUnitTester(c: PTW) extends PeekPokeTester(c) {
    
    poke(c.io.memory.waitrequest, false.B)
    poke(c.io.memory.readdata, "h010_000_01".U)
    poke(c.io.memory.response, 3.U)
    poke(c.io.memory.readdatavalid, false.B)
    poke(c.io.satp_ppn, 4.U)
    poke(c.io.satp_mode, true.B)
    poke(c.io.virtual_address, "h0000_0111".U)
    poke(c.io.request, true.B)
    step(1)
    poke(c.io.memory.waitrequest, false.B)
    poke(c.io.memory.readdatavalid, false.B)
    poke(c.io.request, false.B)
    step(10)
    poke(c.io.memory.readdata, "h010_000_0F".U)
    poke(c.io.memory.response, 0.U)
    poke(c.io.memory.readdatavalid, true.B)
    step(1)
    poke(c.io.memory.readdatavalid, false.B)
    step(10)
}


class PTWTester extends ChiselFlatSpec {
  "PTW Test" should s"PTW (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/PTWtest", "--top-name", "armleocpu_ptw"), () => new PTW(true)) {
      c => new PTWUnitTester(c)
    } should be (true)
  }
}