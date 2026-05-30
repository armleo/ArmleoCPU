package armleocpu.memory.l3cache

import chisel3._
import chisel3.util._
import armleocpu._
import addressUtils._

/**
 * Accept downstream response (addr + data) and emit a DataArrayReq (Valid)
 * to populate the data array. The external owner connects this Valid to
 * DataArray.io.req (which is Flipped(Valid(...)) ).
 */
class RefillWriter(implicit ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new DownstreamResp()(cbp)))
    val dataArrayReq = Output(Valid(new DataArrayReq()(ccx, cbp)))
    val victimCommand = Output(new VictimSelectionCommand)
    val victimStatus  = Input(new VictimSelectionStatus)
  })

  // Default
  io.in.ready := false.B
  io.dataArrayReq.valid := false.B
  io.dataArrayReq.bits := 0.U.asTypeOf(io.dataArrayReq.bits)

  // Default victim command
  io.victimCommand.increment := false.B
  io.victimCommand.clear := false.B

  private val ways = 1 << ccx.l3.cacheWaysLog2
  val victimMask = UIntToOH(io.victimStatus.victimWay, ways)

  // Accept input whenever available (DataArray has no back-pressure on Valid)
  when(io.in.valid) {
    io.in.ready := true.B

    val entry = Wire(new Entry(cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2))
    entry.tag := getCacheTag(io.in.bits.addr)
    entry.valid := true.B
    entry.dirty := false.B
    entry.unique := false.B
    entry.sharer := 0.U
    entry.data := io.in.bits.data

    val darr = Wire(new DataArrayReq()(ccx, cbp))
    darr.addr := io.in.bits.addr
    darr.write := true.B
    darr.wayMask := victimMask
    darr.wdata := entry

    when(io.in.fire()) {
      io.victimCommand.increment := true.B
    }

    io.dataArrayReq.valid := true.B
    io.dataArrayReq.bits := darr
  }
}
