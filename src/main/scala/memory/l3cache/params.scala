package armleocpu

import chisel3.util.log2Ceil

class L3CacheParams(
  val cacheEntriesLog2: Int = log2Ceil(2 * 1024),
  val cacheWaysLog2: Int = 2
) {

}
