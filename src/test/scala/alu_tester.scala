
package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import ALU._
import Consts._

class ALUUnitTester(c: ALU) extends PeekPokeTester(c) {
  val uint32_max = BigInt("FFFFFFFF", 16)
  val uint64_max = BigInt("FFFFFFFFFFFFFFFF", 16);
  val fifty = BigInt("50")
  val zero = BigInt("0")
  val one = BigInt("1")
  // Using BigInt is required because otherwise type infering does not work
  // It just converts everything into Any

  poke(c.io.dw, DW_32)

  val tests = List(
    //OP    , A          , B        , shamt, result expected
    (FN_ADD, uint32_max, uint32_max, (uint64_max << 1) & uint64_max),
    (FN_ADD, fifty     , fifty     , fifty + fifty),
    (FN_SUB, fifty     , fifty     , zero),
    (FN_SUB, uint32_max, uint32_max, zero),
    (FN_SRA, uint32_max, zero      , uint64_max),
    (FN_SRA, uint32_max, one       , uint64_max),
    (FN_SR , uint32_max, one       , uint32_max >> 1),
    (FN_AND, uint64_max, zero      , zero),
    (FN_AND, uint64_max, uint32_max, uint64_max),
    (FN_XOR, uint32_max, uint32_max, zero)
  )
  for(test <- tests) {
    poke(c.io.fn, test._1)
    poke(c.io.in1, test._2)
    poke(c.io.in2, test._3)
    step(0)
    expect(c.io.out, test._4)
    step(1)
  }
}


class ALUTester extends ChiselFlatSpec {
  "ALU Test" should s"alu (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/alutest", "--top-name", "armleocpu_alu"), () => new ALU()) {
      c => new ALUUnitTester(c)
    } should be (true)
  }
}