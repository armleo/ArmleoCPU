package armleocpu

import chisel3._
import chisel3.util._
import Instructions._

class LoadStoreUnit(implicit ccx: CCXParams) extends ExecUnit {
  val i = in.uop.instr
  val rs1 = in.rs1

  when(i === LOAD)       {  out.alu_out := rs1.asSInt + i(31,20).asSInt;                handle("LOAD")}
  .elsewhen(i === STORE) {  out.alu_out := rs1.asSInt + Cat(i(31,25), i(11,7)).asSInt;  handle("STORE")}
}