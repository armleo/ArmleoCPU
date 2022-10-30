package armleocpu


import scala.math._
import chisel3._
import chisel3.util

object utils {
  def isPowerOfTwo[T : Numeric](d: T) {
    return util.log2Up(T) == util.log2Down(T)
  }
}
