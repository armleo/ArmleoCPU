package armleocpu


import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

// DECODE
class DecodeUop(implicit ccx: CCXParams) extends FetchUop {
  val rs1        = UInt(ccx.xLen.W)
  val rs2        = UInt(ccx.xLen.W)
}


class Decode(implicit ccx: CCXParams) extends CCXModule {
  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val in             = IO(Flipped(DecoupledIO(new FetchUop))) 
  val out             = IO(DecoupledIO(new DecodeUop))
  val ctrl              = IO(new PipelineControlIO) // Pipeline command interface form control unit
  val regs_decode       = IO(Flipped(new regs_decode_io))

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/

  val decode_uop_bits_r         = Reg(new FetchUop)
  val decode_uop_valid_r        = Reg(Bool())
  
  /**************************************************************************/
  /*                                                                        */
  /*                COMB                                                    */
  /*                                                                        */
  /**************************************************************************/
  val kill              = ctrl.kill || ctrl.flush || ctrl.jump
  ctrl.busy             := out.valid

  out.bits.viewAsSupertype(new FetchUop)   := decode_uop_bits_r
  out.valid                                      := decode_uop_valid_r
  out.bits.rs1                              := regs_decode.rs1
  out.bits.rs2                              := regs_decode.rs2
  in.ready                                       := false.B
  regs_decode.instr_i                                   := in.bits.instr
  regs_decode.commit                                  := false.B
  



  when((!out.valid) || (out.valid && out.ready)) {
    when(in.valid && !kill) {
      // IF REGISTER not reserved, then move the Uop downs stage
      // ELSE stall

      // Only send the uop down the stage if no conflict with any of rs1/rs2/rd
      // otherwise the pipeline will issue instructions with old register values
      
      // Also RD is checked so that the register that needs to be written is overwritten
      
      val stall         = regs_decode.rs1_reserved || regs_decode.rs2_reserved || regs_decode.rd_reserved
      
      when (!stall) {
        regs_decode.commit := true.B
        
        // FIXME: In the future do not combinationally assign
        decode_uop_bits_r                                      := in.bits

        in.ready                                   := true.B
        decode_uop_valid_r                                := true.B
        log(cf"PASS instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x")
      } .otherwise {
        log(cf"STALL RESERVE instr=0x${in.bits.instr}%x, pc=0x${in.bits.pc}%x")
        decode_uop_valid_r := false.B
      }
    } .otherwise {
      //log(cf"IDLE")
      decode_uop_valid_r := false.B
      when(kill) {
        in.ready := true.B
      }
    }
  } .elsewhen(kill) {
    in.ready := true.B
    decode_uop_valid_r := false.B
    log(cf"KILL")
  } .otherwise {
    decode_uop_valid_r := false.B
  }
}