
package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import ALU._

class ALUUnitTester(c: Alu_imp) extends PeekPokeTester(c) {
    poke(c.io.op, ALU_ADD)
    poke(c.io.A, 50)
    poke(c.io.B, 50)
    expect(c.io.out, 100)
    step(1)
    poke(c.io.op, ALU_SUB)
    poke(c.io.A, 50)
    poke(c.io.B, 50)
    expect(c.io.out, 0)
    step(1)
    poke(c.io.op, ALU_MUL)
    poke(c.io.A, 4)
    poke(c.io.B, 40)
    expect(c.io.out, 160)
    step(1)
    poke(c.io.op, ALU_MUL)
    poke(c.io.A, ((BigInt(1) << 32) - 1))
    poke(c.io.B, ((BigInt(1) << 32) - 1))
    expect(c.io.out, 1)
    step(1)
    poke(c.io.op, ALU_MULH)
    poke(c.io.A, ((BigInt(1) << 32) - 1))
    poke(c.io.B, ((BigInt(1) << 32) - 1))
    expect(c.io.out, 0)
    step(1)
    poke(c.io.op, ALU_MULHU)
    poke(c.io.A, ((BigInt(1) << 32) - 1))
    poke(c.io.B, ((BigInt(1) << 32) - 1))
    println(peek(c.io.out).toString)
}


class ALUTester extends ChiselFlatSpec {
  "ALU Test" should s"alu (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/alutest", "--top-name", "armleocpu_alu"), () => new Alu_imp()) {
      c => new ALUUnitTester(c)
    } should be (true)
  }
}