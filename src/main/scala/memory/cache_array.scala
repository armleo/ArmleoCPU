package armleocpu

import chisel3._
import chisel3.util._

class CacheArrayReq(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  val addr        = UInt(ccx.apLen.W)

  // Meta write
  val metaWrite   = Bool()
  val metaWdata   = Vec(cp.ways, new CacheMeta)
  val metaMask    = UInt(cp.ways.W) // ways-wide mask (needed for reset)

  // Data write (one cache line, one way)
  val dataWrite   = Bool()
  val dataWayIdx  = UInt(cp.waysLog2.W)                 // selects the way to modify
  val dataWdata   = Vec(1 << ccx.cacheLineLog2, UInt(8.W)) // line-bytes payload
  val dataMask    = Vec(1 << ccx.cacheLineLog2, Bool())    // per-byte mask within the line

  val destinationId = UInt(4.W) // passed through to response
}

class CacheArrayResp(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  val metaRdata      = Vec(cp.ways, new CacheMeta)
  val dataRdata      = Vec(1 << (cp.waysLog2 + ccx.cacheLineLog2), UInt(8.W))
  val destinationId  = UInt(4.W)
}

class CacheArrayIO(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  val req  = Flipped(Valid(new CacheArrayReq))
  val resp = Valid(new CacheArrayResp)
}

class CacheArray(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Module {
  val io = IO(new CacheArrayIO)

  // SRAM layout:
  // meta: [set] -> Vec(ways, Meta)
  // data: [set] -> Vec(ways * lineBytes, Byte)
  val meta = SRAM.masked(cp.entries, Vec(cp.ways, new CacheMeta), 0, 0, 1)
  val data = SRAM.masked(cp.entries, Vec(1 << (cp.waysLog2 + ccx.cacheLineLog2), UInt(8.W)), 0, 0, 1)

  // Handy constants
  val lineBytes    = 1 << ccx.cacheLineLog2
  val ways         = 1 << cp.waysLog2
  val totalBytes   = ways * lineBytes

  io.resp.valid := false.B
  io.resp.bits.metaRdata := VecInit(Seq.fill(cp.ways)(0.U.asTypeOf(new CacheMeta)))
  io.resp.bits.dataRdata := VecInit(Seq.fill(totalBytes)(0.U(8.W)))
  io.resp.bits.destinationId := 0.U

  // Register response valid + destination ID so they line up with the SRAM read
  val respValid          = RegInit(false.B)
  val respDestinationId  = RegInit(0.U(4.W))
  io.resp.valid := respValid
  io.resp.bits.destinationId := respDestinationId

  // Always drive read data to outputs (last connect wins)
  io.resp.bits.metaRdata := meta.readwritePorts(0).readData
  io.resp.bits.dataRdata := data.readwritePorts(0).readData

  // Default: disable ports unless we have a request
  meta.readwritePorts(0).enable  := false.B
  data.readwritePorts(0).enable  := false.B
  meta.readwritePorts(0).isWrite := false.B
  data.readwritePorts(0).isWrite := false.B
  // Provide safe defaults for write buses
  meta.readwritePorts(0).writeData:= io.req.bits.metaWdata
  meta.readwritePorts(0).mask.get := io.req.bits.metaMask.asBools



  // DATA: expand (way, byte-in-line) -> flat Vec(totalBytes)
  val expandedData = Wire(Vec(totalBytes, UInt(8.W)))
  val expandedMask = Wire(Vec(totalBytes, Bool()))
  for (w <- 0 until ways) {
    for (b <- 0 until lineBytes) {
      val flat = w*lineBytes + b
      val selWay = io.req.bits.dataWayIdx === w.U
      expandedData(flat) := Mux(selWay, io.req.bits.dataWdata(b), 0.U)
      expandedMask(flat) := io.req.bits.dataWrite && selWay && io.req.bits.dataMask(b)
    }
  }
  data.readwritePorts(0).writeData := expandedData
  data.readwritePorts(0).mask.get  := expandedMask
  
  
  val setIdx = io.req.bits.addr(ccx.cacheLineLog2 + cp.entriesLog2 - 1, ccx.cacheLineLog2)
  meta.readwritePorts(0).address  := setIdx
  data.readwritePorts(0).address := setIdx

  when (io.req.valid) {
    // META
    meta.readwritePorts(0).enable   := true.B
    meta.readwritePorts(0).isWrite  := io.req.bits.metaWrite

    data.readwritePorts(0).enable  := true.B
    data.readwritePorts(0).isWrite := io.req.bits.dataWrite


    // Pipeline response control
    respValid := true.B
    respDestinationId := io.req.bits.destinationId
  } .otherwise {
    respValid := false.B
  }
}
