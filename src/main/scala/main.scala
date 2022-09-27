package armleocpu

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}




/*
object ALUDriver extends App {
  (new ChiselStage).execute(args, Seq(ChiselGeneratorAnnotation(() => new ALU)))
}

object CacheBackstorageDriver extends App {
  (new ChiselStage).execute(Array("-frsq", "-c:CacheBackstorage:-o:generated_vlog/cache_backstorage_mems.conf","--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new CacheBackstorage(new CacheParams(arg_tag_width = 64 - 12 + (6 - 3), arg_ways = 4, arg_lane_width = 3)))))
}


object TLB_Driver extends App {
  (new ChiselStage).execute(Array("-frsq", "-c:TLB:-o:generated_vlog/tlb_mems", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new TLB(ENTRIES_W = 2, tlb_ways = 2))))
}


object CCXInterconnect_Driver extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new CCXInterconnect(n = 2, addr_width = 10))))
}*/