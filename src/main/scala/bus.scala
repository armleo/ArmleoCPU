package armleocpu

import chisel3._
import chisel3.util._

import Consts._

class busParams(
  val addrWidthBits: Int, val dataWidthBits: Int, val idBits: Int = 1
) {
  require((dataWidthBits % 8) == 0)
  require(idBits >= 1)
}

class ax_t(p: busParams) extends Bundle {
  val addr    = (UInt(p.addrWidthBits.W)) // address for the transaction, should be burst aligned if bursts are used
  val size    = (UInt(3.W)) // size of data beat in bytes, set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
  val len     = (UInt(8.W)) // number of data beats minus one in burst: max 255 for incrementing, 15 for wrapping
  val burst   = (UInt(2.W)) // burst mode: 0 for fixed, 1 for incrementing, 2 for wrapping
  val id      = (UInt(p.idBits.W)) // transaction ID for multiple outstanding requests
  val lock    = (Bool()) // set to 1 for exclusive access
}

class w_t(p: busParams) extends Bundle {
  val data    = (UInt(p.dataWidthBits.W))
  val strb    = (UInt((p.dataWidthBits/8).W))
  val last    = (Bool())
}

class b_t(p: busParams) extends Bundle {
  val id      = (UInt(p.idBits.W))
  val resp    = (UInt(2.W))
}

class r_t(p: busParams) extends Bundle {
  val data    = (UInt(p.dataWidthBits.W))
  val id      = (UInt(p.idBits.W))
  val last    = (Bool())
  val resp    = (UInt(2.W))
}


class ibus_t(idBits: Int = 1, dbus_len: Int = 256) extends Bundle{
  val p = new busParams(xLen, dbus_len, idBits)
  
  val ar  = Decoupled(new ax_t(p))
  val r   = Flipped(Decoupled(new r_t(p)))
}

class dbus_t(idBits: Int = 1, dbus_len: Int = 256) extends ibus_t(idBits, dbus_len) {
  val aw  = Decoupled(new ax_t(p))
  val w   = Decoupled(new w_t(p))
  val b   = Flipped(Decoupled(new b_t(p)))
}
