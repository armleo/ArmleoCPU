package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

object fetch_cmd extends ChiselEnum {
    val none, kill, set_pc, flush = Value
}


  
// FETCH
class fetch_uop_t(val c: coreParams) extends Bundle {
  val pc              = UInt(c.xLen.W)
  val instr           = UInt(c.iLen.W)
}


class fetch(val c: coreParams) extends Module {

    // -------------------------------------------------------------------------
    //  Interface
    // -------------------------------------------------------------------------
    val ibus              = IO(new ibus_t(c))
    // Pipeline command interface form control unit
    val cmd               = IO(Input(chiselTypeOf(fetch_cmd.none)))
    val cmd_ready         = IO(Output(Bool()))
    val new_pc            = IO(Input(c.reset_vector.U(c.apLen.W)))
    val busy              = IO(Output(Bool()))

    val fetch_uop         = IO(Output(new fetch_uop_t(c)))
    val fetch_uop_valid   = IO(Output(Bool()))
    val fetch_uop_accept  = IO(Input (Bool()))

    // -------------------------------------------------------------------------
    //  State
    // -------------------------------------------------------------------------

    val pc                    = RegInit(c.reset_vector.U(c.apLen.W))
    val hold_fetch_uop        = Reg(new fetch_uop_t(c))
    val hold_fetch_uop_valid  = RegInit(false.B)
    val output_stage_active   = RegInit(false.B)
    val output_stage_pc       = Reg(UInt(c.xLen.W))
    val cache_refill_active   = RegInit(false.B)
    val ptw_refill_active     = RegInit(false.B)
    
    // -------------------------------------------------------------------------
    //  Combinational
    // -------------------------------------------------------------------------
    val start_new_request = Wire(Bool())
    val cache_miss = Wire(Bool())
    val tlb_miss = Wire(Bool())

    start_new_request := false.B
    busy := false.B

    when(hold_fetch_uop_valid) { // holding
      fetch_uop := hold_fetch_uop
      fetch_uop_valid := true.B
      when(fetch_uop_accept) {
        hold_fetch_uop_valid := false.B
        start_new_request := true.B
      }
      busy := true.B
    } .elsewhen (output_stage_active) { // If the response is accepted, then 
      // TODO: If error then produce uop with error
      // TODO: Output error if tlb is error
      fetch_uop.pc := output_stage_pc

      // TODO: The icache read result 
      fetch_uop.instr := 0.U

      when(tlb_miss) {
        fetch_uop_valid := false.B
        output_stage_active := false.B
        ptw_refill_active := true.B
      } .elsewhen(cache_miss) { // Miss then go to refill
        fetch_uop_valid := false.B
        output_stage_active := false.B
        cache_refill_active := true.B
      } .elsewhen(fetch_uop_accept) { // Not miss and accepted, start a new fetch
        start_new_request := true.B
        output_stage_active := false.B
      } .elsewhen(!fetch_uop_accept) { // Not miss and not accepted, go to hold
        output_stage_active := false.B
        hold_fetch_uop_valid := true.B
        hold_fetch_uop := fetch_uop
      }
      busy := true.B
    } .elsewhen(ptw_refill_active) {
      // TODO: PTW refill
      // TODO: reset the bool
    } .elsewhen(cache_refill_active) {
      // TODO: icache refill
      // TODO: cache_refill_active := false.B
      busy := true.B
      // TODO: If fails, then produce uop with error?
    } .otherwise {
      start_new_request := true.B
    }

    when(start_new_request) {
      when(cmd === fetch_cmd.kill) {
        busy := false.B
        cmd_ready := true.B
      } .elsewhen (cmd === fetch_cmd.flush) {
        busy := false.B
        cmd_ready := true.B
      } .elsewhen(cmd === fetch_cmd.set_pc) {
        busy := false.B
        cmd_ready := true.B
        pc := new_pc
        // TODO: start a fetch request
        // output_stage_active := true.B
      } .elsewhen(cmd === fetch_cmd.none) {
        // TODO: start a fetch request
        // (pc + 4)
        // output_stage_active := true.B
      }
    }
    
    
}