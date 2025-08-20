package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._

object bus_const_t extends ChiselEnum {
    val OKAY   = "b00".U(8.W)
    val EXOKAY = "b01".U(8.W)
    val SLVERR = "b10".U(8.W)
    val DECERR = "b11".U(8.W)

    val OP_READ         = 1.U(8.W)
    val OP_WRITETHROUGH = 2.U(8.W)
    val OP_ATOMIC_READ  = 4.U(8.W)
    val OP_ATOMIC_WRITE = 5.U(8.W)
}


class ax_payload_t(busBytes: Int)(implicit ccx: CCXParams) extends Bundle {
  val addr    = Output(UInt((ccx.apLen).W)) // address for the transaction, should be burst aligned if bursts are used
  val op      = Output(UInt(8.W))
  val data    = Output(UInt((busBytes * 8).W))
  val strb    = Output(UInt((busBytes).W))
}


class response_t(busBytes: Int) extends Bundle {
  val data    = Input(UInt((busBytes * 8).W))
  val resp    = Input(UInt(8.W))
}



class dbus_t(coherency: Boolean = false)(implicit ccx: CCXParams) extends Bundle {
  val ax  = DecoupledIO(new ax_payload_t(busBytes = ccx.busBytes))
  val r   = Flipped(DecoupledIO(new response_t(busBytes = ccx.busBytes)))
}

    /*
    val EXCLUSIVE_OKAY  = 4.U(8.W)
    val DIRTY_OKAY      = 5.U(8.W) // Implies exclusivity
    val SHARED_OKAY     = 6.U(8.W) 
    val INVALID_OKAY    = 7.U(8.W) // Returned on C bus on invalidation
    */


  /*
    // Coherent request
    val CACHE_READ_SHARED  = 4.U(8.W) // 
    val CACHE_READ_UNIQUE  = 5.U(8.W) // Read with intention to write
    val CACHE_MAKE_UNIQUE  = 6.U(8.W) // Ask the peers to release their instances of the cache
    val CACHE_WRITEBACK    = 7.U(8.W) // Writeback. L1 still holds the line
    val CACHE_EVICT_DIRTY  = 8.U(8.W) // Evict request
    val CACHE_EVICT_CLEAN  = 9.U(8.W) // Evict request
    val CACHE_FLUSH        = 10.U(8.W) // L3 cache has to flush itself before returning anything

    // Coherent snoops
    val SNOOP_READ_UNIQUE  = 101.U(8.W)
    val SNOOP_READ_SHARED  = 102.U(8.W)
    val SNOOP_INVALIDATE   = 103.U(8.W)
    */
    // We make sure that these do not overlap.
    // This can be used to then make sure that no request goes from coherent bus to non coherent

/*
// Cache coherency:

class ac_payload_t(implicit val ccx: CCXParams) extends Bundle {
  val addr    = Input(SInt((ccx.apLen).W))
  val snoop   = Input(UInt(8.W))
}

class c_payload_t(busBytes: Int) extends Bundle {
  val resp    = Output(UInt(8.W))
  val data    = Output(UInt((busBytes * 8).W))
}
*/

/*
class corebus_t(implicit val ccx: CCXParams) extends dbus_t(ccx = ccx, coherency = true) {
  val ac = Flipped(DecoupledIO(new ac_payload_t))
  val c = DecoupledIO(new c_payload_t(busBytes = ccx.busBytes))
}
*/

class pbus_t(implicit ccx: CCXParams) extends dbus_t {
  
}

// TODO: Add the ACK signal
// TODO: Add the operations:

// ReadShared <- Read allocating
// CleanUnique <- To update to writable from read allocated
// ReadUnique <- for write allocating

// WriteClean to not evict and write
// WriteBack to evict and write

// Cache uses:
// CleanShared to respond to interconnect on requests for ReadShared
// CleanInvalid to respond to interconnect on requests for ReadUnique/CleanUnique

