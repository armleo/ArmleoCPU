package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.utils.threeStateStageIO

class BRAMBusParams(addrWidth: Int, busBytes: Int, lenWidth: Int = 8)
    extends BusParams(addrWidth = addrWidth, busBytes = busBytes, idWidth = 0, lenWidth = lenWidth)

class AXPayload()(implicit val bp: BusParams) extends Bundle {
  val write = Bool()
  val op = UInt(8.W)
  val addr = UInt(bp.addrWidth.W)
  val len = UInt(bp.lenWidth.W)
}

class AXBus()(implicit val bp: BusParams) extends Bundle {
  val ax = Decoupled(new AXPayload)
  val w = Decoupled(new WPayload)
  val r = Flipped(Decoupled(new RPayload))
  val b = Flipped(Decoupled(new BPayload))
}

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
  val ar = Flipped(Decoupled(new ARPayload))
  val r = Decoupled(new RPayload)
  val dataArrayReq = Output(new DataArrayReq(addrWidth, busBytes))
  val dataArrayResp = Input(new DataArrayResp(busBytes))
  val requestKeeper = Flipped(new RequestKeeperIO)
  val burstManager = Flipped(new BurstManagerIO)
  val resp = Input(UInt(8.W))
  val writePriority = Input(Bool())
  val starting = Output(Bool())
  val active = Output(Bool())
  val done = Output(Bool())
}

class WriterIO(addrWidth: Int, busBytes: Int)(implicit val bp: BusParams) extends Bundle {
  val aw = Flipped(Decoupled(new AWPayload))
  val w = Flipped(Decoupled(new WPayload))
  val b = Decoupled(new BPayload)
  val dataArrayReq = Output(new DataArrayReq(addrWidth, busBytes))
  val requestKeeper = Flipped(new RequestKeeperIO)
  val burstManager = Flipped(new BurstManagerIO)
  val resp = Input(UInt(8.W))
  val starting = Output(Bool())
  val active = Output(Bool())
  val done = Output(Bool())
}

class MisalignedAccessHandlerIO(implicit val bp: BusParams) extends Bundle {
  val ar = Flipped(Decoupled(new ARPayload))
  val aw = Flipped(Decoupled(new AWPayload))
  val w = Flipped(Decoupled(new WPayload))
  val r = Decoupled(new RPayload)
  val b = Decoupled(new BPayload)
  val starting = Output(Bool())
  val active = Output(Bool())
  val done = Output(Bool())
}

class ReadMisalignmentCheckerIO(implicit val bp: BusParams) extends Bundle {
  val in = Flipped(Decoupled(new ARPayload))
  val out = Decoupled(new ARPayload)
  val misaligned = Output(Bool())
}

class WriteMisalignmentCheckerIO(implicit val bp: BusParams) extends Bundle {
  val in = Flipped(Decoupled(new AWPayload))
  val out = Decoupled(new AWPayload)
  val misaligned = Output(Bool())
}

class MisalignmentCheckerIO(implicit val bp: BusParams) extends Bundle {
  val arIn = Flipped(Decoupled(new ARPayload))
  val arOut = Decoupled(new ARPayload)
  val awIn = Flipped(Decoupled(new AWPayload))
  val awOut = Decoupled(new AWPayload)
  val readMisaligned = Output(Bool())
  val writeMisaligned = Output(Bool())
}

class IdYankerIO(depth: Int, idWidth: Int)(implicit val outerBp: BusParams, val innerBp: BRAMBusParams) extends Bundle {
  val in = Flipped(new ReadWriteBus()(outerBp))
  val out = new ReadWriteBus()(innerBp)
}

class RequestMuxControlIO extends Bundle {
  val readerStarting = Input(Bool())
  val readerActive = Input(Bool())
  val writerStarting = Input(Bool())
  val writerActive = Input(Bool())
  val misalignedStarting = Input(Bool())
  val misalignedActive = Input(Bool())

  val selectReader = Output(Bool())
  val selectWriter = Output(Bool())
  val selectMisaligned = Output(Bool())
}

class DataArrayReqMuxIO(addrWidth: Int, busBytes: Int) extends Bundle {
  val reader = Input(new DataArrayReq(addrWidth, busBytes))
  val writer = Input(new DataArrayReq(addrWidth, busBytes))
  val selectWriter = Input(Bool())
  val out = Output(new DataArrayReq(addrWidth, busBytes))
}
