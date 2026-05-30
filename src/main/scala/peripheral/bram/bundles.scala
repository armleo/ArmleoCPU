package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.utils.threeStateStageIO

class DataArrayReq(addrWidth: Int, busBytes: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val read = Bool()
  val write = Bool()
  val wdata = UInt((busBytes * 8).W)
  val wmask = UInt(busBytes.W)
}

class DataArrayResp(busBytes: Int) extends Bundle {
  val rdata = UInt((busBytes * 8).W)
}

class DataArrayIO(addrWidth: Int, busBytes: Int) extends Bundle {
  val req = Input(new DataArrayReq(addrWidth, busBytes))
  val resp = Output(new DataArrayResp(busBytes))
}

class RequestKeeperIO(implicit val bp: BusParams) extends Bundle {
  val stage = new threeStateStageIO
  val request = Input(new AWPayload)
  val decrement = Input(Bool())
  val id = Output(UInt(bp.idWidth.W))
  val burstRemaining = Output(UInt((bp.lenWidth + 1).W))
  val last = Output(Bool())
}

class BurstManagerIO(implicit val bp: BusParams) extends Bundle {
  val stage = new threeStateStageIO
  val requestAddr = Input(UInt(bp.addrWidth.W))
  val saveIncrementedAddr = Input(Bool())
  val finish = Input(Bool())
  val incrementedAddr = Output(UInt(bp.addrWidth.W))
  val addr = Output(UInt(bp.addrWidth.W))
}

class ReaderIO(addrWidth: Int, busBytes: Int)(implicit val bp: BusParams) extends Bundle {
  val stage = new threeStateStageIO
  val ar = Flipped(Decoupled(new ARPayload))
  val r = Decoupled(new RPayload)
  val dataArrayReq = Output(new DataArrayReq(addrWidth, busBytes))
  val dataArrayResp = Input(new DataArrayResp(busBytes))
  val requestKeeper = Flipped(new RequestKeeperIO)
  val burstManager = Flipped(new BurstManagerIO)
  val resp = Input(UInt(8.W))
}

class WriterIO(addrWidth: Int, busBytes: Int)(implicit val bp: BusParams) extends Bundle {
  val stage = new threeStateStageIO
  val aw = Flipped(Decoupled(new AWPayload))
  val w = Flipped(Decoupled(new WPayload))
  val b = Decoupled(new BPayload)
  val dataArrayReq = Output(new DataArrayReq(addrWidth, busBytes))
  val requestKeeper = Flipped(new RequestKeeperIO)
  val burstManager = Flipped(new BurstManagerIO)
  val resp = Input(UInt(8.W))
}

class MisalignedAccessHandlerIO(implicit val bp: BusParams) extends Bundle {
  val stage = new threeStateStageIO
  val ar = Flipped(Decoupled(new ARPayload))
  val aw = Flipped(Decoupled(new AWPayload))
  val w = Flipped(Decoupled(new WPayload))
  val r = Decoupled(new RPayload)
  val b = Decoupled(new BPayload)
}
