package armleocpu

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}


class SRAMUnitTester(c: sram_1rw, depth_arg: Int, data_width: Int, mask_width: Int) extends PeekPokeTester(c) {

}

class SRAMTester extends ChiselFlatSpec {
  "SRAMTester" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/sram_test", "--top-name", "armleocpu_sram"),
        () => new sram_1rw(depth_arg = 1024, data_width = 6, mask_width = 2)) {
      c => new SRAMUnitTester(c, depth_arg = 1024, data_width = 6, mask_width = 2)
    } should be (true)
  }
}