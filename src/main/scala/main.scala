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