package armleocpu

import chisel3._
import chisel3.util._
import Instructions._

class AluUnit(implicit ccx: CCXParams) extends ExecUnit {
  val i    = in.uop.instr
  val rs1  = in.rs1
  val rs2  = in.rs2
  val s12  = in.simm12
  val sh   = in.shamt
  val rs2S = in.rs2Sh

  when     (i === LUI)  { out.alu_out := Cat(i(31,12), 0.U(12.W)).asSInt;                   handle("LUI")   }
  .elsewhen(i === AUIPC){ out.alu_out := in.uop.pc.asSInt + Cat(i(31,12), 0.U(12.W)).asSInt;handle("AUIPC") }
  .elsewhen(i === ADD)  { out.alu_out := rs1.asSInt + rs2.asSInt;                           handle("ADD")   }
  .elsewhen(i === ADDW) { out.alu_out := (rs1(31,0).asSInt + rs2(31,0).asSInt);             handle("ADDW")  }
  .elsewhen(i === SUB)  { out.alu_out := rs1.asSInt - rs2.asSInt;                           handle("SUB")   }
  .elsewhen(i === SUBW) { out.alu_out := (rs1(31,0).asSInt - rs2(31,0).asSInt);             handle("SUBW")  }
  .elsewhen(i === AND)  { out.alu_out := (rs1.asSInt & rs2.asSInt);                         handle("AND")   }
  .elsewhen(i === OR)   { out.alu_out := (rs1.asSInt | rs2.asSInt);                         handle("OR")    }
  .elsewhen(i === XOR)  { out.alu_out := (rs1.asSInt ^ rs2.asSInt);                         handle("XOR")   }
  .elsewhen(i === SLL)  { out.alu_out := (rs1.asUInt << rs2S).asSInt;                       handle("SLL")   }
  .elsewhen(i === SRL)  { out.alu_out := (rs1.asUInt >> rs2S).asSInt;                       handle("SRL")   }
  .elsewhen(i === SRA)  { out.alu_out := (rs1.asSInt >> rs2S).asSInt;                       handle("SRA")   }
  .elsewhen(i === SLLW) { out.alu_out := (rs1(31,0).asUInt << rs2S(4,0)).asSInt;            handle("SLLW")  }
  .elsewhen(i === SRLW) { out.alu_out := (rs1(31,0).asUInt >> rs2S(4,0)).asSInt;            handle("SRLW")  }
  .elsewhen(i === SRAW) { out.alu_out := (rs1(31,0).asSInt >> rs2S(4,0)).asSInt;            handle("SRAW")  }
  .elsewhen(i === SLT)  { out.alu_out := (rs1.asSInt  <  rs2.asSInt).asSInt;                handle("SLT")   }
  .elsewhen(i === SLTU) { out.alu_out := (rs1.asUInt  <  rs2.asUInt).asSInt;                handle("SLTU")  }
  .elsewhen(i === ADDI) { out.alu_out := rs1.asSInt + s12;                                  handle("ADDI")  }
  .elsewhen(i === ADDIW){ out.alu_out := (rs1(31,0).asSInt + s12(31,0).asSInt);             handle("ADDIW") }
  .elsewhen(i === SLTI) { out.alu_out := (rs1.asSInt  <  s12).asSInt;                       handle("SLTI")  }
  .elsewhen(i === SLTIU){ out.alu_out := (rs1.asUInt  <  s12.asUInt).asSInt;                handle("SLTIU") }
  .elsewhen(i === ANDI) { out.alu_out := (rs1.asUInt  &  s12.asUInt).asSInt;                handle("ANDI")  }
  .elsewhen(i === ORI)  { out.alu_out := (rs1.asUInt  |  s12.asUInt).asSInt;                handle("ORI")   }
  .elsewhen(i === XORI) { out.alu_out := (rs1.asUInt  ^  s12.asUInt).asSInt;                handle("XORI")  }
  .elsewhen(i === SLLI) { out.alu_out := (rs1.asUInt  << sh).asSInt;                        handle("SLLI")  }
  .elsewhen(i === SRLI) { out.alu_out := (rs1.asUInt  >> sh).asSInt;                        handle("SRLI")  }
  .elsewhen(i === SRAI) { out.alu_out := (rs1.asSInt  >> sh).asSInt;                        handle("SRAI")  }
  .elsewhen(i === SLLIW){ out.alu_out := (rs1(31,0).asUInt << sh(4,0)).asSInt;              handle("SLLIW") }
  .elsewhen(i === SRLIW){ out.alu_out := (rs1(31,0).asUInt >> sh(4,0)).asSInt;              handle("SRLIW") }
  .elsewhen(i === SRAIW){ out.alu_out := (rs1(31,0).asSInt >> sh(4,0)).asSInt;              handle("SRAIW") }
}