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
  

  val OP_READ         = 1.U(8.W) // Only used in non coherent buses

  
  val ACQUIRE_DATA_SHARED   = 4.U(8.W) // Read when intenting to read
  val ACQUIRE_DATA_UNIQUE   = 5.U(8.W) // Read with intention to write
  val ACQUIRE_UNQIUE        = 6.U(8.W) // Ask the peers to release their instances of the cache
  val RELEASE_DATA          = 7.U(8.W) // Writeback. L1 still holds the line
  
  val FLUSH                 = 8.U(8.W) // L3 cache has to flush itself before returning anything
  val PROBE                 = 9.U(8.W)
  val PROBE_ACK             = 10.U(8.W)
}


class AxPayload(busBytes: Int)(implicit ccx: CCXParams) extends Bundle {
  val addr    = Output(UInt((ccx.apLen).W)) // address for the transaction, should be burst aligned if bursts are used
  val op      = Output(UInt(8.W))
  val data    = Output(UInt((busBytes * 8).W))
  val strb    = Output(UInt((busBytes).W))
}


class Response(busBytes: Int) extends Bundle {
  val data    = Input(UInt((busBytes * 8).W))
  val resp    = Input(UInt(8.W))
}

class Bus()(implicit ccx: CCXParams) extends Bundle {
  val req  = DecoupledIO(new AxPayload(busBytes = ccx.busBytes))
  val resp   = Flipped(DecoupledIO(new Response(busBytes = ccx.busBytes)))
}

class CoherenceRequest(implicit val ccx: CCXParams) extends Bundle {
  val op      = Input(UInt(8.W))
  val addr    = Input(SInt((ccx.apLen).W))
}

class CoherenceResponse()(implicit ccx: CCXParams) extends Bundle {
  val resp    = Output(UInt(8.W))
  val data    = Output(UInt((ccx.busBytes * 8).W))
}

class CoherentBus()(implicit ccx: CCXParams) extends Bus {
  val creq  = Flipped(DecoupledIO(new CoherenceRequest))
  val cresp = DecoupledIO(new CoherenceResponse)
  val ack   = Output(Bool())
}
