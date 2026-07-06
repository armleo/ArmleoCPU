package armleocpu
import chisel3.util.log2Ceil

object Consts {
  def cacheLineLog2: Int = 6 // Fixed 64 bytes
  def cacheLineBytes: Int = 1 << cacheLineLog2
  def busBytes:Int = cacheLineBytes


  val xLen: Int = 64
  val iLen: Int = 32
  val apLen: Int = 56
  val avLen: Int = 39
  val pagetableLevels: Int = 3

  val xLenLog2 = log2Ceil(xLen)
  val xLenBytes = xLen / 8
  val xLenBytesLog2 = log2Ceil(xLenBytes)


  val PTESIZE = 64 // bits. Only used by RVFI
}
