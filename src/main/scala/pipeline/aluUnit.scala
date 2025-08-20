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

  when     (i === LUI)  { out.aluOut := Cat(i(31,12), 0.U(12.W)).asSInt;                   handle("LUI")   }
  .elsewhen(i === AUIPC){ out.aluOut := in.uop.pc.asSInt + Cat(i(31,12), 0.U(12.W)).asSInt;handle("AUIPC") }
  .elsewhen(i === ADD)  { out.aluOut := rs1.asSInt + rs2.asSInt;                           handle("ADD")   }
  .elsewhen(i === ADDW) { out.aluOut := (rs1(31,0).asSInt + rs2(31,0).asSInt);             handle("ADDW")  }
  .elsewhen(i === SUB)  { out.aluOut := rs1.asSInt - rs2.asSInt;                           handle("SUB")   }
  .elsewhen(i === SUBW) { out.aluOut := (rs1(31,0).asSInt - rs2(31,0).asSInt);             handle("SUBW")  }
  .elsewhen(i === AND)  { out.aluOut := (rs1.asSInt & rs2.asSInt);                         handle("AND")   }
  .elsewhen(i === OR)   { out.aluOut := (rs1.asSInt | rs2.asSInt);                         handle("OR")    }
  .elsewhen(i === XOR)  { out.aluOut := (rs1.asSInt ^ rs2.asSInt);                         handle("XOR")   }
  .elsewhen(i === SLL)  { out.aluOut := (rs1.asUInt << rs2S).asSInt;                       handle("SLL")   }
  .elsewhen(i === SRL)  { out.aluOut := (rs1.asUInt >> rs2S).asSInt;                       handle("SRL")   }
  .elsewhen(i === SRA)  { out.aluOut := (rs1.asSInt >> rs2S).asSInt;                       handle("SRA")   }
  .elsewhen(i === SLLW) { out.aluOut := (rs1(31,0).asUInt << rs2S(4,0)).asSInt;            handle("SLLW")  }
  .elsewhen(i === SRLW) { out.aluOut := (rs1(31,0).asUInt >> rs2S(4,0)).asSInt;            handle("SRLW")  }
  .elsewhen(i === SRAW) { out.aluOut := (rs1(31,0).asSInt >> rs2S(4,0)).asSInt;            handle("SRAW")  }
  .elsewhen(i === SLT)  { out.aluOut := (rs1.asSInt  <  rs2.asSInt).asSInt;                handle("SLT")   }
  .elsewhen(i === SLTU) { out.aluOut := (rs1.asUInt  <  rs2.asUInt).asSInt;                handle("SLTU")  }
  .elsewhen(i === ADDI) { out.aluOut := rs1.asSInt + s12;                                  handle("ADDI")  }
  .elsewhen(i === ADDIW){ out.aluOut := (rs1(31,0).asSInt + s12(31,0).asSInt);             handle("ADDIW") }
  .elsewhen(i === SLTI) { out.aluOut := (rs1.asSInt  <  s12).asSInt;                       handle("SLTI")  }
  .elsewhen(i === SLTIU){ out.aluOut := (rs1.asUInt  <  s12.asUInt).asSInt;                handle("SLTIU") }
  .elsewhen(i === ANDI) { out.aluOut := (rs1.asUInt  &  s12.asUInt).asSInt;                handle("ANDI")  }
  .elsewhen(i === ORI)  { out.aluOut := (rs1.asUInt  |  s12.asUInt).asSInt;                handle("ORI")   }
  .elsewhen(i === XORI) { out.aluOut := (rs1.asUInt  ^  s12.asUInt).asSInt;                handle("XORI")  }
  .elsewhen(i === SLLI) { out.aluOut := (rs1.asUInt  << sh).asSInt;                        handle("SLLI")  }
  .elsewhen(i === SRLI) { out.aluOut := (rs1.asUInt  >> sh).asSInt;                        handle("SRLI")  }
  .elsewhen(i === SRAI) { out.aluOut := (rs1.asSInt  >> sh).asSInt;                        handle("SRAI")  }
  .elsewhen(i === SLLIW){ out.aluOut := (rs1(31,0).asUInt << sh(4,0)).asSInt;              handle("SLLIW") }
  .elsewhen(i === SRLIW){ out.aluOut := (rs1(31,0).asUInt >> sh(4,0)).asSInt;              handle("SRLIW") }
  .elsewhen(i === SRAIW){ out.aluOut := (rs1(31,0).asSInt >> sh(4,0)).asSInt;              handle("SRAIW") }
}