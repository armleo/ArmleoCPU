package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._

object bus_resp_t extends ChiselEnum {
    val OKAY   = "b00".U(2.W)
    val EXOKAY = "b01".U(2.W)
    val SLVERR = "b10".U(2.W)
    val DECERR = "b11".U(2.W)
}


class pbus_ax_payload_t(ccx: CCXParams, busBytes: Int) extends Bundle {
  val addr    = Output(SInt((ccx.apLen).W)) // address for the transaction, should be burst aligned if bursts are used
  val op      = Output(UInt(4.W))
  // op request tyoe:
    // 0x1: Read
    // 0x2: Write
    // 0x3: Flush
  val data    = Output(UInt((busBytes * 8).W))
  val strb    = Output(UInt((busBytes).W))
}

class ax_payload_t(ccx: CCXParams, busBytes: Int) extends pbus_ax_payload_t(ccx = ccx, busBytes = busBytes) {
  val cache   = Output(UInt(2.W))
  // Cache request type
  // 0x0: Read shared
  // 0x1: Read Unique
  // 0x2: Invalidate

  // For writes:
  // 0x1: Write back
  // 0x2: Invalidate (so L2 can update the directory)
}


class response_t(busBytes: Int) extends Bundle {
  val data    = Input(UInt((busBytes * 8).W))
  val resp    = Input(UInt(2.W))
}


// Cache coherency:

class ac_payload_t(ccx: CCXParams) extends Bundle {
  val addr    = Input(SInt((ccx.apLen).W))
  val snoop   = Input(UInt(5.W))
  
  // Snoop types:
    // 0x1: Read shared clean
    // 0x2: Read unique (can be clean or dirty)
    // 0x3: Write back request
}

class c_payload_t(busBytes: Int) extends Bundle {
  val resp    = Output(UInt(2.W))
  val data    = Output(UInt((busBytes * 8).W))
}



class dbus_t(ccx: CCXParams, coherency: Boolean = false) extends Bundle {
  val ax  = DecoupledIO(new ax_payload_t(ccx, busBytes = ccx.busBytes))
  val r   = Flipped(DecoupledIO(new response_t(busBytes = ccx.busBytes)))
}

class corebus_t(ccx: CCXParams) extends dbus_t(ccx = ccx, coherency = true) {
  val ac = Flipped(DecoupledIO(new ac_payload_t(ccx)))
  val c = DecoupledIO(new c_payload_t(busBytes = ccx.busBytes))
}


class pbus_t(ccx: CCXParams) extends dbus_t(ccx = ccx) {
  
}
