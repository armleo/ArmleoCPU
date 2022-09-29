package armleocpu


import chisel3._
import chisel3.util._

import Consts._



import scala.math._

object utils {
    def clog2(x: Int): Int = { require(x > 0); ceil(log(x)/log(2)).toInt }
}

class ArmleoCPU extends Module {
    val maxRequests = 4
    val maxRequests_W = utils.clog2(maxRequests)

    val fetch_interface = IO(DecoupledIO(Output(UInt(xLen.W))))
    val response_interface = IO(Flipped(DecoupledIO(Output(UInt(xLen.W)))))
    val kill = IO(Input(Bool()))
    val pc = RegInit(0.U(xLen.W))



    //      inflight_requests keep track from 0 to maxRequests
    //      if maxRequests == inflight_requests no more fetches are sent
    //
    //      inflight_requests are incremented/decremented by every element of inflight_requests_add_subs
    //      

    val inflight_requests = RegInit(0.S(maxRequests_W.W))
    
    val inflight_requests_add_subs = Array(
                            Wire(SInt((maxRequests_W + 1).W)),
                            Wire(SInt((maxRequests_W + 1).W))
                        )
    for (n <- inflight_requests_add_subs) {
        n := 0.S
    }



    // States:
    //          Fetching, new instruction
    //          Fetching, not working on new instruction
    //          Fetching, killing
    //          Killed
    //          Not fetching because too much requests
    //          
    
    fetch_interface.bits := pc
    fetch_interface.valid := inflight_requests =/= (maxRequests - 1).S
    

    when((fetch_interface.valid && fetch_interface.ready)) {
        inflight_requests_add_subs(0) := 1.S(maxRequests_W.W)
    }
    
    response_interface.ready := false.B
    when(response_interface.valid) {
        inflight_requests_add_subs(1) := -1.S(maxRequests_W.W)
        response_interface.ready := true.B
    }

    inflight_requests := inflight_requests + inflight_requests_add_subs(0) + inflight_requests_add_subs(1)
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object ArmleoCPUGenerator extends App {
  (new ChiselStage).execute(Array("-frsq", "-o:memory_configs", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new ArmleoCPU)))
}


