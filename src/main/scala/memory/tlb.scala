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

abstract class TlbEntry(vpnWidth: Int)(implicit val ccx: CCXParams) extends tlb_accessbits_t {
  // The accessbits are defined in tlb_accessbits_t we extends
  val ppn = UInt(44.W) // We keep the 44 bits as it can be a pointer to a subtree
  
  val rvfiPtes = Vec(3, UInt(ccx.PTESIZE.W))
  def isLeaf(): Bool = read || execute


  val vpn: UInt
  def vaddrMatch(vaddr: UInt): Bool
}


class TlbGigaEntry(implicit ccx: CCXParams) extends TlbEntry(9) {
  val vpn = UInt(9.W)
  def vaddrMatch(vaddr: UInt): Bool = vpn === vaddr(38,30)
}

class TlbMegaEntry(implicit ccx: CCXParams) extends TlbEntry(18) {
  val vpn = UInt(18.W)
  def vaddrMatch(vaddr: UInt): Bool = vpn === vaddr(38,21)
}

class TlbKiloEntry(implicit ccx: CCXParams) extends TlbEntry(24) {
  val vpn = UInt(24.W)
  def vaddrMatch(vaddr: UInt): Bool = vpn === vaddr(38, 12)
}


class TlbIOReq[T <: TlbEntry](t: T, p: AssociativeMemoryParameters)(implicit val ccx: CCXParams) extends AssociativeMemoryReq(t = t, p = p) {
  val vaddr       = Input(UInt(ccx.apLen.W))
}

class TlbIOResp[T <: TlbEntry](t: T, p: AssociativeMemoryParameters) extends AssociativeMemoryResp(t = t, p = p) {
  import p._

  val hits = Output(Vec(ways, Bool()))
  val hit  = Output(Bool())
  val hitIdx = Output(UInt(log2Ceil(ways).W))
}

class TlbIO[T <: TlbEntry](t: T, p: AssociativeMemoryParameters)(implicit val ccx: CCXParams) extends Bundle {
  import p._
  
  val req = new TlbIOReq(t = t, p = p)
  val res = new TlbIOResp(t = t, p = p)
}

class Tlb[T <: TlbEntry](
  // Primary parameters
  t: T,
  p: AssociativeMemoryParameters,
  
)(implicit val ccx: CCXParams) {

  val io = new TlbIO(t = t, p = p)

  val assocMem = new AssociativeMemory(t = t, p = p)
  io.req <> assocMem.io.req
  io.res <> assocMem.io.resp

  assocMem.io.req.idx := io.req.vaddr(ccx.apLen, 12)
  // FIXME: Add the hit calculation logic
}
