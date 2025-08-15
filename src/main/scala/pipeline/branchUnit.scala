package armleocpu

import chisel3._
import chisel3.util._
import Instructions._

class BranchUnit(implicit ccx: CCXParams) extends ExecUnit {
  val i  = in.uop.instr
  val rs1 = in.rs1; val rs2 = in.rs2

  out.alu_out := in.uop.pc.asSInt + Cat(i(31), i(7), i(30,25), i(11,8), 0.U(1.W)).asSInt

  when(i === BEQ)       { out.branch_taken := (rs1 === rs2);              handle("BEQ")  }
  .elsewhen(i === BNE)  { out.branch_taken := (rs1 =/= rs2);              handle("BNE")  }
  .elsewhen(i === BLT)  { out.branch_taken := (rs1.asSInt <  rs2.asSInt); handle("BLT")  }
  .elsewhen(i === BLTU) { out.branch_taken := (rs1.asUInt < rs2.asUInt);  handle("BLTU") }
  .elsewhen(i === BGE)  { out.branch_taken := (rs1.asSInt >= rs2.asSInt); handle("BGE")  }
  .elsewhen(i === BGEU) { out.branch_taken := (rs1.asUInt >= rs2.asUInt); handle("BGEU") }
}