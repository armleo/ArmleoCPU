package armleocpu

import chisel3._
import chisel3.util._

object Consts {
  def X = BitPat("b?")
  def N = BitPat("b0")
  def Y = BitPat("b1")

  def xLen = 64
  def iLen = 32
  
  val SZ_DW = 1
  def DW_X  = X
  def DW_32 = false.B
  def DW_64 = true.B
  def DW_XPR = DW_64
}