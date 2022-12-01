package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._


object controlunit_state extends ChiselEnum {
  val reset   = 0.U(3.W)
  val idle    = 1.U(3.W)
  val new_pc  = 2.U(3.W)
  val flush   = 3.U(3.W)
}

object controlunit_cmd extends ChiselEnum {
  val none    = 0.U(3.W)
  val retire  = 1.U(3.W)
  val branch  = 2.U(3.W)
  val flush   = 3.U(3.W)
}

/**************************************************************************/
/*                                                                        */
/*                FIXME: Debug state                                      */
/*                                                                        */
/**************************************************************************/


class ControlUnit(val c: CoreParams) extends Module {
  val cmd        = IO(Input (chiselTypeOf(controlunit_cmd.none)))
  val pc_in      = IO(Input (UInt(c.archParams.avLen.W)))
  val pc_out     = IO(Output(UInt(c.archParams.avLen.W)))

  val kill               = IO(Output(Bool()))
  val wb_kill            = IO(Output(Bool())) // Kill writeback. Can only set right after a command
  val cu_to_fetch_cmd    = IO(Output(chiselTypeOf(fetch_cmd.none)))
  val wb_flush           = IO(Output(Bool()))

  val fetch_ready           = IO(Input  (Bool()))
  val decode_to_cu_ready    = IO(Input  (Bool()))
  val execute1_to_cu_ready  = IO(Input  (Bool()))
  val execute2_to_cu_ready  = IO(Input  (Bool()))
  val wb_ready              = IO(Input  (Bool()))

  
  val cu_pc           = RegInit(c.reset_vector.U(c.archParams.avLen.W))
  val cu_state        = RegInit(controlunit_state.reset)
  val wb_flush_reg    = RegInit(false.B)
  wb_flush := wb_flush_reg

  val allready = fetch_ready && decode_to_cu_ready && execute1_to_cu_ready && execute2_to_cu_ready && wb_ready

  cu_to_fetch_cmd := fetch_cmd.none
  kill := false.B
  pc_out := cu_pc
  wb_kill := false.B

  when(cu_state === controlunit_state.reset) {
    cu_to_fetch_cmd := fetch_cmd.flush
    kill := true.B
    cu_state := controlunit_state.reset
    wb_flush_reg := true.B
    when(wb_ready) {
      wb_flush_reg := false.B
    }
    when(allready) {
      cu_state := controlunit_state.idle
    }
    cu_pc := c.reset_vector.U
  } .elsewhen((cmd === controlunit_cmd.branch) || (cu_state === controlunit_state.new_pc)) {
    cu_to_fetch_cmd := fetch_cmd.kill
    kill := true.B
    cu_state := controlunit_state.new_pc
    cu_pc := pc_in
    when(cu_state === controlunit_state.new_pc) {
      when(allready) {
        cu_state := controlunit_state.idle
        cu_to_fetch_cmd := fetch_cmd.set_pc
      }
    }
  } .elsewhen((cmd === controlunit_cmd.flush) || (cu_state === controlunit_state.flush)) {
    cu_to_fetch_cmd := fetch_cmd.flush
    wb_flush_reg := true.B
    kill := true.B
    cu_state := controlunit_state.flush
    cu_pc := pc_in
    when(cu_state === controlunit_state.flush) {
      when(wb_ready) {
        wb_flush_reg := false.B
      }
      when(allready) {
        wb_flush_reg := false.B
        cu_state := controlunit_state.idle
        cu_to_fetch_cmd := fetch_cmd.set_pc
      }
    }
  }

  wb_kill := cu_state =/= controlunit_state.idle
}
