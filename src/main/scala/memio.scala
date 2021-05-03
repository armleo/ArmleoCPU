package armleocpu

import chisel3._
import chisel3.util._

// TODO: Remove
object MemHostIfResponse {
	val OKAY = "b00".U(2.W)
	val SLAVEERROR = "b10".U(2.W)
	val DECODEERROR = "b11".U(2.W)
}

class MemHostIf extends Bundle {
	val address = Output(UInt(34.W))
	val response = Input(UInt(2.W))
	val burstcount = Output(UInt(5.W))
	val waitrequest = Input(Bool())

	val read = Output(Bool())
	val readdata = Input(UInt(32.W))
	val readdatavalid = Input(Bool())

	val write = Output(Bool())
	val writedata = Output(UInt(32.W))
}

class AXIAddress(addrWidthBits: Int, idBits: Int) extends Bundle {
  // Handshake signals
  val valid = Output(Bool())
  val ready = Input(Bool())
  
  val addr    = Output(UInt(addrWidthBits.W)) // address for the transaction, should be burst aligned if bursts are used
  val size    = Output(UInt(3.W)) // size of data beat in bytes, set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
  val len     = Output(UInt(8.W)) // number of data beats -1 in burst: max 255 for incrementing, 15 for wrapping
  val burst   = Output(UInt(2.W)) // burst mode: 0 for fixed, 1 for incrementing, 2 for wrapping
  val id      = Output(UInt(idBits.W)) // transaction ID for multiple outstanding requests
  val lock    = Output(Bool()) // set to 1 for exclusive access
  val cache   = Output(UInt(4.W)) // cachability, set to 0010 or 0011
  val prot    = Output(UInt(3.W)) // generally ignored, set to to all zeroes
  val qos     = Output(UInt(4.W)) // not implemented, set to zeroes
}

class AXIWriteData(dataWidthBits: Int) extends Bundle {
  // Handshake signals
  val valid = Output(Bool())
  val ready = Input(Bool())

  val data    = Output(UInt(dataWidthBits.W))
  val strb    = Output(UInt((dataWidthBits/8).W))
  val last    = Output(Bool())
}

class AXIWriteResponse(idBits: Int) extends Bundle {
  // Handshake signals
  val valid = Output(Bool())
  val ready = Input(Bool())

  val id      = Input(UInt(idBits.W))
  val resp    = Input(UInt(2.W))
}

class AXIReadData(dataWidthBits: Int, idBits: Int) extends Bundle {
  // Handshake signals
  val valid = Output(Bool())
  val ready = Input(Bool())

  val data    = Input(UInt(dataWidthBits.W))
  val id      = Input(UInt(idBits.W))
  val last    = Input(Bool())
  val resp    = Input(UInt(2.W))
}

// M*st*r renamed to Host

class AXIHostIF(addrWidthBits: Int, dataWidthBits: Int, idBits: Int) extends Bundle {  
  val aw  = new AXIAddress(addrWidthBits, idBits)
  val w   = new AXIWriteData(dataWidthBits)
  val b   = new AXIWriteResponse(idBits)
  val ar  = new AXIAddress(addrWidthBits, idBits)
  val r   = new AXIReadData(dataWidthBits, idBits)
  
  require((dataWidthBits % 8) == 0)
}

