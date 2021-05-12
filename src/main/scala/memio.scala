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
  val addr    = (UInt(p.addrWidthBits.W)) // address for the transaction, should be burst aligned if bursts are used
  val size    = (UInt(3.W)) // size of data beat in bytes, set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
  val len     = (UInt(8.W)) // number of data beats -1 in burst: max 255 for incrementing, 15 for wrapping
  val burst   = (UInt(2.W)) // burst mode: 0 for fixed, 1 for incrementing, 2 for wrapping
  val id      = (UInt(p.idBits.W)) // transaction ID for multiple outstanding requests
  val lock    = (Bool()) // set to 1 for exclusive access
  val cache   = (UInt(4.W)) // cachability, set to 0010 or 0011
  val prot    = (UInt(3.W)) // generally ignored, set to to all zeroes
  val qos     = (UInt(4.W)) // not implemented, set to zeroes
}

class ACEReadAddress(p: AXIParams) extends AXIAddress(p) {
  // Encoding
  // operation         ,      bar,         domain,     snoop
  // ReadNoSnoop       ,      0b0,      0b00/0b11,    0b0000
  //
  // ReadOnce          ,      0b0,      0b01/0b10,    0b0000
  // ReadShared        ,                              0b0001
  // ReadClean         ,                              0b0010
  // ReadNotSharedDirty,                              0b0011
  // ReadUnique        ,                              0b0111
  // CleanUnique       ,                              0b1011
  // MakeUnique        ,                              0b1100

  val snoop   = (UInt(4.W))
  val domain  = (UInt(2.W))
  val bar     = (UInt(2.W))

  def isReadNoSnoop():Bool = (bar === 0.U) &&
    ((domain === "b01".U) || (domain === "b10".U)) &&
    (snoop === "b0000".U)
  def isReadShared():Bool = (bar === 0.U) &&
    ((domain === "b11".U) || (domain === 0.U)) &&
    (snoop === "b0001".U)
}

class ACEWriteAddress(p: AXIParams) extends AXIAddress(p) {
  val snoop   = (UInt(3.W))
  val domain  = (UInt(2.W))
  val bar     = (UInt(2.W))
  // val unique  = (Bool()) // Only required of write evict is supported

  
  def isWriteUnique(): Bool = ((bar === 0.U) &&
    ((domain === "b10".U) || (domain === "b01".U)) &&
    (snoop === "b000".U))
  def isWriteNoSnoop(): Bool = ((bar === 0.U) &&
    ((domain === "b00".U) || (domain === "b11".U)) &&
    (snoop === "b000".U))
}


class AXIWriteData(p: AXIParams) extends Bundle {
  val data    = (UInt(p.dataWidthBits.W))
  val strb    = (UInt((p.dataWidthBits/8).W))
  val last    = (Bool())
}

class AXIWriteResponse(p: AXIParams) extends Bundle {
  val id      = (UInt(p.idBits.W))
  val resp    = (UInt(2.W))
}


class AXIReadData(p: AXIParams) extends Bundle {
  val data    = (UInt(p.dataWidthBits.W))
  val id      = (UInt(p.idBits.W))
  val last    = (Bool())
  val resp    = (UInt(2.W))
}

class ACEReadData(p: AXIParams) extends Bundle {
  val data    = (UInt(p.dataWidthBits.W))
  val id      = (UInt(p.idBits.W))
  val last    = (Bool())
  val resp    = (UInt(4.W))
  // RESP encoding:
  // RRESP[3] isShared
  // RRESP[2] PassDirty

}

// Note: ACE Snoop Address is always WRAP request with size of 64 bytes or 8 x 64 bits
class ACESnoopAddress(p: AXIParams) extends Bundle {
  val addr    = (UInt(p.addrWidthBits.W))
  val snoop   = (UInt(4.W))
  val prot    = (UInt(3.W))
}

class ACESnoopResponse(p: AXIParams) extends Bundle {
  val resp    = (UInt(5.W))
  // Bit 0: DataTransfer
  // Bit 1: ECC Error
  // Bit 2: PassDirty
  // Bit 3: IsShared
  // Bit 4: WasUnique
}

class ACESnoopData(p: AXIParams) extends Bundle {
  val data    = (UInt(p.dataWidthBits.W))
  val last    = (Bool())
}



// M*st*r renamed to Host
// Sl*ve renamed to Client

class AXIHostIF(p: AXIParams) extends Bundle {  
  val aw  = Decoupled(new AXIAddress(p))
  val w   = Decoupled(new AXIWriteData(p))
  val b   = Flipped(Decoupled(new AXIWriteResponse(p)))
  val ar  = Decoupled(new AXIAddress(p))
  val r   = Flipped(Decoupled(new AXIReadData(p)))
}

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

