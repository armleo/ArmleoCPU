
package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3._

import Control._

import scala.math.BigInt

class StoreGenUnitTester(c: StoreGen) extends PeekPokeTester(c) {
    val testData1 = BigInt("2309737967") // hex: 0x89AB_CDEF
    val testData2 = BigInt("19088743") // hex: 0x0123_4567
    
    poke(c.io.inwordOffset, 0)
    poke(c.io.st_type, ST_SW)
    poke(c.io.rawWritedata, testData1)
    //poke(c.io.rawReaddata, testData2)

    expect(c.io.resultWritedata, testData1)
    expect(c.io.missAlligned, false.B)

    for(i <- 1 to 3) {
        poke(c.io.inwordOffset, i)
        expect(c.io.missAlligned, true.B)
    }
    step(1)

    poke(c.io.inwordOffset, 0)
    poke(c.io.st_type, ST_SH)
    assert((peek(c.io.resultWritedata) & 0xFFFF) == (testData1 & 0xFFFF))
    expect(c.io.mask, 3) // b0011
    expect(c.io.missAlligned, false.B)

    poke(c.io.inwordOffset, 1)
    poke(c.io.st_type, ST_SB)
    assert(((peek(c.io.resultWritedata) >> 8) & 0xFF) == (testData1 & 0xFF))
    expect(c.io.mask, 2) // b0010
    expect(c.io.missAlligned, false.B)

    

/*
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
    }*/

    // TODO: Test half words
}


class StoreGenTester extends ChiselFlatSpec {
    "StoreGenTest" should s"" in {
        
        Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/StoreGentest", "--top-name", "armleocpu_StoreGen"), () => new StoreGen()) {
            c => new StoreGenUnitTester(c)
        } should be (true)
    }
}