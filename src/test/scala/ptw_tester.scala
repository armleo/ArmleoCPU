package armleocpu


import chisel3._
import chisel3.util._


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


class PTWUnitTester(c: PTW) extends PeekPokeTester(c) {
    poke(c.io.virtual_address, "h0000_0000".U)
    // poke(c.io.op, PTW_ADD)
    // poke(c.io.A, 50)
    // poke(c.io.B, 50)
    // expect(c.io.out, 100)
    // step(1)
    // poke(c.io.op, PTW_SUB)
    // poke(c.io.A, 50)
    // poke(c.io.B, 50)
    // expect(c.io.out, 0)
    // step(1)
    // poke(c.io.op, PTW_MUL)
    // poke(c.io.A, 4)
    // poke(c.io.B, 40)
    // expect(c.io.out, 160)
    // step(1)
    // poke(c.io.op, PTW_MUL)
    // poke(c.io.A, ((BigInt(1) << 32) - 1))
    // poke(c.io.B, ((BigInt(1) << 32) - 1))
    // expect(c.io.out, 1)
    // step(1)
    // poke(c.io.op, PTW_MULH)
    // poke(c.io.A, ((BigInt(1) << 32) - 1))
    // poke(c.io.B, ((BigInt(1) << 32) - 1))
    // expect(c.io.out, 0)
    // step(1)
    // poke(c.io.op, PTW_MULHU)
    // poke(c.io.A, ((BigInt(1) << 32) - 1))
    // poke(c.io.B, ((BigInt(1) << 32) - 1))
    // println(peek(c.io.out).toString)
}


class PTWTester extends ChiselFlatSpec {
  "PTW Test" should s"PTW (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/PTWtest", "--top-name", "armleocpu_ptw"), () => new PTW()) {
      c => new PTWUnitTester(c)
    } should be (true)
  }
}