
package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


class CacheUnitTester(c: Cache) extends PeekPokeTester(c) {
  step(10)
}


class CacheTester extends ChiselFlatSpec {
  "cache" should s"cache (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/cachetest", "--top-name", "armleocpu_cache"), () => new Cache(7, 7, true, true)) {
      c => new CacheUnitTester(c)
    } should be (true)
  }
}