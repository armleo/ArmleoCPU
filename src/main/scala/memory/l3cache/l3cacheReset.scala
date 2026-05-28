package armleocpu

import chisel3._
import chisel3.util._

class L3CacheResetIO(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val start = Input(Bool())
  val active = Output(Bool())
  val done = Output(Bool())

  val dataArray = Valid(new L3CacheDataArrayReq)
  val victim = Output(new L3CacheVictimCommand)
}

class L3CacheReset(implicit ccx: CCXParams, cbp: CoherentBusParams) extends Module {
  val io = IO(new L3CacheResetIO)

  private val entries = 1 << ccx.l3.cacheEntriesLog2
  private val ways = 1 << ccx.l3.cacheWaysLog2
  private val counterWidth = ccx.l3.cacheEntriesLog2

  require(entries > 0)

  val running = RegInit(false.B)
  val counter = RegInit(0.U(counterWidth.W))
  val lastEntry = counter === (entries - 1).U

  when(io.start && !running) {
    running := true.B
    counter := 0.U
  } .elsewhen(running) {
    counter := counter + 1.U
    when(lastEntry) {
      running := false.B
    }
  }

  val invalidEntry = Wire(new L3CacheEntry(cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2))
  invalidEntry := 0.U.asTypeOf(invalidEntry)
  invalidEntry.valid := false.B

  val resetAddr = Wire(UInt(cbp.addrWidth.W))
  resetAddr := counter << ccx.cacheLineLog2

  io.active := running
  io.done := running && lastEntry

  io.dataArray.valid := running
  io.dataArray.bits.addr := resetAddr
  io.dataArray.bits.write := true.B
  io.dataArray.bits.wayMask := Fill(ways, 1.U(1.W))
  io.dataArray.bits.wdata := invalidEntry

  io.victim.increment := false.B
  io.victim.clear := io.start
}
