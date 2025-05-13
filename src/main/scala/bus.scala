package armleocpu

import chisel3._
import chisel3.util._
import armleocpu.utils._
import chisel3.experimental.dataview._

class BusParams(
  val data_bytes: Int = 8
) {
  // FIXME: Add the check data_bytes to be multipleof2
  require(isPositivePowerOfTwo(data_bytes))
}


object bus_resp_t extends ChiselEnum {
    val OKAY   = "b00".U(2.W)
    val EXOKAY = "b01".U(2.W)
    val SLVERR = "b10".U(2.W)
    val DECERR = "b11".U(2.W)
}


class ax_payload_t(cp: CoreParams) extends Bundle {
  val addr    = Output(SInt((cp.apLen).W)) // address for the transaction, should be burst aligned if bursts are used
  val size    = Output(UInt(3.W)) // size of data beat in bytes, set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
  val len     = Output(UInt(8.W)) // number of data beats minus one in burst: max 255 for incrementing, 15 for wrapping
  val lock    = Output(Bool()) // set to 1 for exclusive access
}

class ax_t(cp: CoreParams) extends DecoupledIO(new ax_payload_t(cp)) {
}


class w_payload_t(cp: CoreParams) extends Bundle {
  val data    = Output(UInt((cp.bp.data_bytes * 8).W))
  val strb    = Output(UInt((cp.bp.data_bytes).W))
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
  val data    = Input(UInt((cp.bp.data_bytes * 8).W))
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
