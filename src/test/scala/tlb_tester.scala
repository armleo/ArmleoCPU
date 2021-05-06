
package armleocpu

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}


import Consts._
import CacheConsts._

class TLBUnitTester(c: TLB, ENTRIES_W:Int, tlb_ways: Int) extends PeekPokeTester(c) {
  def s0_none() {
    poke(c.io.s0.cmd, TLB_CMD_NONE)
  }
  def s0_resolve(virt_address: BigInt) {
    poke(c.io.s0.cmd, TLB_CMD_RESOLVE)
    poke(c.io.s0.virt_address, virt_address)
  }
  def s0_new_entry(virt_address: BigInt, access_permissions_tag_input: Int, ptag_input: BigInt) {
    poke(c.io.s0.cmd, TLB_CMD_NEW_ENTRY)
    poke(c.io.s0.virt_address, virt_address)
    poke(c.io.s0.access_permissions_tag_input, access_permissions_tag_input)
    poke(c.io.s0.ptag_input, ptag_input)
  }

  def s0_invalidate(entry: Int) {
    poke(c.io.s0.cmd, TLB_CMD_INVALIDATE)
    poke(c.io.s0.virt_address, entry)
  }
  
  def s1_expect_miss() {
    expect(c.io.s1.miss, 1)
  }

  def s1_expect_result(access_permissions_tag_output: Int, ptag_output: BigInt) {
    expect(c.io.s1.miss, 0)
    // Outputs are valid only for requests that is hit AND virtual memory is enabled
    expect(c.io.s1.access_permissions_tag_output, access_permissions_tag_output)
    expect(c.io.s1.ptag_output, ptag_output)
  }


  s0_none()
  step(1)
  println("Testing invalidate")
  for(i <- 0 until (1 << ENTRIES_W)) {
    s0_invalidate(i)
    step(1)
  }
  println("Testing miss resolution")
  s0_resolve(BigInt("1000000")) // Miss resolution request
  step(1)
  s1_expect_miss()
  println("Testing new entry")
  s0_new_entry(BigInt("1000000"), 123, BigInt("510"))
  step(1)
  println("Testing new entry is valid")
  s0_resolve(BigInt("1000000"))
  step(1)
  println("Result of new entry resolution")
  s1_expect_result(123, BigInt("510")) // hit
  s0_none()

  step(5)
}

// CRITICAL: PLEASE KEEP THIS MESSAGE BELOW
// CRITICAL: FIRRTL Backend generates confusing errors use backend verilator
class TLBTester extends ChiselFlatSpec {
  val arg_lane_width = 3
  "TLB ENTRIES_W = 2, tlb_ways = 1" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/tlb_test", "--top-name", "armleocpu_tlb"),
        () => new TLB(ENTRIES_W = 2, tlb_ways = 1)) {
      c => new TLBUnitTester(c, ENTRIES_W = 2, tlb_ways = 1)
    } should be (true)
  }
    /*
  "TLB ENTRIES_W = 2, tlb_ways = 2" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/tlb_test", "--top-name", "armleocpu_tlb"),
        () => new TLB(ENTRIES_W = 2, tlb_ways = 2)) {
      c => new TLBUnitTester(c, ENTRIES_W = 2, tlb_ways = 2)
    } should be (true)
  }

  "TLB ENTRIES_W = 6, tlb_ways = 4" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/tlb_test", "--top-name", "armleocpu_tlb"),
        () => new TLB(ENTRIES_W = 6, tlb_ways = 4)) {
      c => new TLBUnitTester(c, ENTRIES_W = 6, tlb_ways = 4)
    } should be (true)
  }*/
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