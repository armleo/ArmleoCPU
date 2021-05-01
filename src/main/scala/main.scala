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


object sram_1rw_Driver extends App {
  (new ChiselStage).execute(Array("-frsq", "-m:sram_1rw;-o:generated_vlog/sram_1rw_mems","--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new sram_1rw(10, 32, 4))))
}