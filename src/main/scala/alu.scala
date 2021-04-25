package armleocpu

import chisel3._
import chisel3.util._

object ALU
{
  val SZ_ALU_FN = 4
  def FN_X    = BitPat("b????")
  def FN_ADD  = 0.U(SZ_ALU_FN.W)
  def FN_SL   = 1.U(SZ_ALU_FN.W)
  def FN_SNE  = 3.U(SZ_ALU_FN.W)
  def FN_XOR  = 4.U(SZ_ALU_FN.W)
  def FN_SR   = 5.U(SZ_ALU_FN.W)
  def FN_OR   = 6.U(SZ_ALU_FN.W)
  def FN_AND  = 7.U(SZ_ALU_FN.W)
  def FN_SUB  = 10.U(SZ_ALU_FN.W)
  def FN_SRA  = 11.U(SZ_ALU_FN.W)
  def FN_SLT  = 12.U(SZ_ALU_FN.W)
  def FN_SGE  = 13.U(SZ_ALU_FN.W)
  def FN_SLTU = 14.U(SZ_ALU_FN.W)
  def FN_SGEU = 15.U(SZ_ALU_FN.W)

  // Always convert to bool otherwise you will get compilation error
  def isSub(cmd: UInt) = cmd(3).asBool
  def isCmp(cmd: UInt) = (cmd >= FN_SLT).asBool
  def cmpUnsigned(cmd: UInt) = (cmd(1)).asBool
  def cmpInverted(cmd: UInt) = (cmd(0)).asBool
  def cmpEq(cmd: UInt) = (!cmd(3)).asBool
}

import ALU._
import Consts._

class ALU extends Module {

  val io = IO(new Bundle {
    val dw = Input(UInt(SZ_DW.W))
    val fn = Input(UInt(SZ_ALU_FN.W))
    val in1 = Input(UInt(xLen.W))
    val in2 = Input(UInt(xLen.W))
    val out = Output(UInt(xLen.W))

    val adder_out = Output(UInt(xLen.W))
    val cmp_out = Output(Bool())
  })
  
  // ADD, SUB
  val issub = isSub(io.fn)

  val in2_inv = Mux(isSub(io.fn), ~io.in2, io.in2)
  val in1_xor_in2 = io.in1 ^ in2_inv
  io.adder_out := io.in1 + in2_inv + isSub(io.fn)

  // SLT, SLTU
  val slt =
    Mux(io.in1(xLen-1) === io.in2(xLen-1), io.adder_out(xLen-1),
    Mux(cmpUnsigned(io.fn), io.in2(xLen-1), io.in1(xLen-1)))
  // comparison result, used in branch calculation
  io.cmp_out := cmpInverted(io.fn) ^ Mux(cmpEq(io.fn), in1_xor_in2 === 0.U, slt)

  // SLL, SRL, SRA
  val (shamt, shifter_in_r) = {
      val shifter_in_hi_32 = Fill(32, isSub(io.fn) && io.in1(31))
      val shifter_in_hi = Mux(io.dw === DW_64, io.in1(63,32), shifter_in_hi_32)
      val shamt = Cat(io.in2(5) & (io.dw === DW_64), io.in2(4,0))
      (shamt, Cat(shifter_in_hi, io.in1(31,0)))
  }
  val shifter_in = Mux(io.fn === FN_SR  || io.fn === FN_SRA, shifter_in_r, Reverse(shifter_in_r))
  val shifter_out_r = (Cat(isSub(io.fn) & shifter_in(xLen-1), shifter_in).asSInt >> shamt)(xLen-1,0)
  val shifter_out_l = Reverse(shifter_out_r)
  val shifter_out = Mux(io.fn === FN_SR || io.fn === FN_SRA, shifter_out_r, 0.U) |
              Mux(io.fn === FN_SL,                     shifter_out_l, 0.U)

  // AND, OR, XOR
  val logic = Mux(io.fn === FN_XOR || io.fn === FN_OR, in1_xor_in2, 0.U) |
              Mux(io.fn === FN_OR || io.fn === FN_AND, io.in1 & io.in2, 0.U)
  val shift_logic_combined = (isCmp(io.fn) && slt) | logic | shifter_out
  val out = Mux(io.fn === FN_ADD || io.fn === FN_SUB, io.adder_out, shift_logic_combined)

  io.out := out
  when (io.dw === DW_32) { io.out := Cat(Fill(32, out(31)), out(31,0)) }
}