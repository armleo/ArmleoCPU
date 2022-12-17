package armleocpu


import chisel3._
import chisel3.util._

import armleocpu.utils._


// DECODE
class decode_uop_t(c: CoreParams) extends fetch_uop_t(c) {
  val rs1_data        = UInt(c.xLen.W)
  val rs2_data        = UInt(c.xLen.W)
}


class Decode(c: CoreParams) extends Module {
  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val decode_uop          = IO(Output(new decode_uop_t(c)))
  
  val decode_uop_valid    = IO(Output(Bool()))
  val decode_uop_accept   = IO(Input (Bool()))

  val kill                = IO(Input (Bool()))

  val fetch_uop_valid     = IO(Input (Bool()))
  val fetch_uop_accept    = IO(Output(Bool()))
  val fetch_uop           = IO(Input (new fetch_uop_t(c)))

  val regs_decode         = IO(Flipped(new regs_decode_io(c)))

  val dlog = new Logger(c.lp.coreName, f"decod", c.core_verbose)


  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/

  val decode_uop_r        = Reg(new decode_uop_t(c))
  val decode_uop_valid_r  = Reg(Bool())
  decode_uop              := decode_uop_r
  decode_uop_valid        := decode_uop_valid_r

  regs_decode.instr_i := fetch_uop.instr
  
  
  when((!decode_uop_valid) || (decode_uop_valid && decode_uop_accept)) {
    when(fetch_uop_valid && !kill) {
      
      
      // IF REGISTER not reserved, then move the Uop downs stage
      // ELSE stall

      // Only send the uop down the stage if no conflict with any of rs1/rs2/rd
      // otherwise the pipeline will issue instructions with old register values
      
      // Also RD is checked so that the register is not overwritten

      
      
      val stall         = regs_decode.rs1_reserved || regs_decode.rs2_reserved || regs_decode.rd_reserved
      
      when (!stall) {
        regs_decode.commit_i := true.B
        
        // FIXME: In the future do not combinationally assign
        decode_uop_r.viewAsSupertype(new fetch_uop_t(c))  := fetch_uop

        // STALL until reservation is reset
        decode_uop.rs1_data                             := regs_decode.rs1_rdataregs(fetch_uop.instr(19, 15)) // FIXME: In the future do not combinationally assign
        decode_uop.rs2_data                             := regs(fetch_uop.instr(24, 20)) // FIXME: In the future do not combinationally assign
        
        fetch_uop_accept                                := true.B
        decode_uop_valid_r                              := true.B
        dlog("Instruction passed to next stage instr=0x%x, pc=0x%x", fetch_uop.instr, fetch_uop.pc)
      } .otherwise {
        dlog("Instruction stalled because of reservation instr=0x%x, pc=0x%x", fetch_uop.instr, fetch_uop.pc)
        decode_uop_valid_r := false.B
      }
    } .otherwise {
      dlog("Idle")
      decode_uop_valid_r := false.B
      when(kill) {
        fetch_uop_accept := true.B
      }
    }
  } .elsewhen(kill) {
    fetch_uop_accept := true.B
    decode_uop_valid_r := false.B
    dlog("Instr killed")
  } .otherwise {
    decode_uop_valid_r := false.B
  }
}