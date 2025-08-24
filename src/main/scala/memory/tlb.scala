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

class TlbAccessBits extends Bundle {
  val dirty   = Bool()
  val access  = Bool()
  val global  = Bool()
  val user    = Bool()
  val execute = Bool()
  val write   = Bool()
  val read    = Bool()

  // Trying to read this entry resulted in accessFault
  val accessFault = Bool()

  // Trying to resolve this entry resulted in pageFault
  val pageFault = Bool()
}


// This bundle is kept in the memory,
//    while valid bit is kept in registers due to flush invalidating every entry

abstract class TlbEntry(vpnWidth: Int)(implicit val ccx: CCXParams) extends TlbAccessBits {
  val ppn = UInt(44.W) // We keep the entire 44 bits as it can be a pointer to a subtree
  val rvfiPtes: Vec[UInt]
  val vpn: UInt

  def isLeaf(): Bool = read || execute
  def vaddrMatch(vaddr: UInt): Bool
}


class TlbGigaEntry(implicit ccx: CCXParams) extends TlbEntry(9) {
  val vpn = UInt(9.W)
  val rvfiPtes = Vec(1, UInt(ccx.PTESIZE.W))
  def vaddrMatch(vaddr: UInt): Bool = vpn === vaddr(38,30)
}

class TlbMegaEntry(implicit ccx: CCXParams) extends TlbEntry(18) {
  val vpn = UInt(18.W)
  val rvfiPtes = Vec(2, UInt(ccx.PTESIZE.W))
  def vaddrMatch(vaddr: UInt): Bool = vpn === vaddr(38,21)
}

class TlbKiloEntry(implicit ccx: CCXParams) extends TlbEntry(24) {
  val vpn = UInt(24.W)
  val rvfiPtes = Vec(3, UInt(ccx.PTESIZE.W))
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
  
  val req = Valid(new TlbIOReq(t = t, p = p))
  val resp = new TlbIOResp(t = t, p = p)
}

class Tlb[T <: TlbEntry](
  // Primary parameters
  t: T,
  p: AssociativeMemoryParameters,
  
)(implicit ccx: CCXParams) extends CCXModule {

  val io = new TlbIO(t = t, p = p)

  val assocMem = Module(new AssociativeMemory(t = t, p = p))
  io.req <> assocMem.io.req
  io.resp <> assocMem.io.resp

  assocMem.io.req.bits.idx := io.req.bits.vaddr(ccx.apLen, 12)
  
  val s1_vaddr = RegEnable(io.req.bits.vaddr, io.req.valid)

  io.resp.hits    := assocMem.io.resp.readEntry.map(_.vaddrMatch(s1_vaddr))
  io.resp.hit     := io.resp.hits.asUInt.orR
  io.resp.hitIdx  := PriorityEncoder(io.resp.hits)
}


class L2TlbIO[T <: TlbEntry](t: T, p: AssociativeMemoryParameters, n: Int)(implicit ccx: CCXParams) extends Bundle {
  val req  = Vec(n, Decoupled(new TlbIOReq(t = t, p = p)))
  val resp = Vec(n, new TlbIOResp(t = t, p = p))
}

class L2Tlb[T <: TlbEntry](
  t: T,
  p: AssociativeMemoryParameters,
  n: Int // Number of request/response ports
)(implicit ccx: CCXParams) extends CCXModule {

  val io = IO(new L2TlbIO(t = t, p = p, n = n))

  // Shared TLB instance
  val tlb = Module(new Tlb(t = t, p = p))

  // Round-robin arbiter for requests
  val rrArb = Module(new RRArbiter(new TlbIOReq(t = t, p = p), n))
  for (i <- 0 until n) {
    rrArb.io.in(i) <> io.req(i)
  }

  // Connect selected request to TLB
  tlb.io.req.valid := rrArb.io.out.valid
  tlb.io.req.bits  := rrArb.io.out.bits
  rrArb.io.out.ready := true.B

  // Broadcast TLB response to all ports
  for (i <- 0 until n) {
    io.resp(i) := tlb.io.resp
  }
}