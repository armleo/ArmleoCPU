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
  val lock    = Output(Bool()) // set to 1 for exclusive access
}

class ax_t(cp: CoreParams) extends DecoupledIO(new ax_payload_t(cp)) {
}


class w_payload_t(cp: CoreParams) extends Bundle {
  val data    = Output(UInt((cp.busBytes * 8).W))
  val strb    = Output(UInt((cp.busBytes).W))
  val last    = Output(Bool())
}

class w_t(cp: CoreParams) extends DecoupledIO(new w_payload_t(cp)) {
}


class b_payload_t(cp: CoreParams) extends Bundle {
  val resp    = Input(UInt(2.W))
}
class b_t(cp: CoreParams) extends DecoupledIO(new b_payload_t(cp)) {
}

class r_payload_t(cp: CoreParams) extends Bundle {
  val data    = Input(UInt((cp.busBytes * 8).W))
  val last    = Input(Bool())
  val resp    = Input(UInt(2.W))
}

class r_t(cp: CoreParams) extends DecoupledIO(new r_payload_t(cp)) {
}


class ibus_t(cp: CoreParams) extends Bundle {
  val ar  = new ax_t(cp)
  val r   = Flipped(new r_t(cp))
}

class dbus_t(cp: CoreParams) extends ibus_t(cp = cp) {
  val aw  = new ax_t(cp)
  val w   = new w_t(cp)
  val b   = Flipped(new b_t(cp))
}

class pbus_t(cp: CoreParams) extends dbus_t(cp = cp) {
  when(aw.valid) {
    assert(aw.bits.len === 0.U, "Pbus burst not supported")
    assert(aw.bits.lock === false.B, "Pbus exclusive access not supported")
  }
  
  when(ar.valid) {
    assert(ar.bits.len === 0.U, "Pbus burst not supported")
    assert(ar.bits.lock === false.B, "Pbus exclusive access not supported")
  }
}
