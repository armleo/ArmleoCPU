package armleocpu

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}



object ArmleoCPUDriver extends App {
  //(new ChiselStage).emitVerilog(new ArmleoCPU)
}


object ALUDriver extends App {
  (new ChiselStage).execute(args, Seq(ChiselGeneratorAnnotation(() => new ALU)))
}

object RegfileDriver extends App {
  (new ChiselStage).execute(args, Seq(ChiselGeneratorAnnotation(() => new Regfile)))
}

object CacheBackstorageDriver extends App {
  (new ChiselStage).execute(Array("-frsq", "-c:CacheBackstorage:-o:generated_vlog/cache_backstorage_mems.conf","--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new CacheBackstorage(new CacheParams(arg_tag_width = 64 - 12 + (6 - 3), arg_ways = 4, arg_lane_width = 3)))))
}


object sram_1rw_Driver extends App {
  (new ChiselStage).execute(Array("-frsq", "-c:sram_1rw:-o:generated_vlog/sram_1rw_mems.conf", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new sram_1rw(depth_arg = 1 << 10, data_width = 32, mask_width = 4))))
}

object Divider_Driver extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Divider())))
}

object MultiplierDriver extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Multiplier())))
}


object TLB_Driver extends App {
  (new ChiselStage).execute(Array("-frsq", "-c:TLB:-o:generated_vlog/tlb_mems", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new TLB(ENTRIES_W = 2, tlb_ways = 2))))
}