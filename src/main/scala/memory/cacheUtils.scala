package armleocpu


import chisel3._
import chisel3.util._


object CacheUtils {
  def getIdx(addr: UInt)(implicit ccx: CCXParams, cp: CacheParams): UInt = addr(ccx.cacheLineLog2 + cp.entriesLog2 - 1, ccx.cacheLineLog2)
  def getPtag(addr: UInt)(implicit ccx: CCXParams, cp: CacheParams): UInt = addr(ccx.apLen - 1, ccx.cacheLineLog2 + cp.entriesLog2)
}

