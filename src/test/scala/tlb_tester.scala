
package armleocpu

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}


import Consts._
import CacheConsts._

class TLBUnitTester(c: TLB, ENTRIES_W:Int, tlb_ways: Int) extends PeekPokeTester(c) {

}

// CRITICAL: PLEASE KEEP THIS MESSAGE BELOW
// CRITICAL: FIRRTL Backend generates confusing errors use backend verilator
class TLBTester extends ChiselFlatSpec {
  val arg_lane_width = 3
  "TLB ENTRIES_W = 2, tlb_ways = 1" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/tlb_test", "--top-name", "armleocpu_tlb"),
        () => new TLB(ENTRIES_W = 2, tlb_ways = 1, debug = true)) {
      c => new TLBUnitTester(c, ENTRIES_W = 2, tlb_ways = 1)
    } should be (true)
  }
    /*
  "TLB ENTRIES_W = 2, tlb_ways = 2" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/tlb_test", "--top-name", "armleocpu_tlb"),
        () => new TLB(ENTRIES_W = 2, tlb_ways = 2, debug = true)) {
      c => new TLBUnitTester(c, ENTRIES_W = 2, tlb_ways = 2)
    } should be (true)
  }

  "TLB ENTRIES_W = 6, tlb_ways = 4" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/tlb_test", "--top-name", "armleocpu_tlb"),
        () => new TLB(ENTRIES_W = 6, tlb_ways = 4, debug = true)) {
      c => new TLBUnitTester(c, ENTRIES_W = 6, tlb_ways = 4)
    } should be (true)
  }*/
}

object TLBDriver extends App {
  (new ChiselStage).execute(Array("-frsq", "-c:CacheBackstorage:-o:generated_vlog/tlb_mems.conf","--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new TLB(ENTRIES_W = 6, tlb_ways = 4, debug = true))))
}


/*
import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3._


class TLBUnitTester(c: TLB) extends PeekPokeTester(c) {
    // write to virt 0 with phys 50, accesstag
    poke(c.io.enable, true.B)

    poke(c.io.write, true.B)
    poke(c.io.invalidate, false.B)
    poke(c.io.resolve, false.B)
    poke(c.io.virt, 0.U)

    poke(c.io.accesstag_input, "b11011101".U)
    poke(c.io.phystag_input, 50.U)

    step(1) // write done
    // write to virt 1 with phys 50, accesstag
    poke(c.io.virt, 1.U)
    poke(c.io.accesstag_input, "b11011111".U)
    poke(c.io.phystag_input, 51.U)

    step(1) // write done
    // read from virt 0
        poke(c.io.virt, 0.U)
        poke(c.io.write, false.B)
        poke(c.io.resolve, true.B)
    step(1) // read done
        // read done, check results
        expect(c.io.miss, false.B)
        expect(c.io.done, true.B)
        expect(c.io.phystag_output, 50.U)
        expect(c.io.accesstag_output, "b11011101".U)
        // init next read at virt = 1
        poke(c.io.virt, 1.U)
        
    step(1) // read done
        // check variables
        expect(c.io.miss, false.B)
        expect(c.io.done, true.B)
        expect(c.io.phystag_output, 51.U)
        expect(c.io.accesstag_output, "b11011111".U)
        // disable resolve, invalidate
        poke(c.io.resolve, false.B)
        poke(c.io.invalidate, true.B)
    step(1) // invalidate done, request read that should miss
        poke(c.io.invalidate, false.B)
        poke(c.io.resolve, true.B)
    step(1)
        poke(c.io.resolve, false.B) // disable all commands
        expect(c.io.done, true.B)
        expect(c.io.miss, true.B)
    // done
    step(10) // all tests done
}


class TLBTester extends ChiselFlatSpec {
    "TLBTest" should s"" in {
        
        Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/tlbtest", "--top-name", "armleocpu_tlb"), () => new TLB(/*ENTRIES=2, ENTRIES_W=*/1, /*debug=*/true, /*mememulate=*/true)) {
            c => new TLBUnitTester(c)
        } should be (true)
    }
}*/