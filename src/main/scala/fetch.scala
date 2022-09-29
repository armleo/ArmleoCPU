package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

object fetch_cmd extends ChiselEnum {
    val fetch_cmd_none, fetch_cmd_kill, fetch_cmd_set_pc = Value
}

class fetch extends Module {
    // -------------------------------------------------------------------------
    //  Parameters
    // -------------------------------------------------------------------------
    val maxRequests = 4
    val maxRequests_W = utils.clog2(maxRequests)


    // -------------------------------------------------------------------------
    //  Interface
    // -------------------------------------------------------------------------
    // The interface used to send requests
    val request_interface = IO(Flipped(ICacheInterface))
    val response_interface = IO(Flipped(DecoupledIO(Output(UInt(xLen.W)))))

    // Pipeline command interface form control unit
    val cmd = IO(Input(fetch_cmd))
    val cmd_ready = IO(Output(Bool()))
    val 



    // -------------------------------------------------------------------------
    //  State
    // -------------------------------------------------------------------------

    // Keep the predicted PC. It is used to calculate PC + 4
    val pc = RegInit(0.U(xLen.W))

    // If fetch active then the request is already sent, you cant kill the request
    //  Wait until ready is asserted, then ignore the response
    // If fetch active then pc is kept the same and commands are ignored
    val fetch_active = RegInit(Bool())

    // We need to keep the csr values of registers, because they may change during the request
    //  While the requirement for the cachge interface bus for these values to be fixed
    val memory_csr_regs = RegInit(new MemoryCSRBundle)

    //      inflight_requests keep track from 0 to maxRequests
    //      if maxRequests == inflight_requests no more fetches are sent
    val inflight_requests = RegInit(0.S(maxRequests_W.W))
    



    // -------------------------------------------------------------------------
    //  Combinational logic
    // -------------------------------------------------------------------------
    //
    // inflight_requests are incremented/decremented by every element of inflight_requests_add_subs
    // It is for the purpose of future scalability
    // 
    // Initialize to zero values, so it will be kept the same by default
    // We dont need to do premature optimization, instead we can just use a simple Mux
    // for these wire, as the optimizer will simply optimize it themselves
    val inflight_requests_add_subs = Array(
                            Wire(SInt((maxRequests_W + 1).W)),
                            Wire(SInt((maxRequests_W + 1).W))
                        )
    
    for (n <- inflight_requests_add_subs) {
        n := 0.S
    }


    // States:
    //          Not fetching, Killed
    //          Fetching, new instruction
    //              new pc supplied
    //          Fetching, not working on new instruction
    //          Fetching, killing
    //          Not fetching because too much requests

    // TODO: Set the request/response to idle
    request_interface.valid := false.B
    response_interface.ready := false.B
    cmd_ready := false.B
    
    when(!fetch_active && (inflight_requests === 0.U) && (cmd === fetch_cmd_kill)) {
        // Not fetching, no inflight requests, killing
        // No active request, no inflight requests. No action needed

        cmd_ready := true.B
        // Tell control unit that the execution is stopped
    } .elsewhen (!fetch_active && (inflight_requests =/= 0.U) && (cmd === fetch_cmd_kill)) {
        // Not fetching, inflight requests, killing
        // TODO: 
    } .elsewhen (fetch_active) {
        // Active fetch
            // If request is accepted, then
            //      TODO: Increment inflight requests
            // else
            //      TODO:

    }
    
    
    /* .elsewhen (fetch_active) {
        // Fetch is active, keep it
    } .elsewhen (!fetch_active) {
        // Fetch is not active
    }*/




    /*
    request_interface.addr := Mux(fetch_active, pc, pc_next)

    request_interface.valid := inflight_requests =/= (maxRequests - 1).S
    

    when((request_interface.valid && request_interface.ready)) {
        inflight_requests_add_subs(0) := 1.S(maxRequests_W.W)
    }
    
    response_interface.ready := false.B
    when(response_interface.valid) {
        inflight_requests_add_subs(1) := -1.S(maxRequests_W.W)
        response_interface.ready := true.B
    }
    */

    inflight_requests := inflight_requests + inflight_requests_add_subs(0) + inflight_requests_add_subs(1)
}