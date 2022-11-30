package armleocpu

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import armleocpu.utils._

class BusParams(
  val data_bytes: Int = 4,
  val apLen: Int = 34
) {
  // FIXME: Add the check data_bytes to be multipleof2
  require(isPositivePowerOfTwo(data_bytes))
}


object bus_resp_t extends ChiselEnum {
    val OKAY = "b00".U(2.W)
    val EXOKAY = "b01".U(2.W)
    val SLVERR = "b10".U(2.W)
    val DECERR = "b11".U(2.W)
}

object burst_t extends ChiselEnum {
    val FIXED = "b00".U(2.W)
    val INCR = "b01".U(2.W)
    val WRAP = "b10".U(2.W)
}


class ax_t(p: BusParams) extends Bundle {
  val valid   = Output(Bool())
  val ready   = Input (Bool())
  val addr    = Output(SInt((p.apLen * 8).W)) // address for the transaction, should be burst aligned if bursts are used
  val size    = Output(UInt(3.W)) // size of data beat in bytes, set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
  val len     = Output(UInt(8.W)) // number of data beats minus one in burst: max 255 for incrementing, 15 for wrapping
  val lock    = Output(Bool()) // set to 1 for exclusive access
}

class w_t(p: BusParams) extends Bundle {
  val valid   = Output(Bool())
  val ready   = Input(Bool())
  val data    = Output(UInt((p.data_bytes * 8).W))
  val strb    = Output(UInt((p.data_bytes).W))
  val last    = Output(Bool())
}

class b_t(p: BusParams) extends Bundle {
  val valid   = Input(Bool())
  val ready   = Output(Bool())
  val resp    = Input(UInt(2.W))
}

class r_t(p: BusParams) extends Bundle {
  val valid   = Input(Bool())
  val ready   = Output(Bool())
  val data    = Input(UInt((p.data_bytes * 8).W))
  val last    = Input(Bool())
  val resp    = Input(UInt(2.W))
}


class ibus_t(val p: BusParams) extends Bundle {
  val ar  = new ax_t(p)
  val r   = new r_t(p)
}

class dbus_t(val p: BusParams) extends Bundle {
  val ar  = new ax_t(p)
  val r   = new r_t(p)
  val aw  = new ax_t(p)
  val w   = new w_t(p)
  val b   = new b_t(p)
}
