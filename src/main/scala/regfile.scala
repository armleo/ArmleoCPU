package armleocpu


import chisel3._
import chisel3.util._

import Consts._

class regs_retire_io extends Bundle {
  val commit    = Input (Bool())

  val rd_write    = Input (Bool())
  val rd_addr     = Input (UInt(5.W))
  val rd_wdata    = Input (UInt(xLen.W))
}

class regs_decode_io(implicit val ccx: CCXParams) extends Bundle {
  val instr_i   = Input (UInt(iLen.W))
  val commit    = Input (Bool())

  val rs1       = new RS()
  val rs2       = new RS()
  val rd        = new ReservedStatus()
}

class ReservedStatus extends Bundle {
  val reserved = Output(Bool())
}

class RS extends ReservedStatus{
  val value = Output(UInt(xLen.W))
}

class Regfile(implicit ccx: CCXParams) extends CCXModule {
  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/
  val ctrl    = IO(new PipelineControlIO) // Pipeline command interface form control unit
  val decode  = IO(new regs_decode_io)
  val retire  = IO(new regs_retire_io)

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/

  val regs_reservation  = RegInit(VecInit.tabulate(32) {f:Int => false.B})
  // 2 read ports, 1 write port
  // Use two read-write ports (no Vec element). One RW port will be used for writes when retiring.
  val regs_mem          = SRAM(32, UInt(xLen.W), 0, 0, 2)
  val hold            = RegInit(false.B)

  val holdRs1         = Reg(UInt(xLen.W))
  val holdRs2         = Reg(UInt(xLen.W))

  // Drive read addresses for rs1/rs2 using read ports
  // Drive read addresses for rs1/rs2 using RW ports (read path)
  regs_mem.readPorts(0).address := decode.instr_i(19, 15)
  regs_mem.readPorts(0).enable := true.B

  regs_mem.readPorts(1).address := decode.instr_i(24, 20)
  regs_mem.readPorts(1).enable := true.B

  /**************************************************************************/
  /*                                                                        */
  /*                Regs reservations                                       */
  /*                                                                        */
  /**************************************************************************/
  
  decode.rs1.reserved  := (decode.instr_i(19, 15) =/= 0.U) && regs_reservation(decode.instr_i(19, 15))
  decode.rs2.reserved  := (decode.instr_i(24, 20) =/= 0.U) && regs_reservation(decode.instr_i(24, 20))
  decode.rd.reserved   := (decode.instr_i(11,  7) =/= 0.U) && regs_reservation(decode.instr_i(11,  7))

  when(decode.commit) {
    when(decode.instr_i(11, 7) =/= 0.U) {
      regs_reservation(decode.instr_i(11, 7)) := true.B
    }
  }

  when(ctrl.kill || ctrl.flush || ctrl.jump) {
    regs_reservation := 0.U.asTypeOf(chiselTypeOf(regs_reservation))
  } .elsewhen(retire.commit) {
    // In the future do not unconditionally unreserve it. Need proper RD logic and instruction decode map
    regs_reservation(retire.rd_addr) := false.B
  }

  /**************************************************************************/
  /*                                                                        */
  /*                Regs reading                                            */
  /*                                                                        */
  /**************************************************************************/
  when(!hold) {
    hold := true.B
    decode.rs1.value := regs_mem.readPorts(0).data
    decode.rs2.value := regs_mem.readPorts(1).data
    holdRs1 := regs_mem.readPorts(0).data
    holdRs2 := regs_mem.readPorts(1).data
  } .otherwise {
    decode.rs1.value := holdRs1
    decode.rs2.value := holdRs2
  }
  
  when(decode.commit) {
    hold := false.B
  }
  
  ctrl.busy := false.B
}