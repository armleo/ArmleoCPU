package armleocpu


import chisel3._
import chisel3.util._



import chisel3.util._
import chisel3.experimental.dataview._

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

  val fetch_uop         = IO(Flipped(DecoupledIO(new fetch_uop_t(c)))) 
  val decode_uop          = IO(DecoupledIO(new decode_uop_t(c)))
  
  val kill                = IO(Input (Bool()))

  val regs_decode         = IO(Flipped(new regs_decode_io(c)))

  val dlog = new Logger(c.lp.coreName, f"decoder ", c.core_verbose)

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/

  val decode_uop_r        = Reg(new fetch_uop_t(c))
  val decode_uop_valid_r  = Reg(Bool())
  
  /**************************************************************************/
  /*                                                                        */
  /*                COMB                                                    */
  /*                                                                        */
  /**************************************************************************/

  decode_uop.bits.viewAsSupertype(new fetch_uop_t(c))   := decode_uop_r
  decode_uop.valid                                      := decode_uop_valid_r
  decode_uop.bits.rs1_data                              := regs_decode.rs1_data
  decode_uop.bits.rs2_data                              := regs_decode.rs2_data
  fetch_uop.ready                                       := false.B
  regs_decode.instr_i                                   := fetch_uop.bits.instr
  regs_decode.commit_i                                  := false.B
  

  when((!decode_uop.valid) || (decode_uop.valid && decode_uop.ready)) {
    when(fetch_uop.valid && !kill) {
      // IF REGISTER not reserved, then move the Uop downs stage
      // ELSE stall

      // Only send the uop down the stage if no conflict with any of rs1/rs2/rd
      // otherwise the pipeline will issue instructions with old register values
      
      // Also RD is checked so that the register that needs to be written is overwritten
      
      val stall         = regs_decode.rs1_reserved || regs_decode.rs2_reserved || regs_decode.rd_reserved
      
      when (!stall) {
        regs_decode.commit_i := true.B
        
        // FIXME: In the future do not combinationally assign
        decode_uop_r                                      := fetch_uop

        fetch_uop.ready                                   := true.B
        decode_uop_valid_r                                := true.B
        dlog("Instruction passed to next stage instr=0x%x, pc=0x%x", fetch_uop.bits.instr, fetch_uop.bits.pc)
      } .otherwise {
        dlog("Instruction stalled because of reservation instr=0x%x, pc=0x%x", fetch_uop.bits.instr, fetch_uop.bits.pc)
        decode_uop_valid_r := false.B
      }
    } .otherwise {
      dlog("Idle")
      decode_uop_valid_r := false.B
      when(kill) {
        fetch_uop.ready := true.B
      }
    }
  } .elsewhen(kill) {
    fetch_uop.ready := true.B
    decode_uop_valid_r := false.B
    dlog("Instr killed")
  } .otherwise {
    decode_uop_valid_r := false.B
  }
}