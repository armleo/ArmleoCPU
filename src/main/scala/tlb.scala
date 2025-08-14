package armleocpu

import chisel3._
import chisel3.util._




class L1_TlbParams(
  val sets:Int = 4,
  val ways:Int = 2,
  val flushLatency: Int = 0
) {
}

class L2_TlbParams(
  val gigapage_sets:Int = 64,
  val megapage_sets:Int = 16,
  val kilopage_sets:Int = 4,

  val gigapage_ways:Int = 4,
  val megapage_ways:Int = 8,
  val kilopage_ways:Int = 16,

  val gigapage_flushLatency:Int = 2,
  val megapage_flushLatency:Int = 2,
  val kilopage_flushLatency:Int = 2,
) {
  require(log2Ceil(kilopage_sets) <= 9)
  require(log2Ceil(megapage_sets) <= 18)
  require(log2Ceil(gigapage_sets) <= 27)
}

/**************************************************************************/
/* Input/Output Bundles                                                   */
/**************************************************************************/

class tlb_accessbits_t extends Bundle {
  val dirty   = Bool()
  val access  = Bool()
  val global  = Bool()
  val user    = Bool()
  val execute = Bool()
  val write   = Bool()
  val read    = Bool()

  // Trying to read this entry resulted in accessfault
  val accessfault = Bool()

  // Trying to resolve this entry resulted in pagefault
  val pagefault = Bool()
}


// This bundle is kept in the memory,
//    while valid bit is kept in registers due to flush invalidating every entry

class tlb_entry_t(ccx: CCXParams, lvl: Int) extends tlb_accessbits_t {
  require(lvl <= 2)
  require(lvl >= 0)
  // The accessbits are defined in tlb_accessbits_t we extends
  val vpn =
    if(lvl == 2)        UInt(9.W)
    else if (lvl == 1)  UInt(18.W)
    else                UInt(24.W)
  
  val ppn = UInt(44.W) // We keep the 44 bits as it can be a pointer to a subtree
  
  val rvfi_ptes = Vec(3, UInt(ccx.PTESIZE.W))

  def is_leaf(): Bool = read || execute
  def va_match(va: UInt): Bool = if(lvl == 2) vpn === va(38,30) else if(lvl == 1) vpn === va(38,21) else vpn === va(38, 12)
}

