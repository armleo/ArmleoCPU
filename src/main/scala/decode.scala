package armleocpu


import chisel3._
import chisel3.util._



import chisel3.util._
import chisel3.experimental.dataview._

// DECODE
class decode_uop_t(ccx: CCXParameters) extends fetch_uop_t(c) {
  val rs1_data        = UInt(c.xLen.W)
  val rs2_data        = UInt(c.xLen.W)
}


class Decode(ccx: CCXParameters) extends Module {
  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val uop_i         = IO(Flipped(DecoupledIO(new fetch_uop_t(c)))) 
  val uop_o          = IO(DecoupledIO(new decode_uop_t(c)))
  
  val kill                = IO(Input (Bool()))
  val busy              = IO(Output(Bool()))

  busy := uop_o.valid

  val regs_decode         = IO(Flipped(new regs_decode_io(c)))

  val dlog = new Logger(c.lp.coreName, f"DECODER ", c.core_verbose)

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/

  val decode_uop_bits_r        = Reg(new fetch_uop_t(c))
  val decode_uop_valid_r  = Reg(Bool())
  
  /**************************************************************************/
  /*                                                                        */
  /*                COMB                                                    */
  /*                                                                        */
  /**************************************************************************/

  uop_o.bits.viewAsSupertype(new fetch_uop_t(c))   := decode_uop_bits_r
  uop_o.valid                                      := decode_uop_valid_r
  uop_o.bits.rs1_data                              := regs_decode.rs1_data
  uop_o.bits.rs2_data                              := regs_decode.rs2_data
  uop_i.ready                                       := false.B
  regs_decode.instr_i                                   := uop_i.bits.instr
  regs_decode.commit_i                                  := false.B
  



  when((!uop_o.valid) || (uop_o.valid && uop_o.ready)) {
    when(uop_i.valid && !kill) {
      // IF REGISTER not reserved, then move the Uop downs stage
      // ELSE stall

      // Only send the uop down the stage if no conflict with any of rs1/rs2/rd
      // otherwise the pipeline will issue instructions with old register values
      
      // Also RD is checked so that the register that needs to be written is overwritten
      
      val stall         = regs_decode.rs1_reserved || regs_decode.rs2_reserved || regs_decode.rd_reserved
      
      when (!stall) {
        regs_decode.commit_i := true.B
        
        // FIXME: In the future do not combinationally assign
        decode_uop_bits_r                                      := uop_i.bits

        uop_i.ready                                   := true.B
        decode_uop_valid_r                                := true.B
        dlog("PASS instr=0x%x, pc=0x%x", uop_i.bits.instr, uop_i.bits.pc)
      } .otherwise {
        dlog("STALL RESERVE instr=0x%x, pc=0x%x", uop_i.bits.instr, uop_i.bits.pc)
        decode_uop_valid_r := false.B
      }
    } .otherwise {
      //dlog("IDLE")
      decode_uop_valid_r := false.B
      when(kill) {
        uop_i.ready := true.B
      }
    }
  } .elsewhen(kill) {
    uop_i.ready := true.B
    decode_uop_valid_r := false.B
    dlog("KILL")
  } .otherwise {
    decode_uop_valid_r := false.B
  }
}