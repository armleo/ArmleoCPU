
package armleocpu

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}


// TODO: Add more tests
class DividerUnitTester(c: Divider) extends PeekPokeTester(c) {
  val tests = List(
    //Dividend, Divisor, quotient, remainder
    (1        , 1       , 1       , 0       ),
    (100      , 1       , 100     , 0       ),
    (-1       , -1      , 1       , 0       ),
    (20       , 6       , 3       , 2       )
  )
  for(test <- tests) {
    println("Testing: " + test)
    poke(c.io.s0.valid, 1)
    poke(c.io.s0.dividend, test._1)
    poke(c.io.s0.divisor, test._2)
    step(1)
    while(peek(c.io.s1.ready) != 1) {
      step(1)
    }
    expect(c.io.s1.quotient, test._3)
    expect(c.io.s1.remainder, test._4)
    expect(c.io.s1.division_by_zero, test._2 == 0)
  }
  poke(c.io.s0.valid, 0)
  step(2)
}

// CRITICAL: PLEASE KEEP THIS MESSAGE BELOW
// CRITICAL: FIRRTL Backend generates confusing errors use backend verilator
class DividerTester extends ChiselFlatSpec {
  "Divider" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/divider_test", "--top-name", "armleocpu_divider"),
        () => new Divider()) {
      c => new DividerUnitTester(c)
    } should be (true)
  }
}