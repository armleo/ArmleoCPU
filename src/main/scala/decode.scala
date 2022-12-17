package armleocpu


import chisel3._
import chisel3.util._

import armleocpu.utils._

class Decode(c: CoreParams) extends Module {
  when((!decode_uop_valid) || (decode_uop_valid && decode_uop_accept)) {
    when(fetch.uop_valid && !cu.kill) {
      
      
      // IF REGISTER not reserved, then move the Uop downs stage
      // ELSE stall

      // Only send the uop down the stage if no conflict with any of rs1/rs2/rd
      // otherwise the pipeline will issue instructions with old register values
      
      // Also RD is checked so that the register is not overwritten

      

      val stall         = rs1_reserved || rs2_reserved || rd_reserved
      
      when (!stall) {
        regfile.decode.commit_i := true.B
        
        // FIXME: In the future do not combinationally assign
        decode_uop.viewAsSupertype(new fetch_uop_t(c))  := fetch.uop

        // STALL until reservation is reset
        decode_uop.rs1_data                             := regs(fetch.uop.instr(19, 15)) // FIXME: In the future do not combinationally assign
        decode_uop.rs2_data                             := regs(fetch.uop.instr(24, 20)) // FIXME: In the future do not combinationally assign
        
        fetch.uop_accept                                := true.B
        decode_uop_valid                                := true.B
        dlog("Instruction passed to next stage instr=0x%x, pc=0x%x", fetch.uop.instr, fetch.uop.pc)
      } .otherwise {
        dlog("Instruction stalled because of reservation instr=0x%x, pc=0x%x", fetch.uop.instr, fetch.uop.pc)
        decode_uop_valid := false.B
      }
    } .otherwise {
      dlog("Idle")
      decode_uop_valid := false.B
      when(cu.kill) {
        fetch.uop_accept := true.B
      }
    }
  } .elsewhen(cu.kill) {
    fetch.uop_accept := true.B
    decode_uop_valid := false.B
    dlog("Instr killed")
  } .otherwise {
    decode_uop_valid := false.B
  }
}