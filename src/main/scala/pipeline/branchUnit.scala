package armleocpu

import chisel3._
import chisel3.util._
import Instructions._

class ExecuteBranchUnit(implicit ccx: CCXParams) extends ExecUnit {
  val i  = in.uop.instr
  val rs1 = in.uop.rs1; val rs2 = in.uop.rs2

  out.aluOut := in.uop.pc.asSInt + Cat(i(31), i(7), i(30,25), i(11,8), 0.U(1.W)).asSInt
  out.handled := false.B
  out.branchTaken := false.B



       when(i === BEQ)  { out.branchTaken := (rs1 === rs2);              handle("BEQ")  }
  .elsewhen(i === BNE)  { out.branchTaken := (rs1 =/= rs2);              handle("BNE")  }
  .elsewhen(i === BLT)  { out.branchTaken := (rs1.asSInt <  rs2.asSInt); handle("BLT")  }
  .elsewhen(i === BLTU) { out.branchTaken := (rs1.asUInt < rs2.asUInt);  handle("BLTU") }
  .elsewhen(i === BGE)  { out.branchTaken := (rs1.asSInt >= rs2.asSInt); handle("BGE")  }
  .elsewhen(i === BGEU) { out.branchTaken := (rs1.asUInt >= rs2.asUInt); handle("BGEU") }
}