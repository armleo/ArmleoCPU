package armleocpu.memory.l3cache

import chisel3._
import chisel3.util._
import armleocpu.utils.threeStateStageIO
import armleocpu._

class ReseterIO(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends threeStateStageIO {
  val dataArrayReq = Valid(new DataArrayReq)
  val victimSelectionCommand = Output(new VictimSelectionCommand)
}

class Reseter(implicit ccx: CCXParams, cbp: CoherentBusParams) extends Module {
  val io = IO(new ReseterIO)

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

  val invalidEntry = Wire(new Entry(cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2))
  invalidEntry := 0.U.asTypeOf(invalidEntry)
  invalidEntry.valid := false.B

  val resetAddr = Wire(UInt(cbp.addrWidth.W))
  resetAddr := counter << ccx.cacheLineLog2

  io.active := running
  io.done := running && lastEntry

  io.dataArrayReq.valid := running
  io.dataArrayReq.bits.addr := resetAddr
  io.dataArrayReq.bits.write := true.B
  io.dataArrayReq.bits.wayMask := Fill(ways, 1.U(1.W))
  io.dataArrayReq.bits.wdata := invalidEntry

  io.victimSelectionCommand.increment := false.B
  io.victimSelectionCommand.clear := io.start
}
