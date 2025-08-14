package armleocpu

import chisel3._
import chisel3.util._


class L2_TlbParams(
  val giga:AssociativeMemoryParameters = new AssociativeMemoryParameters(64, 4),
  val mega:AssociativeMemoryParameters = new AssociativeMemoryParameters(16, 8),
  val kilo:AssociativeMemoryParameters = new AssociativeMemoryParameters(4, 16),
) {
  require(log2Ceil(kilo.sets) <= 9)
  require(log2Ceil(mega.sets) <= 18)
  require(log2Ceil(giga.sets) <= 27)
}

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

// TODO: Make this an abstract with vpn being an UInt
// TODO: Make the giga/mega/kilo pages
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



class TlbIO[T <: Data](t: T, p: AssociativeMemoryParameters) extends AssociativeMemoryIO(t = t, p = p) {
  import p._
  
  // TODO: Add the hit io in the response
}

class Tlb[T <: tlb_entry_t](
  // Primary parameters
  t: T,
  p: AssociativeMemoryParameters,
  ccx: CCXParams
) {
  // TODO: Add the underlying memory logic
  // TODO: Add the hit calculation logic
}
