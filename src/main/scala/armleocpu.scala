package armleocpu


import chisel3._
import chisel3.util._

import Consts._

class ArmleoCPU extends Module {

    val maxRequests = 4

    val fetch_interface = IO(DecoupledIO(Output(UInt(xLen.W))))
    val response_interface = IO(Flipped(DecoupledIO(Output(UInt(xLen.W)))))

    val pc = RegInit(0.U(xLen.W))
    val inflight_requests = RegInit(0.U(maxRequests.W))
    val start_new_request = (inflight_requests =/= (maxRequests - 1).U) &&
    

    fetch_interface.bits := pc
    fetch_interface.valid := inflight_requests =/= (maxRequests - 1).U


    when (fetch_interface.valid) {
        pc := 
    }

    when(fetch_interface.valid && fetch_interface.ready) {
        inflight_requests := inflight_requests + 1.U
    }

    response_interface.ready := false.B
    when(response_interface.valid) {
        

        response_interface.ready := true.B
    }
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object ArmleoCPUGenerator extends App {
  (new ChiselStage).execute(Array("-frsq", "-o:memory_configs", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new ArmleoCPU)))
}


