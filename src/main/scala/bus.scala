package armleocpu

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import armleocpu.utils._

class BusParams(
  val data_bytes: Int = 4
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

object burst_t extends ChiselEnum {
    val FIXED = "b00".U(2.W)
    val INCR  = "b01".U(2.W)
    val WRAP  = "b10".U(2.W)
}


class ax_t(cp: CoreParams) extends Bundle {
  val valid   = Output(Bool())
  val ready   = Input (Bool())
  val addr    = Output(SInt((cp.archParams.apLen * 8).W)) // address for the transaction, should be burst aligned if bursts are used
  val size    = Output(UInt(3.W)) // size of data beat in bytes, set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
  val len     = Output(UInt(8.W)) // number of data beats minus one in burst: max 255 for incrementing, 15 for wrapping
  val lock    = Output(Bool()) // set to 1 for exclusive access
}

class w_t(cp: CoreParams) extends Bundle {
  val valid   = Output(Bool())
  val ready   = Input(Bool())
  val data    = Output(UInt((cp.bp.data_bytes * 8).W))
  val strb    = Output(UInt((cp.bp.data_bytes).W))
  val last    = Output(Bool())
}

class b_t(cp: CoreParams) extends Bundle {
  val valid   = Input(Bool())
  val ready   = Output(Bool())
  val resp    = Input(UInt(2.W))
}

class r_t(cp: CoreParams) extends Bundle {
  val valid   = Input(Bool())
  val ready   = Output(Bool())
  val data    = Input(UInt((cp.bp.data_bytes * 8).W))
  val last    = Input(Bool())
  val resp    = Input(UInt(2.W))
}


class ibus_t(cp: CoreParams) extends Bundle {
  val ar  = new ax_t(cp)
  val r   = new r_t(cp)
}

class dbus_t(cp: CoreParams) extends ibus_t(cp = cp) {
  val aw  = new ax_t(cp)
  val w   = new w_t(cp)
  val b   = new b_t(cp)
}
