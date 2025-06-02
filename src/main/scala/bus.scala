package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._

object bus_const_t extends ChiselEnum {
    val OKAY   = "b00".U(2.W)
    val EXOKAY = "b01".U(2.W)
    val SLVERR = "b10".U(2.W)
    val DECERR = "b11".U(2.W)

    val OP_READ   = 1.U(8.W)
    val OP_WRITE  = 2.U(8.W)
    val OP_FLUSH  = 3.U(8.W)

    // Coherent request
    val CACHE_READ_SHARED  = 1.U(8.W) // 
    val CACHE_READ_UNIQUE  = 2.U(8.W) // Read with intention to write
    val CACHE_WRITEBACK    = 3.U(8.W) // Writeback. L1 still holds the line
    val CACHE_EVICT_DIRTY  = 4.U(8.W) // Evict request
    val CACHE_EVICT_CLEAN  = 5.U(8.W) // Evict request
    val CACHE_FLUSH        = 6.U(8.W) // L3 cache has to flush itself before returning anything

    // Coherent snoops
    val SNOOP_READ_UNIQUE  = 1.U(8.W)
    val SNOOP_READ_SHARED  = 2.U(8.W)
    val SNOOP_INVALIDATE   = 3.U(8.W)
}


class ax_payload_t(ccx: CCXParams, busBytes: Int) extends Bundle {
  val addr    = Output(SInt((ccx.apLen).W)) // address for the transaction, should be burst aligned if bursts are used
  val op      = Output(UInt(8.W))
  val data    = Output(UInt((busBytes * 8).W))
  val strb    = Output(UInt((busBytes).W))
}


class response_t(busBytes: Int) extends Bundle {
  val data    = Input(UInt((busBytes * 8).W))
  val resp    = Input(UInt(2.W))
}


// Cache coherency:

class ac_payload_t(ccx: CCXParams) extends Bundle {
  val addr    = Input(SInt((ccx.apLen).W))
  val snoop   = Input(UInt(8.W))
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
