
package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3._

import Control._

import scala.math.BigInt

class LoadGenUnitTester(c: LoadGen) extends PeekPokeTester(c) {
    val testData = BigInt("2309737967")
    poke(c.io.inwordOffset, 0)
    poke(c.io.ld_type, LD_LW)
    poke(c.io.rawData, testData)
    expect(c.io.result, testData)
    expect(c.io.missAlligned, false.B)

    for(i <- 1 to 3) {
        poke(c.io.inwordOffset, i)
        expect(c.io.missAlligned, true.B)
    }

    // missaligned zero
    poke(c.io.ld_type, LD_LBU)
    for(i <- 0 to 3) {
        poke(c.io.inwordOffset, i)
        expect(c.io.missAlligned, false.B)
        expect(c.io.result, (testData >> (i * 8)) & 0xFF)
    }
    poke(c.io.ld_type, LD_LB)
    poke(c.io.inwordOffset, 0)
    poke(c.io.rawData, 1)
    expect(c.io.missAlligned, false.B)
    expect(c.io.result, 1)

    // signed extension test
    poke(c.io.ld_type, LD_LB)
    poke(c.io.rawData, BigInt("4294967295"))

    for(i <- 0 to 3) {
        poke(c.io.inwordOffset, i)
        expect(c.io.missAlligned, false.B)
        expect(c.io.result, BigInt("4294967295"))
    }

    // TODO: Test half words
}


class LoadGenTester extends ChiselFlatSpec {
    "LoadGenTest" should s"" in {
        
        Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/LoadGentest", "--top-name", "armleocpu_LoadGen"), () => new LoadGen()) {
            c => new LoadGenUnitTester(c)
        } should be (true)
    }
}