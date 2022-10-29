package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

object fetch_cmd extends ChiselEnum {
    val fetch_cmd_none, fetch_cmd_kill, fetch_cmd_set_pc = Value
}

class fetch(val c: coreParams) extends Module {

    // -------------------------------------------------------------------------
    //  Interface
    // -------------------------------------------------------------------------
    val ibus = IO(new ibus_t(c))
    // Pipeline command interface form control unit
    val cmd = IO(Input(chiselTypeOf(fetch_cmd.fetch_cmd_none)))
    val cmd_ready = IO(Output(Bool()))
    val busy = IO(Output(Bool()))



    // -------------------------------------------------------------------------
    //  State
    // -------------------------------------------------------------------------

    // Keep the predicted PC. It is used to calculate PC + 4
    val pc = RegInit(0.U(c.xLen.W))

    // If fetch active then the request is already sent, you cant kill the request
    //  Wait until ready is asserted, then ignore the response
    // If fetch active then pc is kept the same and commands are ignored
    val fetch_request_active = RegInit(false.B)
    val fetch_response_pending = RegInit(false.B)

    // icache_ways * icache_entries * icache_entry_bytes bytes of icache
    
    val physical_addr_width = 34
    val ptag_width = physical_addr_width - log2Up(c.icache_entries * c.icache_entry_bytes)
    val icache_data  = Seq(c.icache_ways, SyncReadMem(c.icache_entries * c.icache_entry_bytes / c.ibus_data_bytes, UInt(c.ibus_data_bytes.W)))
    val icache_ptags = Seq(c.icache_ways, SyncReadMem(c.icache_entries, UInt(ptag_width.W)))
    val icache_valid = Seq(c.icache_ways, Vec(c.icache_entries, Bool()))


    
    
    // We need to keep the csr values of registers, because they may change during the request
    //  While the requirement for the cachge interface bus for these values to be fixed
    // TODO: 
    // val memory_csr_regs = RegInit(new MemoryCSRBundle)



    // -------------------------------------------------------------------------
    //  Combinational logic
    // -------------------------------------------------------------------------



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

    */


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

    //inflight_requests := inflight_requests + inflight_requests_add_subs(0) + inflight_requests_add_subs(1)
    
}