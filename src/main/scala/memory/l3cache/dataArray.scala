package armleocpu.memory.l3cache

import chisel3._
import chisel3.util._
import armleocpu._

class DataArrayReq(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val addr = UInt(cbp.addrWidth.W)
  val write = Bool()
  val wayMask = UInt((1 << ccx.l3.cacheWaysLog2).W)
  val wdata = new Entry(cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2)
}

class DataArrayResp(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val rdata = Vec(
    1 << ccx.l3.cacheWaysLog2,
    new Entry(cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2)
  )
  val hit = Bool()
  val hitIdx = UInt(ccx.l3.cacheWaysLog2.W)
  val unique = Bool()
  val sharer = UInt(ccx.coreCount.W)
  val dirty = Bool()
}

class DataArrayIO(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Bundle {
  val req = Flipped(Valid(new DataArrayReq))
  val resp = Valid(new DataArrayResp)
}

class DataArray(implicit ccx: CCXParams, cbp: CoherentBusParams) extends Module {
  val io = IO(new DataArrayIO)

  private val entries = 1 << ccx.l3.cacheEntriesLog2
  private val ways = 1 << ccx.l3.cacheWaysLog2
  private val tagWidth = cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2

  val data = SRAM.masked(
    entries,
    Vec(ways, new Entry(tagWidth)),
    0,
    0,
    1
  )

  val port = data.readwritePorts(0)

  // Stage 0: accept the array request and issue the SRAM access.
  val s0_valid = io.req.valid
  val s0_addr = io.req.bits.addr

  // Stage 1: SRAM read data is available, decode it using the registered address.
  val s1_valid = RegNext(s0_valid, false.B)
  val s1_addr = RegEnable(s0_addr, s0_valid)

  val s1_cacheHits = port.readData.map(entry => entry.valid && entry.tag === addressUtils.getCacheTag(s1_addr))
  val s1_cacheHit = VecInit(s1_cacheHits).asUInt.orR
  val s1_cacheHitIdx = PriorityEncoder(s1_cacheHits)
  val s1_hitEntry = port.readData(s1_cacheHitIdx)

  io.resp.valid := s1_valid
  io.resp.bits.rdata := port.readData
  io.resp.bits.hit := s1_cacheHit
  io.resp.bits.hitIdx := s1_cacheHitIdx
  io.resp.bits.unique := s1_cacheHit && s1_hitEntry.unique
  io.resp.bits.sharer := Mux(s1_cacheHit, s1_hitEntry.sharer, 0.U(ccx.coreCount.W))
  io.resp.bits.dirty := s1_cacheHit && s1_hitEntry.dirty

  port.address := addressUtils.getCacheEntryIdx(s0_addr)
  port.enable := io.req.valid
  port.isWrite := io.req.bits.write
  port.writeData := VecInit(Seq.fill(ways)(io.req.bits.wdata))
  port.mask.get := VecInit(io.req.bits.wayMask.asBools)
}
