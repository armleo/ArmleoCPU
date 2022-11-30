package armleocpu


import scala.math._
import chisel3._
import chisel3.util

object utils {
  def isPowerOfTwo(d: BigInt):Boolean = {
    return util.log2Up(d) == util.log2Down(d)
  }

  def isPositivePowerOfTwo(d: BigInt):Boolean = {
    return isPowerOfTwo(d) && (d >= 1)
  }
}
