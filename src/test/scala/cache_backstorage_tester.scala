package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


import Consts._
import CacheConsts._

class CacheBackstorageUnitTester(c: CacheBackstorage) extends PeekPokeTester(c) {
  def s0_noop() {
    poke(c.io.s0.valid, 0)
  }

  def s1_noop() {
    poke(c.io.s1.ptag, 101)
  }

  def s0_write_all_ways(write_mask:Int, write_full_tag: Int, state_tag_in: Int, address_tag_in: Int, lane: Int, offset: Int) {
    poke(c.io.s0.valid, 1)
    poke(c.io.s0.req_type, CB_WRITE_ALL_WAYS)
    for(i <- 0 until 8) {
      poke(c.io.s0.write_mask(i), (write_mask >> i) & 1) // TODO:
    }

    poke(c.io.s0.write_full_tag, write_full_tag)
    poke(c.io.s0.way_idx_in, 3)
    poke(c.io.s0.state_tag_in, state_tag_in)
    poke(c.io.s0.address_tag_in, address_tag_in)

    poke(c.io.s0.lane, lane)
    poke(c.io.s0.offset, offset)

  }

  def s0_read_request(lane: Int, offset: Int) {
    poke(c.io.s0.valid, 1)
    poke(c.io.s0.req_type, CB_READ)
    for(i <- 0 until 8) {
      poke(c.io.s0.write_mask(i), 1) // TODO:
    }
    poke(c.io.s0.write_full_tag, 1)
    poke(c.io.s0.way_idx_in, 2)
    poke(c.io.s0.state_tag_in, 3)
    poke(c.io.s0.address_tag_in, 0)

    poke(c.io.s0.lane, lane)
    poke(c.io.s0.offset, offset)
  }

  def s1_read_request(ptag: Int) {
    poke(c.io.s1.ptag, ptag)
  }

  def s1_expect_hit(hit: Int) {
    expect(c.io.s1.hit, hit)
  }



  // Start
  s0_noop()
  step(1)

  s0_write_all_ways(write_mask = 0, write_full_tag = 1, state_tag_in = 0x1, address_tag_in = 110, lane = 0, offset = 3)
  s1_noop()

  step(1)
  s0_write_all_ways(write_mask = 0, write_full_tag = 1, state_tag_in = 0x1, address_tag_in = 111, lane = 0, offset = 3)
  s1_noop()

  step(1)
  s0_noop()
  s1_noop()

  step(1)
  s0_read_request(lane = 0, offset = 3)
  s1_noop()

  step(1)
  s0_noop()
  s1_read_request(ptag = 111)
  step(0)
  s1_expect_hit(1)

  step(1)
  s0_noop()
  s1_noop()

  step(1)
  s0_noop()
  s1_noop()
}

// CRITICAL: PLEASE KEEP THIS MESSAGE BELOW
// CRITICAL: FIRRTL Backend generates confusing errors use backend verilator
class CacheBackstorageTester extends ChiselFlatSpec {
  "Cache Backstorage" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/cache_backstorage_test", "--top-name", "armleocpu_cache_backstorage"),
        () => new CacheBackstorage(new CacheParams(arg_tag_width = 64 - 12, arg_ways = 4))) {
      c => new CacheBackstorageUnitTester(c)
    } should be (true)
  }
}