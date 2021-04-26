package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


import Consts._
import CacheConsts._


class CacheBackstorageUnitTester(c: CacheBackstorage) extends PeekPokeTester(c) {
    
}


class CacheBackstorageTester extends ChiselFlatSpec {
  "Cache Backstorage" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/cache_backstorage_test"),
        () => new CacheBackstorage(new CacheParams(arg_tag_width = 64 - 12, arg_ways = 4))) {
      c => new CacheBackstorageUnitTester(c)
    } should be (true)
  }
}