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
  val ptag = UInt(c.ptag_len.W)
}

/**************************************************************************/
/* TLB Module                                                             */
/* ways/entries are not extracted from coreParams,                        */
/* because it depends on the itlb parameter                               */
/**************************************************************************/

class TLB(is_itlb: Boolean, c: coreParams) extends Module {
  /**************************************************************************/
  /* Parameters from coreParams                                             */
  /**************************************************************************/

  var ways      = c.dtlb_ways
  var entries   = c.dtlb_entries

  if(is_itlb) {
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
  
  // Command for TLB
  val s0 = IO(new Bundle {
    
    val cmd = Input(chiselTypeOf(tlb_cmd.write))
    val virt_address_top = Input(UInt(c.vtag_len.W))
    val write_data = Input(new tlb_data_t(c))
  })

  // Output stage of TLB
  // Only valid for one cycle because it uses memory units,
  // which keep the output valid only one cycle
  val s1 = IO(new Bundle {
    val miss = Output(Bool())
    val hit = Output(Bool())

    val read_data = Output(new tlb_data_t(c))
  })

  
  /**************************************************************************/
  /* Command decoding                                                       */
  /**************************************************************************/
  // Command decoding. CMD is used to guarantee that only one cmd is executed at the same time
  val s0_resolve          = s0.cmd === tlb_cmd.resolve
  val s0_invalidate_all   = s0.cmd === tlb_cmd.invalidate_all
  val s0_write            = s0.cmd === tlb_cmd.write

  /**************************************************************************/
  /* Shorthand functions                                                    */
  /**************************************************************************/
  def resolve(vaddr: UInt) = {
    s0.cmd          := tlb_cmd.resolve
    s0.virt_address_top := vaddr(c.avLen, 12)
  }

  def invalidate_all() = {
    s0.cmd := tlb_cmd.invalidate_all
  }

  def write(vaddr: UInt, paddr: UInt, meta: tlbmeta_t) = {
    s0.cmd              := tlb_cmd.write
    s0.virt_address_top     := vaddr(c.avLen, 12)
    s0.write_data.ptag  := paddr(c.apLen, 12)
    s0.write_data.meta  := meta
  }

  
  /**************************************************************************/
  /* Decomposition the virtual address                                      */
  /**************************************************************************/
  val s0_index = s0.virt_address_top(entries_index_width-1, 0)
  val s0_vtag = s0.virt_address_top(c.vtag_len-1, entries_index_width)

  /**************************************************************************/
  /* TLB Storage and state                                                  */
  /**************************************************************************/
  val entry_valid                         = Reg     (Vec(entries, Vec(ways, Bool())))
  val entry_meta_perm                     = SyncReadMem (entries, Vec(ways, new tlbpermissionmeta_t))
  val entry_vtag                          = SyncReadMem (entries, Vec(ways, UInt(c.vtag_len.W)))
  val entry_ptag                          = SyncReadMem (entries, Vec(ways, UInt(c.ptag_len.W)))
  val entry_meta_perm_rdwr                = entry_meta_perm (s0_index)
  val entry_vtag_rdwr                     = entry_vtag      (s0_index)
  val entry_ptag_rdwr                     = entry_ptag      (s0_index)
  
  // Registers inputs for use in second cycle, to compute miss/hit logic
  val s1_vtag     = RegEnable(s0_vtag, s0_resolve)
  
  // Keeps track of victim
  val victim_bits = if (ways_clog2 > 0) ways_clog2 else 1
  val victim_way  = RegInit(0.U(victim_bits.W))

  
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

  val s1_entries_valid       = RegNext(entry_valid(s0_index))
  val s1_entries_meta_perm   = VecInit.tabulate(ways) {way:Int => entry_meta_perm_rdwr(way)}
  val s1_entries_vtag        = VecInit.tabulate(ways) {way:Int => entry_vtag_rdwr     (way)}
  val s1_entries_ptag        = VecInit.tabulate(ways) {way:Int => entry_ptag_rdwr     (way)}

  /**************************************************************************/
  /* Write logic                                                            */
  /**************************************************************************/

  when(s0_write) {
    entry_valid (s0_index)(victim_way) := s0.write_data.meta.valid
    entry_meta_perm_rdwr  (victim_way) := s0.write_data.meta.perm
    entry_vtag_rdwr       (victim_way) := s0_vtag
    entry_ptag_rdwr       (victim_way) := s0.write_data.ptag
  }

  /**************************************************************************/
  /* Invalidate logic                                                       */
  /**************************************************************************/

  when(s0_invalidate_all || reset.asBool()) {
    // TODO: Invalidate all replace with invalidate only some
    entry_valid.foreach {f => f := 0.U(ways.W).asBools()}
  }

  /**************************************************************************/
  /* Output logic                                                           */
  /**************************************************************************/
  
  s1.read_data.meta.valid := s1_entries_valid(0)
  s1.read_data.meta.perm  := s1_entries_meta_perm(0)
  s1.read_data.ptag       := s1_entries_ptag(0)

  
  s1.miss := true.B
  
  for(i <- 0 until ways) {
    when((s1_entries_valid(i) === true.B) && (s1_vtag === s1_entries_vtag(i))) {
      /**************************************************************************/
      /* Hit                                                                    */
      /**************************************************************************/
      
      s1.miss                 := false.B
      s1.read_data.meta.valid := s1_entries_valid(i)
      s1.read_data.meta.perm  := s1_entries_meta_perm(i)
      s1.read_data.ptag       := s1_entries_ptag(i)
    }
  }

  s1.hit := s1.miss
  
}
