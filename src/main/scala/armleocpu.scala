package armleocpu


import chisel3._
import chisel3.util._

import Consts._

class ArmleoCPU extends Module {
  val pc = Reg(xLen.W)
  
  when (state === STATE_FETCH) {

  }
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object ArmleoCPUGenerator extends App {
  (new ChiselStage).execute(Array("-frsq", "-o:memory_configs", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new ArmleoCPU)))
}


