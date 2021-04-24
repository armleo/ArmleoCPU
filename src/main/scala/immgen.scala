package armleocpu

import chisel3._
import chisel3.util._



import Control._

class ImmGen extends Module {
  val io = IO(new Bundle{
    val inst = Input(UInt(32.W))
    val sel  = Input(UInt(3.W))
    val out  = Output(UInt(32.W))
  })
  
  val Iimm = io.inst(31, 20).asSInt
  val Simm = Cat(io.inst(31, 25), io.inst(11,7)).asSInt
  val Bimm = Cat(io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W)).asSInt
  val Uimm = Cat(io.inst(31, 12), 0.U(12.W)).asSInt
  val Jimm = Cat(io.inst(31), io.inst(19, 12), io.inst(20),
                io.inst(30, 25), io.inst(24, 21), 0.U(1.W)).asSInt
  val Zimm = io.inst(19, 15).zext

  io.out := MuxLookup(io.sel, Iimm & -2.S, 
    Seq(
      IMM_I -> Iimm,
      IMM_S -> Simm,
      IMM_B -> Bimm,
      IMM_U -> Uimm,
      IMM_J -> Jimm,
      IMM_Z -> Zimm)
    ).asUInt
}
