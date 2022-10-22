package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum

object states extends ChiselEnum {
    val FETCH, DECODE, EXECUTE1, EXECUTE2, MEMORY, WRITEBACK = Value
}

import Consts._

class ArmleoCPU extends Module {
  val ireq_addr = Output(UInt(xLen.W))
  val ireq_valid = Output(Bool())
  val ireq_ready = Output(Bool())

  val pc = Reg(UInt(xLen.W))
  val state = Reg(states())

  ireq_addr := pc

  when (state === states.FETCH) {
    ireq_valid := true.B
    when(ireq_ready) {
      state := states.DECODE
    }
  }
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object ArmleoCPUGenerator extends App {
  (new ChiselStage).execute(Array("-frsq", "-o:memory_configs", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new ArmleoCPU)))
}


