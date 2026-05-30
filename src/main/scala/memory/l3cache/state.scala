package armleocpu.memory.l3cache

import chisel3.ChiselEnum


object BankState extends ChiselEnum {
  val init, idle, rResponseAnalysis,
      evict, // Evictition path,
      // either from encountering all-ways-dirty condition, or voluntarily. Depends on the returnState
      wChooseVictim, wWaitB, wRefillAfterEviction,  // The writeback branch
      rSnoop, rSnoopReturn, rRefillStart, rStorageUpdate, rWaitR // The read branch (can be interrupted to service writeback)
      = Value
}
