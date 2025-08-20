package armleocpu


import chisel3._
import chisel3.util._



class regs_retire_io(implicit val ccx: CCXParams) extends Bundle {
  val commit    = Input (Bool())

  val rd_write    = Input (Bool())
  val rd_addr     = Input (UInt(5.W))
  val rd_wdata    = Input (UInt(ccx.xLen.W))
}

class regs_decode_io(implicit val ccx: CCXParams) extends Bundle {
  val instr_i       = Input (UInt(ccx.iLen.W))
  val commit      = Input (Bool())

  val rs1      = Output(UInt(ccx.xLen.W))
  val rs2      = Output(UInt(ccx.xLen.W))

  val rs1_reserved  = Output(Bool())
  val rs2_reserved  = Output(Bool())
  val rd_reserved   = Output(Bool())
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
  val regs              = SyncReadMem(32, UInt(ccx.xLen.W))
  val hold  = RegInit(false.B)

  val holdRs1         = Reg(UInt(ccx.xLen.W))
  val holdRs2         = Reg(UInt(ccx.xLen.W))

  val rs1_rdwr          = regs(decode.instr_i(19, 15))
  val rs2_rdwr          = regs(decode.instr_i(24, 20))

  /**************************************************************************/
  /*                                                                        */
  /*                Regs reservations                                       */
  /*                                                                        */
  /**************************************************************************/
  
  decode.rs1_reserved  := (decode.instr_i(19, 15) =/= 0.U) && regs_reservation(decode.instr_i(19, 15))
  decode.rs2_reserved  := (decode.instr_i(24, 20) =/= 0.U) && regs_reservation(decode.instr_i(24, 20))
  decode.rd_reserved   := (decode.instr_i(11,  7) =/= 0.U) && regs_reservation(decode.instr_i(11,  7))

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
  when(hold) {
    hold := false.B
    decode.rs1 := rs1_rdwr
    decode.rs2 := rs2_rdwr
  } .otherwise {
    decode.rs1 := holdRs1
    decode.rs2 := holdRs2
  }
  
  when(decode.commit) {
    hold := true.B
  }

  /**************************************************************************/
  /*                                                                        */
  /*                Regs writing                                            */
  /*                                                                        */
  /**************************************************************************/
  

  when(retire.rd_write) {
    regs(retire.rd_addr) := retire.rd_wdata
  }

  ctrl.busy := false.B
}