package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._

object busConst extends ChiselEnum {
  val OKAY   = "b00".U(8.W)
  val EXOKAY = "b01".U(8.W)
  val SLVERR = "b10".U(8.W)
  val DECERR = "b11".U(8.W)

  val DIRTY   = "b100".U(8.W)
  val UNIQUE  = "b1000".U(8.W)

  val ReadOnce         = 1.U(8.W) // Only used in non coherent buses
  val WriteOnce        = 2.U(8.W)
  val ReadShared            = 4.U(8.W) // Read when intenting to read
  val ReadUnique            = 5.U(8.W) // Read with intention to write, Ask the peers to release their instances of the cache
  val WriteBack             = 7.U(8.W) // Writeback. L1 still holds the line
  val Flush                 = 8.U(8.W) // L3 cache has to flush itself before returning anything
}


class ARPayload(addrWidth: Int, idWidth: Int) extends Bundle {
  val op      = UInt(8.W)
  val addr    = UInt(addrWidth.W)
  val len     = UInt(8.W)
  val id      = UInt(idWidth.W)
}

class AWPayload(addrWidth: Int, idWidth: Int) extends Bundle {
  val op      = UInt(8.W)
  val addr    = UInt(addrWidth.W)
  val len     = UInt(8.W)
  val id      = UInt(idWidth.W)
}

class WPayload(busBytes: Int) extends Bundle {
  val data    = UInt((busBytes * 8).W)
  val strb    = UInt((busBytes).W)
  val last    = Bool()
}

class BPayload(idWidth: Int) extends Bundle {
  val resp    = UInt(8.W)
  val id      = UInt(idWidth.W)
}

class RPayload(busBytes: Int, idWidth: Int) extends Bundle {
  val data    = UInt((busBytes * 8).W)
  val resp    = UInt(8.W)
  val id      = UInt(idWidth.W)
  val last    = Bool()
}


abstract class ReadBusAbstract extends Bundle {
  val ar: DecoupledIO[ARPayload]
  val r: DecoupledIO[RPayload]
}


abstract class WriteBusAbstract extends Bundle {
  val aw: DecoupledIO[AWPayload]
  val w: DecoupledIO[WPayload]
  val b: DecoupledIO[BPayload]
}



class ReadBus(addrWidth: Int, busBytes: Int, idWidth: Int) extends Bundle {
  val ar = DecoupledIO(new ARPayload(addrWidth = addrWidth, idWidth = idWidth))
  val r = Flipped(DecoupledIO(new RPayload(busBytes = busBytes, idWidth = idWidth)))
}

class WriteBus(addrWidth: Int, busBytes: Int, idWidth: Int) extends Bundle {
  val aw = DecoupledIO(new AWPayload(addrWidth = addrWidth, idWidth = idWidth))
  val w = DecoupledIO(new WPayload(busBytes = busBytes))
  val b = Flipped(DecoupledIO(new BPayload(idWidth = idWidth)))
}

class ReadWriteBus(addrWidth: Int, busBytes: Int, idWidth: Int) extends Bundle {
  val ar = DecoupledIO(new ARPayload(addrWidth = addrWidth, idWidth = idWidth))
  val r = Flipped(DecoupledIO(new RPayload(busBytes = busBytes, idWidth = idWidth)))

  val aw = DecoupledIO(new AWPayload(addrWidth = addrWidth, idWidth = idWidth))
  val w = DecoupledIO(new WPayload(busBytes = busBytes))
  val b = Flipped(DecoupledIO(new BPayload(idWidth = idWidth)))
}


class CoherenceRequest(addrWidth: Int, idWidth: Int)  extends Bundle {
  val op      = UInt(8.W)
  val id      = UInt(idWidth.W)
  val addr    = UInt(addrWidth.W)
}

class CoherenceResponse(idWidth: Int) extends Bundle {
  val resp    = UInt(8.W)
  val id      = UInt(idWidth.W)
}

class CoherenceData(busBytes: Int) extends Bundle {
  val data    = UInt((busBytes * 8).W)
  val last    = Bool()
}

class CoherenceAck extends Bundle {
}


class CoherentBus(addrWidth: Int, busBytes: Int, idWidth: Int) extends ReadWriteBus(addrWidth = addrWidth, busBytes = busBytes, idWidth = idWidth) {
  val creq  = Flipped(DecoupledIO(new CoherenceRequest(addrWidth = addrWidth, idWidth = idWidth)))
  val cresp = DecoupledIO(new CoherenceResponse(idWidth = idWidth))
  val cdata = DecoupledIO(new CoherenceData(busBytes = busBytes))
  val ack   = Flipped(DecoupledIO(new CoherenceAck))
}
