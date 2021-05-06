
package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3._

import Control._

import scala.math.BigInt

class LoadGenUnitTester(c: LoadGen) extends PeekPokeTester(c) {
    val testData1 = BigInt("0123456789ABCDEF", 16)

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.ld_type, LD_LD)
        poke(c.io.raw_data, testData1)
        if(i == 0) {
            expect(c.io.result_data, testData1)
            expect(c.io.miss_alligned, false.B)
        } else {
            expect(c.io.miss_alligned, true.B)
        }
        step(1)
    }

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.ld_type, LD_LWU)
        poke(c.io.raw_data, testData1)
        if(i % 4 == 0) {
            expect(c.io.result_data, (testData1 >> (i * 8)) & BigInt("FFFFFFFF", 16))
            expect(c.io.miss_alligned, false.B)
        } else {
            expect(c.io.miss_alligned, true.B)
        }
        step(1)
    }

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.ld_type, LD_LHU)
        poke(c.io.raw_data, testData1)
        if(i % 2 == 0) {
            expect(c.io.result_data, (testData1 >> (i * 8)) & BigInt("FFFF", 16))
            expect(c.io.miss_alligned, false.B)
        } else {
            expect(c.io.miss_alligned, true.B)
        }
        step(1)
    }

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.ld_type, LD_LBU)
        poke(c.io.raw_data, testData1)
        expect(c.io.result_data, (testData1 >> (i * 8)) & BigInt("FF", 16))
        expect(c.io.miss_alligned, false.B)
        step(1)
    }

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.ld_type, LD_LB)
        poke(c.io.raw_data, testData1)
            val expected_raw = (testData1 >> (i * 8)) & BigInt("FF", 16)
            val sign = ((expected_raw & (BigInt("1") << 7)) > 0)
            val sign_extension = (if(sign) BigInt("FFFFFFFFFFFFFF00", 16) else BigInt("0", 16))
            val expected_extended = expected_raw | sign_extension
            expect(c.io.result_data, expected_extended)
            expect(c.io.miss_alligned, false.B)
        step(1)
    }

    
    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.ld_type, LD_LH)
        poke(c.io.raw_data, testData1)
        if(i % 2 == 0) {
            val expected_raw = (testData1 >> (i * 8)) & BigInt("FFFF", 16)
            val sign = ((expected_raw & (BigInt("1") << 15)) > 0)
            val sign_extension = (if(sign) BigInt("FFFFFFFFFFFF0000", 16) else BigInt("0", 16))
            val expected_extended = expected_raw | sign_extension
            expect(c.io.result_data, expected_extended)
            expect(c.io.miss_alligned, false.B)
        } else {
            expect(c.io.miss_alligned, true.B)
        }
        step(1)
    }

    for(i <- 0 until 8) {
        poke(c.io.inword_offset, i)
        poke(c.io.ld_type, LD_LW)
        poke(c.io.raw_data, testData1)
        if(i % 4 == 0) {
            val expected_raw = (testData1 >> (i * 8)) & BigInt("FFFFFFFF", 16)
            val sign = ((expected_raw & (BigInt("1") << 31)) > 0)
            val sign_extension = (if(sign) BigInt("FFFFFFFF00000000", 16) else BigInt("0", 16))
            val expected_extended = expected_raw | sign_extension
            expect(c.io.result_data, expected_extended)
            expect(c.io.miss_alligned, false.B)
        } else {
            expect(c.io.miss_alligned, true.B)
        }
        step(1)
    }
}


class LoadGenTester extends ChiselFlatSpec {
    "LoadGenTest" should s"" in {
        
        Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/LoadGentest", "--top-name", "armleocpu_LoadGen"), () => new LoadGen()) {
            c => new LoadGenUnitTester(c)
        } should be (true)
    }
}