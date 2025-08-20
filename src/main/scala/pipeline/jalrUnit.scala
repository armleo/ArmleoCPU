package armleocpu

import chisel3._
import chisel3.util._
import Instructions._

class ExecuteJalrUnit(implicit ccx: CCXParams) extends ExecUnit {
  val i = in.uop.instr
  val rs1 = in.uop.rs1

  out.handled := false.B
  out.branchTaken := false.B
  out.aluOut := 0.S

  when(i === JAL)       { out.aluOut := in.uop.pc.asSInt + Cat(i(31), i(19,12), i(20), i(30,21), 0.U(1.W)).asSInt; handle("JAL")}
  .elsewhen(i === JALR) { out.aluOut := rs1.asSInt + i(31,20).asSInt;                                              handle("JALR")}
}