package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


import Consts._
import CacheConsts._




class CacheBackstorageUnitTester(c: CacheBackstorage) extends PeekPokeTester(c) {
    step(1)

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