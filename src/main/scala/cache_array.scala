package armleocpu

import chisel3._
import chisel3.util._

class CacheArrayReq(ccx: CCXParams, cp: CacheParams) extends Bundle {
  val addr      = UInt(ccx.apLen.W)
  val metaWrite = Bool()
  val metaWdata = Vec(cp.ways, new CacheMeta(ccx, cp))
  val metaMask  = UInt(cp.ways.W)
  val dataWrite = Bool()
  val dataWdata = Vec(1 << (ccx.cacheLineLog2), UInt(8.W))
  val dataMask  = UInt((1 << (cp.waysLog2 + ccx.cacheLineLog2)).W)

  // FIXME: DataWdata needs to be properly handled
  // FIXME: Data mask needs to be proper handled
}

class CacheArrayResp(ccx: CCXParams, cp: CacheParams) extends Bundle {
  val metaRdata = Vec(cp.ways, new CacheMeta(ccx, cp))
  val dataRdata = Vec(1 << (cp.waysLog2 + ccx.cacheLineLog2), UInt(8.W))
}

class CacheArraysIO(ccx: CCXParams, cp: CacheParams) extends Bundle {
  val req  = Flipped(Decoupled(new CacheArrayReq(ccx, cp)))
  val resp = Decoupled(new CacheArrayResp(ccx, cp))
}

class CacheArrays(ccx: CCXParams, cp: CacheParams) extends Module {
  val io = IO(new CacheArraysIO(ccx, cp))

  val meta = SRAM.masked(cp.entries, Vec(cp.ways, new CacheMeta(ccx, cp)), 0, 0, 1)
  val data = SRAM.masked(cp.entries, Vec(1 << (cp.waysLog2 + ccx.cacheLineLog2), UInt(8.W)), 0, 0, 1)

  // Default outputs
  io.resp.bits.metaRdata := VecInit(Seq.fill(cp.ways)(0.U.asTypeOf(new CacheMeta(ccx, cp))))
  io.resp.bits.dataRdata := VecInit(Seq.fill(1 << (cp.waysLog2 + ccx.cacheLineLog2))(0.U(8.W)))
  io.req.ready := true.B

  val resp_valid = Reg(Bool())

  io.resp.valid := resp_valid
  // Output read data
  io.resp.bits.metaRdata := meta.readwritePorts(0).readData
  io.resp.bits.dataRdata := data.readwritePorts(0).readData


  // Handle requests
  when(io.req.valid) {
    val idx = io.req.bits.addr(ccx.cacheLineLog2 + cp.entriesLog2 - 1, ccx.cacheLineLog2)

    // Meta array access
    meta.readwritePorts(0).address := idx
    meta.readwritePorts(0).enable := true.B
    meta.readwritePorts(0).isWrite := io.req.bits.metaWrite
    meta.readwritePorts(0).writeData := io.req.bits.metaWdata
    meta.readwritePorts(0).mask.get := io.req.bits.metaMask.asBools

    // Data array access
    data.readwritePorts(0).address := idx
    data.readwritePorts(0).enable := true.B
    data.readwritePorts(0).isWrite := io.req.bits.dataWrite
    data.readwritePorts(0).writeData := io.req.bits.dataWdata
    data.readwritePorts(0).mask.get := io.req.bits.dataMask.asBools

    resp_valid := true.B

  }
}