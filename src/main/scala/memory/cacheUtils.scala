package armleocpu


import chisel3._
import chisel3.util._
import Consts._

object CacheUtils {
  def getIdx(addr: UInt)(implicit cp: CacheParams): UInt = addr(cacheLineLog2 + cp.entriesLog2 - 1, cacheLineLog2)
  def getPtag(addr: UInt)(implicit cp: CacheParams): UInt = addr(apLen - 1, cacheLineLog2 + cp.entriesLog2)
}

