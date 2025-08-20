package armleocpu

import chisel3._
import chisel3.util._
import Instructions._

class ExecuteLoadStoreUnit(implicit ccx: CCXParams) extends ExecUnit {
  val i = in.uop.instr
  val rs1 = in.uop.rs1

  out.handled := false.B
  out.branchTaken := false.B
  out.aluOut := 0.S

  when(i === LOAD)       {  out.aluOut := rs1.asSInt + i(31,20).asSInt;                handle("LOAD")}
  .elsewhen(i === STORE) {  out.aluOut := rs1.asSInt + Cat(i(31,25), i(11,7)).asSInt;  handle("STORE")}
}