package armleocpu

import chisel3.util.ChiselEnum

object L3CacheBankState extends ChiselEnum {
  val init, idle, rResponseAnalysis,
      wChooseVictim, wRefillAfterEviction, wWaitB, // The writeback branch
      rSnoop, rSnoopReturn, rPushReq, rStorageUpdate, rWaitR // The read branch (can be interrupted to service writeback)
      = Value
}
