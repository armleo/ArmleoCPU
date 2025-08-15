package armleocpu

import chisel3._
import chisel3.util._
import Instructions._

class JalrUnit(implicit ccx: CCXParams) extends ExecUnit {
  val i = in.uop.instr
  val rs1 = in.rs1

  when(i === JAL)       { out.alu_out := in.uop.pc.asSInt + Cat(i(31), i(19,12), i(20), i(30,21), 0.U(1.W)).asSInt; handle("JAL")}
  .elsewhen(i === JALR) { out.alu_out := rs1.asSInt + i(31,20).asSInt;                                              handle("JALR")}
}