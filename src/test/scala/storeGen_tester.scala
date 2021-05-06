
package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3._

import Control._

import scala.math.BigInt

class StoreGenUnitTester(c: StoreGen) extends PeekPokeTester(c) {
    val testData1 = BigInt("0123456789ABCDEF", 16)
    

    // Test cases:
    // SD: 0
    // SD: All missaligned values

    // SW: 0, 4
    // SW: All missaligned
    
    // SH: 0, 2, 4, 6
    // SH: All missaligned

    // SB: 0 - 7
    // No missaligned

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.st_type, ST_SD)
        poke(c.io.raw_write_data, testData1)
        if(i == 0) {
            expect(c.io.result_write_data, testData1)
            expect(c.io.miss_alligned, false.B)
            expect(c.io.mask, "b11111111".U)
        } else {
            expect(c.io.miss_alligned, true.B)
        }
        step(1)
    }

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.st_type, ST_SW)
        poke(c.io.raw_write_data, testData1)
        if((i % 4) == 0) {
            val wd = (peek(c.io.result_write_data) >> (i * 8)) & BigInt("FFFFFFFF", 16)
            val lsw = testData1 & BigInt("FFFFFFFF", 16)
            expect(wd == lsw, "SW: not ok")
            expect(c.io.miss_alligned, false.B)
            expect(c.io.mask, BigInt("1111", 2) << i)
        } else {
            expect(c.io.miss_alligned, true.B)
        }
        step(1)
    }

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.st_type, ST_SH)
        poke(c.io.raw_write_data, testData1)
        if((i % 2) == 0) {
            val wd = (peek(c.io.result_write_data) >> (i * 8)) & BigInt("FFFF", 16)
            val lsw = testData1 & BigInt("FFFF", 16)
            expect(wd == lsw, "SH: not ok")
            expect(c.io.miss_alligned, false.B)
            expect(c.io.mask, BigInt("11", 2) << i)
        } else {
            expect(c.io.miss_alligned, true.B)
        }
        step(1)
    }

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.st_type, ST_SB)
        poke(c.io.raw_write_data, testData1)
        val wd = (peek(c.io.result_write_data) >> (i * 8)) & BigInt("FF", 16)
        val lsw = testData1 & BigInt("FF", 16)
        expect(wd == lsw, "SB: not ok")
        expect(c.io.miss_alligned, false.B)
        expect(c.io.mask, BigInt("1", 2) << i)
        step(1)
    }
}


class StoreGenTester extends ChiselFlatSpec {
    "StoreGenTest" should s"" in {
        
        Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/StoreGentest", "--top-name", "armleocpu_StoreGen"), () => new StoreGen()) {
            c => new StoreGenUnitTester(c)
        } should be (true)
    }
}