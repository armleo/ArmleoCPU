package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum


import armleocpu.utils._


class tlbpermissionmeta_t extends Bundle {
  val dirty   = Bool()
  val access  = Bool()
  val global  = Bool()
  val user    = Bool()
  val execute = Bool()
  val write   = Bool()
  val read    = Bool()
}

class tlbmeta_t extends Bundle {
  val perm    = new tlbpermissionmeta_t
  val valid   = Bool()
}

/**************************************************************************/
/* Input/Output Bundles                                                   */
/**************************************************************************/

object tlb_cmd extends ChiselEnum {
  val none, resolve, invalidate_all, write = Value
}

class tlb_data_t(c: coreParams) extends Bundle {
  val meta = new tlbmeta_t
  val ptag = UInt(c.apLen.W)
}

// TLB Command bus
class TLB_S0(c: coreParams) extends Bundle {
  // Command for TLB
  val cmd = Input(chiselTypeOf(tlb_cmd.write))

  val virt_address = Input(UInt(c.vtag_len.W))
  
  val write_data = Input(new tlb_data_t(c))
}

// Output stage of TLB
// Only valid for one cycle because it uses memory units,
// which keep the output valid only one cycle
class TLB_S1(c: coreParams) extends Bundle {
  val miss = Output(Bool())
  val hit = Output(Bool())

  val read_data = Output(new tlb_data_t(c))
}

/**************************************************************************/
/* TLB Module                                                             */
/* ways/entries are not extracted from core, because it depends on the                                                             */
/**************************************************************************/

class TLB(itlb: Boolean, c: coreParams) extends Module {
  /**************************************************************************/
  /* Parameters from coreParams                                             */
  /**************************************************************************/

  var ways      = c.dtlb_ways
  var entries   = c.dtlb_entries

  if(itlb) {
    ways        = c.itlb_ways
    entries     = c.itlb_entries
  }

  
  /**************************************************************************/
  /* Parameters/constants                                                   */
  /**************************************************************************/
  require(entries >= 0)
  require(isPowerOfTwo(entries))

  require(ways >= 2)
  require(isPowerOfTwo(ways))

  val entries_index_width = log2Ceil(entries)

  // Parameter based calculations
  val virtual_address_width = c.vtag_len - entries_index_width
  val ways_clog2 = log2Ceil(ways)
  require(virtual_address_width > 0)

  /**************************************************************************/
  /* Input/Output                                                           */
  /**************************************************************************/
  val s0 = IO(new TLB_S0(c))
  val s1 = IO(new TLB_S1(c))

  /**************************************************************************/
  /* Shorthand functions                                                    */
  /**************************************************************************/
  def resolve(vaddr: UInt) = {
    s0.cmd          := tlb_cmd.resolve
    s0.virt_address := vaddr(c.avLen, 12)
  }

  def invalidate_all() = {
    s0.cmd := tlb_cmd.invalidate_all
  }

  def write(vaddr: UInt, paddr: UInt, meta: tlbmeta_t) = {
    s0.cmd              := tlb_cmd.write
    s0.virt_address     := vaddr(c.avLen, 12)
    s0.write_data.ptag  := paddr(c.apLen, 12)
    s0.write_data.meta  := meta
  }

  
  /**************************************************************************/
  /* Decomposition the virtual address                                      */
  /**************************************************************************/
  val s0_index = s0.virt_address(entries_index_width-1, 0)
  val s0_vtag = s0.virt_address(c.vtag_len-1, entries_index_width)

  /**************************************************************************/
  /* TLB Storage and state                                                  */
  /**************************************************************************/
  val entry_valid                         = Vec         (entries, Vec(ways, Bool()))
  val entry_meta_perm                     = SyncReadMem (entries, Vec(ways, new tlbpermissionmeta_t))
  val entry_vtag                          = SyncReadMem (entries, Vec(ways, UInt(c.vtag_len.W)))
  val entry_ptag                          = SyncReadMem (entries, Vec(ways, UInt(c.ptag_len.W)))
  
  // Registers inputs for use in second cycle, to compute miss/hit logic
  val s1_vtag     = RegEnable(s0_vtag, s0_resolve)
  
  // Keeps track of victim
  val victim_bits = if (ways_clog2 > 0) ways_clog2 else 1
  val victim_way  = RegInit(0.U(victim_bits.W))

  /**************************************************************************/
  /* Command decoding                                                       */
  /**************************************************************************/
  // Command decoding. CMD is used to guarantee that only one cmd is executed at the same time
  val s0_resolve          = s0.cmd === tlb_cmd.resolve
  val s0_invalidate_all   = s0.cmd === tlb_cmd.invalidate_all
  val s0_write            = s0.cmd === tlb_cmd.write

  
  /**************************************************************************/
  /* Victim selection logic                                                 */
  /**************************************************************************/

  if(ways > 0) {
    when(s0_write) {
      // ways may be not power of two, so cap it at that value
      when(victim_way === (ways.U - 1.U)) {
        victim_way := 0.U
      } .otherwise {
        victim_way := victim_way + 1.U
      }
    }
  } else {
    victim_way := 0.U;
  }

  /**************************************************************************/
  /* Read/resolve request logic                                             */
  /**************************************************************************/

  val entries_valid       = RegEnable(  entry_valid(s0_index), s0_resolve)
  val entries_meta_perm   = entry_meta_perm   .read(s0_index, s0_resolve)
  val entries_vtag        = entry_vtag        .read(s0_index, s0_resolve)
  val entries_ptag        = entry_ptag        .read(s0_index, s0_resolve)

  when(s0_write) {
    entries_valid             (s0_index)(victim_way)  :=  s0.write_data.meta.valid
    entry_meta_perm.write     (s0_index,                  Vec(ways, s0.write_data.meta.perm), (1.U << victim_way).asUInt.asBools)
    entry_vtag.write          (s0_index,                  Vec(ways, s0_vtag),                 (1.U << victim_way).asUInt.asBools)
    entry_ptag.write          (s0_index,                  Vec(ways, s0.write_data.ptag),      (1.U << victim_way).asUInt.asBools)
  }

  /**************************************************************************/
  /* Invalidate logic                                                       */
  /**************************************************************************/

  when(s0_invalidate_all) {
    entry_valid.foreach { f => f := 0.U(ways.W)}
  }

  /**************************************************************************/
  /* Output logic                                                           */
  /**************************************************************************/

  s1.read_data.meta.valid := entries_valid(0)
  s1.read_data.meta.perm  := entries_meta_perm(0)
  s1.read_data.ptag       := entries_ptag(0)

  s1.miss := true.B
  for(i <- 0 until ways) {
    when((entries_valid(i) === true.B) && (s1_vtag === entries_vtag(i))) {
      /**************************************************************************/
      /* Hit                                                                    */
      /**************************************************************************/

      s1.miss := false.B
      s1.read_data.meta.valid := entries_valid(i)
      s1.read_data.meta.perm  := entries_meta_perm(i)
      s1.read_data.ptag       := entries_ptag(i)
    }.otherwise {
      /**************************************************************************/
      /* Miss                                                                   */
      /**************************************************************************/
      s1.miss := true.B
    }
  }

  s1.hit := s1.miss
  
}
