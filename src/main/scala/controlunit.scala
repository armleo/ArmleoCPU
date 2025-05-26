package armleocpu

import chisel3._
import chisel3.util._

import chisel3.util._
import chisel3.experimental.dataview._


object controlunit_state extends ChiselEnum {
  val reset   = 0.U(3.W)
  val idle    = 1.U(3.W)
  val new_pc  = 2.U(3.W)
  val flush   = 3.U(3.W)
}

class controlunit_wb_io(val c: CoreParams) extends Bundle {
  val retire              = Input(Bool())
  val branch              = Input(Bool())
  val pc_in               = Input (UInt(c.apLen.W))

  val kill                = Output(Bool()) // Kill writeback. Can only set right after a command
  val flush               = Output(Bool())
  val ready               = Input (Bool())
}


/**************************************************************************/
/*                                                                        */
/*                FIXME: Debug state                                      */
/*                                                                        */
/**************************************************************************/

class ControlUnit(val c: CoreParams) extends Module {
  
  val wb_io      = IO(new controlunit_wb_io(c))
  
  val pc_out     = IO(Output(UInt(c.avLen.W)))

  val cu_to_fetch_cmd    = IO(Output(new fetchControlIO(c)))
  val kill               = IO(Output(Bool()))

  val fetch_ready           = IO(Input  (Bool()))
  val decode_to_cu_ready    = IO(Input  (Bool()))
  val execute_to_cu_ready   = IO(Input  (Bool()))
  

  
  val cu_pc           = RegInit(c.reset_vector.U(c.avLen.W)) // Instruction that need to be executed next
  val cu_state        = RegInit(controlunit_state.reset)
  val wb_flush_reg    = RegInit(false.B)
  wb_io.flush := wb_flush_reg

  val allready = fetch_ready && decode_to_cu_ready && execute_to_cu_ready && wb_io.ready

  cu_to_fetch_cmd := 0.U.asTypeOf(new fetchControlIO(c))
  cu_to_fetch_cmd.newPc := cu_pc

  kill := false.B
  pc_out := cu_pc
  wb_io.kill := false.B

  when(cu_state === controlunit_state.reset) {
    cu_to_fetch_cmd.flush := true.B
    kill := true.B
    cu_state := controlunit_state.reset
    wb_flush_reg := true.B
    when(wb_io.ready) {
      wb_flush_reg := false.B
    }
    when(allready) {
      cu_state := controlunit_state.idle
    }
    cu_pc := c.reset_vector.U
  } .elsewhen((wb_io.cmd === controlunit_cmd.branch) || (cu_state === controlunit_state.new_pc)) {
    cu_to_fetch_cmd := fetch_cmd.kill
    kill := true.B
    cu_state := controlunit_state.new_pc
    cu_pc := wb_io.pc_in
    when(cu_state === controlunit_state.new_pc) {
      when(allready) {
        cu_state := controlunit_state.idle
        cu_to_fetch_cmd := fetch_cmd.set_pc
      }
    }
  } .elsewhen((wb_io.cmd === controlunit_cmd.flush) || (cu_state === controlunit_state.flush)) {
    cu_to_fetch_cmd := fetch_cmd.flush
    wb_flush_reg := true.B
    kill := true.B
    cu_state := controlunit_state.flush
    cu_pc := wb_io.pc_in
    when(cu_state === controlunit_state.flush) {
      when(wb_io.ready) {
        wb_flush_reg := false.B
      }
      when(allready) {
        wb_flush_reg := false.B
        cu_state := controlunit_state.idle
        cu_to_fetch_cmd := fetch_cmd.set_pc
      }
    }
  }

  wb_io.kill := cu_state =/= controlunit_state.idle
}
