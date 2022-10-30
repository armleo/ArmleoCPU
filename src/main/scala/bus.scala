package armleocpu

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class busParams(
  val addrWidthBits: Int, val dataWidthBits: Int, val idBits: Int = 1
) {
  require((dataWidthBits % 8) == 0)
  require(idBits >= 1)
}

object amoop_t extends ChiselEnum {
    val NONE, ADD, AND, XOR, OR, MAX, MAXU, MIN, MINU, SWAP  = Value
}


class ax_t(p: busParams) extends Bundle {
  val valid   = Output(Bool())
  val ready   = Input (Bool())
  val addr    = Output(SInt(p.addrWidthBits.W)) // address for the transaction, should be burst aligned if bursts are used
  val size    = Output(UInt(3.W)) // size of data beat in bytes, set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
  val len     = Output(UInt(8.W)) // number of data beats minus one in burst: max 255 for incrementing, 15 for wrapping
  val burst   = Output(UInt(2.W)) // burst mode: 0 for fixed, 1 for incrementing, 2 for wrapping
  val id      = Output(UInt(p.idBits.W)) // transaction ID for multiple outstanding requests
  val lock    = Output(Bool()) // set to 1 for exclusive access
  val amoop   = Output(chiselTypeOf(amoop_t.ADD))
}

class w_t(p: busParams) extends Bundle {
  val valid   = Output(Bool())
  val ready   = Input(Bool())
  val data    = Output(UInt(p.dataWidthBits.W))
  val strb    = Output(UInt((p.dataWidthBits/8).W))
  val last    = Output(Bool())
}

class b_t(p: busParams) extends Bundle {
  val valid   = Input(Bool())
  val ready   = Output(Bool())
  val id      = Input(UInt(p.idBits.W))
  val resp    = Input(UInt(2.W))
}

class r_t(p: busParams) extends Bundle {
  val valid   = Input(Bool())
  val ready   = Output(Bool())
  val data    = Input(UInt(p.dataWidthBits.W))
  val id      = Input(UInt(p.idBits.W))
  val last    = Input(Bool())
  val resp    = Input(UInt(2.W))
}


class ibus_t(val c: coreParams) extends Bundle {
  val p = new busParams(c.apLen, c.ibus_data_bytes * 8, c.idWidth)
  
  val ar  = new ax_t(p)
  val r   = new r_t(p)
}

class dbus_t(c: coreParams) extends Bundle {
  val p = new busParams(c.apLen, c.dbus_data_bytes * 8, c.idWidth)
  
  val ar  = new ax_t(p)
  val r   = new r_t(p)
  val aw  = new ax_t(p)
  val w   = new w_t(p)
  val b   = new b_t(p)
}
