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

// Note: It is assumed that snoop data width and address width are equal
class AXIParams(val addrWidthBits: Int, val dataWidthBits: Int, val idBits: Int = 1) {
  require((dataWidthBits % 8) == 0)
  require(idBits >= 1)
}

class AXIAddress(p: AXIParams) extends Bundle {
  val addr    = Output(UInt(p.addrWidthBits.W)) // address for the transaction, should be burst aligned if bursts are used
  val size    = Output(UInt(3.W)) // size of data beat in bytes, set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
  val len     = Output(UInt(8.W)) // number of data beats -1 in burst: max 255 for incrementing, 15 for wrapping
  val burst   = Output(UInt(2.W)) // burst mode: 0 for fixed, 1 for incrementing, 2 for wrapping
  val id      = Output(UInt(p.idBits.W)) // transaction ID for multiple outstanding requests
  val lock    = Output(Bool()) // set to 1 for exclusive access
  val cache   = Output(UInt(4.W)) // cachability, set to 0010 or 0011
  val prot    = Output(UInt(3.W)) // generally ignored, set to to all zeroes
  val qos     = Output(UInt(4.W)) // not implemented, set to zeroes
}

class ACEReadAddress(p: AXIParams) extends AXIAddress(p) {
  val snoop   = Output(UInt(4.W))
  val domain  = Output(UInt(2.W))
  val bar     = Output(UInt(2.W))
}

class ACEWriteAddress(p: AXIParams) extends AXIAddress(p) {
  val snoop   = Output(UInt(3.W))
  val domain  = Output(UInt(2.W))
  val bar     = Output(UInt(2.W))
  val unique  = Output(Bool())
}


class AXIWriteData(p: AXIParams) extends Bundle {
  val data    = Output(UInt(p.dataWidthBits.W))
  val strb    = Output(UInt((p.dataWidthBits/8).W))
  val last    = Output(Bool())
}

class AXIWriteResponse(p: AXIParams) extends Bundle {
  val id      = Input(UInt(p.idBits.W))
  val resp    = Input(UInt(2.W))
}


class AXIReadData(p: AXIParams) extends Bundle {
  val data    = Input(UInt(p.dataWidthBits.W))
  val id      = Input(UInt(p.idBits.W))
  val last    = Input(Bool())
  val resp    = Input(UInt(2.W))
}

class ACEReadData(p: AXIParams) extends Bundle {
  val data    = Input(UInt(p.dataWidthBits.W))
  val id      = Input(UInt(p.idBits.W))
  val last    = Input(Bool())
  val resp    = Input(UInt(4.W))
}

class ACESnoopAddress(p: AXIParams) extends Bundle {
  val addr    = Input(UInt(p.addrWidthBits.W))
  val snoop   = Input(UInt(4.W))
  val prot    = Input(UInt(3.W))
}

class ACESnoopResponse(p: AXIParams) extends Bundle {
  val resp    = Output(UInt(5.W))
}

class ACESnoopData(p: AXIParams) extends Bundle {
  val data    = Output(UInt(p.dataWidthBits.W))
  val last    = Output(Bool())
}

/*

// M*st*r renamed to Host

class AXIHostIF(p: AXIParams) extends Bundle {  
  val aw  = new AXIAddress(p)
  val w   = new AXIWriteData(p)
  val b   = new AXIWriteResponse(p)
  val ar  = new AXIAddress(p)
  val r   = new AXIReadData(p)
}*/

class ACEHostIF(p: AXIParams) extends Bundle {
  val aw  = Decoupled(new ACEWriteAddress(p))
  val w   = Decoupled(new AXIWriteData(p))
  val wack = Output(Bool())
  val b   = Flipped(Decoupled(new AXIWriteResponse(p)))
  // Note: AXI and ACE write response is the same
  val ar  = Decoupled(new ACEReadAddress(p))
  val r   = Flipped(Decoupled(new ACEReadData(p)))
  val rack = Output(Bool())

  val ac  = Flipped(Decoupled(new ACESnoopAddress(p)))
  val cr  = Decoupled(new ACESnoopResponse(p))
  val cd  = Decoupled(new ACESnoopData(p))
}

