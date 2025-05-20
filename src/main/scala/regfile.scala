package armleocpu


import chisel3._
import chisel3.util._



class regs_memwb_io(c: CoreParams) extends Bundle {
  val commit_i    = Input (Bool())
  val clear_i     = Input (Bool())

  val rd_write    = Input (Bool())
  val rd_addr     = Input (UInt(5.W))
  val rd_wdata    = Input (UInt(c.xLen.W))
}

class regs_decode_io(c: CoreParams) extends Bundle {
  val instr_i       = Input (UInt(c.iLen.W))
  val commit_i      = Input (Bool())

  val rs1_data      = Output(UInt(c.xLen.W))
  val rs2_data      = Output(UInt(c.xLen.W))

  val rs1_reserved  = Output(Bool())
  val rs2_reserved  = Output(Bool())
  val rd_reserved   = Output(Bool())
}

class Regfile(c: CoreParams) extends Module {
  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val decode  = IO(new regs_decode_io(c))
  val memwb   = IO(new regs_memwb_io(c))

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/

  val regs_reservation  = RegInit(VecInit.tabulate(32) {f:Int => false.B})
  val regs              = SyncReadMem(32, UInt(c.xLen.W))
  val use_read_rs_data  = RegInit(false.B)

  val saved_rs1         = Reg(UInt(c.xLen.W))
  val saved_rs2         = Reg(UInt(c.xLen.W))

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

  when(decode.commit_i) {
    when(decode.instr_i(11, 7) =/= 0.U) {
      regs_reservation(decode.instr_i(11, 7)) := true.B
    }
  }

  when(memwb.commit_i) {
    when(memwb.clear_i) {
      regs_reservation := 0.U.asTypeOf(chiselTypeOf(regs_reservation))
    } .otherwise {
      // In the future do not unconditionally unreserve it. Need proper RD logic and instruction decode map
      regs_reservation(memwb.rd_addr) := false.B
    }
  }

  /**************************************************************************/
  /*                                                                        */
  /*                Regs reading                                            */
  /*                                                                        */
  /**************************************************************************/
  when(use_read_rs_data) {
    use_read_rs_data := false.B
    decode.rs1_data := rs1_rdwr
    decode.rs2_data := rs2_rdwr
  } .otherwise {
    decode.rs1_data := saved_rs1
    decode.rs2_data := saved_rs2
  }
  
  when(decode.commit_i) {
    use_read_rs_data := true.B
  }

  /**************************************************************************/
  /*                                                                        */
  /*                Regs writing                                            */
  /*                                                                        */
  /**************************************************************************/
  

  when(memwb.rd_write) {
    regs(memwb.rd_addr) := memwb.rd_wdata
  }
}