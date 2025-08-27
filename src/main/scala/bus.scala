package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._

// Important:
// This is similar to other buses HOWEVER
// Request can be revoked
// OP field is used to determine the type of the request
// Reads go on one bus writes on another


object busConst extends ChiselEnum {
  val OKAY   = "b00".U(8.W)
  val EXOKAY = "b01".U(8.W)
  val SLVERR = "b10".U(8.W)
  val DECERR = "b11".U(8.W)

  // Used to select from the rresp
  val DIRTYBITNUM = 3
  val UNIQUEBITNUM = 4
  val RETURNDATABITNUM = 5

  val ReadOnce         = 1.U(8.W) // Only used in non coherent buses
  val WriteOnce        = 16.U(8.W)

  val ReadShared            = 2.U(8.W) // Read when intenting to read
  val ReadUnique            = 3.U(8.W) // Read with intention to write, Ask the peers to release their instances of the cache
  val WriteBack             = 17.U(8.W) // Writeback. L1 still holds the line
  
  val Flush                 = 32.U(8.W) // L3 cache has to flush itself before returning anything
  val FlushRemove           = 33.U(8.W) // L3 Cache has to writeback everything AND then remove every entry before returning anything
}


class BusParams(val addrWidth: Int = 1, val busBytes: Int = 1, val idWidth: Int = 0, val lenWidth: Int = 0) {

}

class ARPayload()(implicit val bp: BusParams) extends Bundle {
  import bp._
  val op      = UInt(8.W)
  val addr    = UInt(addrWidth.W)
  val len     = UInt(lenWidth.W)
  val id      = UInt(idWidth.W)
}

class AWPayload()(implicit val bp: BusParams) extends Bundle {
  import bp._
  val op      = UInt(8.W)
  val addr    = UInt(addrWidth.W)
  val len     = UInt(lenWidth.W)
  val id      = UInt(idWidth.W)
}

class WPayload()(implicit val bp: BusParams) extends Bundle {
  import bp._
  val data    = UInt((busBytes * 8).W)
  val strb    = UInt((busBytes).W)
  val last    = Bool()
}

class BPayload()(implicit val bp: BusParams) extends Bundle {
  import bp._
  val resp    = UInt(8.W)
  val id      = UInt(idWidth.W)
}

class RPayload()(implicit val bp: BusParams) extends Bundle {
  import bp._
  val data    = UInt((busBytes * 8).W)
  val resp    = UInt(8.W)
  val id      = UInt(idWidth.W)
  val last    = Bool()
}


abstract trait ReadBusAbstract extends Bundle {
  val ar: DecoupledIO[ARPayload]
  val r: DecoupledIO[RPayload]
}


abstract trait WriteBusAbstract extends Bundle {
  val aw: DecoupledIO[AWPayload]
  val w: DecoupledIO[WPayload]
  val b: DecoupledIO[BPayload]
}

class ReadBus()(implicit val bp: BusParams) extends Bundle {
  val ar = DecoupledIO(new ARPayload)
  val r = Flipped(DecoupledIO(new RPayload))
}

class WriteBus()(implicit val bp: BusParams) extends Bundle {
  val aw = DecoupledIO(new AWPayload)
  val w = DecoupledIO(new WPayload)
  val b = Flipped(DecoupledIO(new BPayload))
}

class ReadWriteBus()(implicit val bp: BusParams) extends WriteBusAbstract with ReadBusAbstract {
  val ar = DecoupledIO(new ARPayload)
  val r = Flipped(DecoupledIO(new RPayload))

  val aw = DecoupledIO(new AWPayload)
  val w = DecoupledIO(new WPayload)
  val b = Flipped(DecoupledIO(new BPayload))
}


class CoherentBusParams(addrWidth: Int)
  (implicit val ccx: CCXParams)
  extends BusParams(addrWidth = addrWidth, busBytes = ccx.busBytes, idWidth = 0, lenWidth = 0) {
    val coherentDataBytes = ccx.busBytes
    require(ccx.busBytes == ccx.cacheLineBytes)
}

class CoherenceRequest()(implicit val bp: CoherentBusParams)  extends Bundle {
  import bp._
  val op      = UInt(8.W)
  val addr    = UInt(addrWidth.W)
}

class CoherenceResponse()(implicit val bp: CoherentBusParams) extends Bundle {
  import bp._
  val resp    = UInt(8.W)
  val id      = UInt(idWidth.W)
}

class CoherenceData()(implicit val bp: CoherentBusParams) extends Bundle {
  import bp._
  val data    = UInt((coherentDataBytes * 8).W)
}
/*
class CoherenceAck extends Bundle {
}
*/

class CoherentBus()(implicit override val bp: CoherentBusParams) extends ReadWriteBus {
  val creq  = Flipped(DecoupledIO(new CoherenceRequest))
  val cresp = DecoupledIO(new CoherenceResponse)
  val cdata = DecoupledIO(new CoherenceData)
  //val ack   = DecoupledIO(new CoherenceAck)
}
