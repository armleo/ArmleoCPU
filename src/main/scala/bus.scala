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


class ax_payload_t(cp: CoreParams) extends Bundle {
  val addr    = Output(SInt((cp.apLen).W)) // address for the transaction, should be burst aligned if bursts are used
  val size    = Output(UInt(3.W)) // size of data beat in bytes, set to UInt(log2Ceil((dataBits/8)-1)) for full-width bursts
  val len     = Output(UInt(8.W)) // number of data beats minus one in burst: max 255 for incrementing, 15 for wrapping
  val cache   = Output(UInt(2.W))
  // Cache request type
  // 0x0: Read shared
  // 0x1: Read Unique
  // 0x2: Invalidate

  // For writes:
  // 0x1: Write back
  // 0x2: Invalidate (so L2 can update the directory)
}


class w_payload_t(cp: CoreParams) extends Bundle {
  val data    = Output(UInt((cp.busBytes * 8).W))
  val strb    = Output(UInt((cp.busBytes).W))
  val last    = Output(Bool())
}

class b_payload_t(cp: CoreParams) extends Bundle {
  val resp    = Input(UInt(2.W))
}

class r_payload_t(cp: CoreParams) extends Bundle {
  val data    = Input(UInt((cp.busBytes * 8).W))
  val last    = Input(Bool())
  val resp    = Input(UInt(2.W))
}


// Cache coherency:

class ac_payload_t(cp: CoreParams) extends Bundle {
  val addr    = Input(SInt((cp.apLen).W))
  val snoop   = Input(UInt(5.W))
  
  // Snoop types:
    // 0x1: Read shared clean
    // 0x2: Read unique (can be clean or dirty)
    // 0x3: Write back request
}

class c_payload_t(cp: CoreParams) extends Bundle {
  val resp    = Output(UInt(2.W))
}

class cd_payload_t(cp: CoreParams) extends Bundle {
  val data    = Output(UInt((cp.busBytes * 8).W))
  val last    = Output(Bool())
}


class ibus_t(cp: CoreParams, coherency: Boolean = false) extends Bundle {
  val ar  = DecoupledIO(new ax_payload_t(cp))
  val r   = Flipped(DecoupledIO(new r_payload_t(cp)))

  /*
  if(!coherency) {
    assert(ar.bits.cache === 0.U, "Cache type not supported")
  }*/
}

class dbus_t(cp: CoreParams, coherency: Boolean = false) extends ibus_t(cp = cp, coherency = coherency) {
  val aw  = DecoupledIO(new ax_payload_t(cp))
  val w   = DecoupledIO(new w_payload_t(cp))
  val b   = Flipped(DecoupledIO(new b_payload_t(cp)))

  /*
  if(!coherency) {
    assert(aw.bits.cache === 0.U, "Cache type not supported")
  }*/
}

class corebus_t(cp: CoreParams) extends dbus_t(cp = cp) {
}
/*
class corebus_t(cp: CoreParams) extends dbus_t(cp = cp, coherency = true) {
  val ac = Flipped(DecoupledIO(new ac_payload_t(cp)))
  val c = DecoupledIO(new c_payload_t(cp))
  val cd = DecoupledIO(new cd_payload_t(cp))
}
*/

class pbus_t(cp: CoreParams) extends dbus_t(cp = cp) {
  /*
  when(aw.valid) {
    assert(aw.bits.len === 0.U, "Pbus burst not supported")
    assert(aw.bits.cache === 0.U, "Pbus cache type not supported")
    //assert(aw.bits.lock === false.B, "Pbus exclusive access not supported")
  }
  
  when(ar.valid) {
    assert(ar.bits.len === 0.U, "Pbus burst not supported")
    assert(aw.bits.cache === 0.U, "Pbus cache type not supported")
    //assert(ar.bits.lock === false.B, "Pbus exclusive access not supported")
  }*/
}
