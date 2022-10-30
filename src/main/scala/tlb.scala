package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum


object TlbConsts {
  val apLen = 34
  val avLen = 32
  val pgoff_len = 12
  val vtag_len = avLen - pgoff_len
  val ptag_len = apLen - pgoff_len
}

import TlbConsts._
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

object tlb_cmd extends ChiselEnum {
  val none, resolve, invalidate_all, write = Value
}

class tlb_data_t extends Bundle {
  val meta = new tlbmeta_t
  val ptag = UInt(apLen.W)
}

// TLB Command bus
class TLB_S0 extends Bundle {
  // Command for TLB
  val cmd = Input(chiselTypeOf(tlb_cmd.write))

  val virt_address = Input(UInt(vtag_len.W))
  
  val write_data = Input(new tlb_data_t)
}

// Output stage of TLB
// Only valid for one cycle because it uses memory units,
// which keep the output valid only one cycle
class TLB_S1 extends Bundle {
  val miss = Output(Bool())

  val read_data = Output(new tlb_data_t)
}

class TLB(ways: Int, entries: Int) extends Module {
  
  /**************************************************************************/
  /* Input/Output                                                           */
  /**************************************************************************/
  val s0 = IO(new TLB_S0())
  val s1 = IO(new TLB_S1())

  /**************************************************************************/
  /* Parameters/constants                                                   */
  /**************************************************************************/
  require(entries >= 0)
  require(isPowerOfTwo(entries))

  require(ways >= 2)
  require(isPowerOfTwo(ways))

  val entries_index_width = log2Ceil(entries)

  // Parameter based calculations
  val virtual_address_width = vtag_len - entries_index_width
  val ways_clog2 = log2Ceil(ways)
  require(virtual_address_width > 0)

  /**************************************************************************/
  /* Shorthand functions                                                    */
  /**************************************************************************/
  def resolve(vaddr: UInt) {
    s0.cmd := tlb_cmd.resolve
    s0.virt_address := vaddr(avLen, 12)
  }

  def invalidate_all() {
    s0.cmd := tlb_cmd.invalidate_all
  }

  def write(vaddr: UInt, paddr: UInt, meta: tlbmeta_t) {
    s0.cmd              := tlb_cmd.write
    s0.virt_address     := vaddr(avLen, 12)
    s0.write_data.ptag  := paddr(apLen, 12)
    s0.write_data.meta  := meta
  }

  
  /**************************************************************************/
  /* Decomposition the virtual address                                      */
  /**************************************************************************/
  val s0_index = s0.virt_address(entries_index_width-1, 0)
  val s0_vtag = s0.virt_address(vtag_len-1, entries_index_width)

  /**************************************************************************/
  /* TLB Storage and state                                                  */
  /**************************************************************************/
  val entry_valid                         = Vec         (entries, Vec(ways, Bool()))
  val entry_meta_perm                     = SyncReadMem (entries, Vec(ways, new tlbpermissionmeta_t))
  val entry_vtag                          = SyncReadMem (entries, Vec(ways, UInt(vtag_len.W)))
  val entry_ptag                          = SyncReadMem (entries, Vec(ways, UInt(ptag_len.W)))
  
  // Registers inputs for use in second cycle
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

  

  for(i <- 0 until ways) {
    entry_valid()
    
    accesstag_permissions_storage(i).io.address := s0_index
    vtag_storage(i).io.address := s0_index
    ptag_storage(i).io.address := s0_index

    // Data is read only when requested
    accesstag_permissions_storage(i).io.read := s0_resolve
    vtag_storage(i).io.read := s0_resolve
    ptag_storage(i).io.read := s0_resolve
    
    // Write bus
    // Accesstag is modified for writes and invalidations
    // PTAG/VTAG is not modified in invalidate_all, because valid bit is set to zero.
    // This reduces power consumption because less bit transition
    accesstag_permissions_storage(i).io.write := s0_invalidate_all || (s0_write && victim_way === i.U)
    vtag_storage(i).io.write := (s0_write && victim_way === i.U)
    ptag_storage(i).io.write := (s0_write && victim_way === i.U)

    accesstag_permissions_storage(i).io.write_data(0) := Mux(s0_invalidate_all, 0.U(8.W), s0.access_permissions_tag_input)
    vtag_storage(i).io.write_data(0) := s0_vtag // Vtag is written value from input
    ptag_storage(i).io.write_data(0) := s0.ptag_input // We write ptag input to memory for write requests

    accesstag_permissions_storage(i).io.write_mask := 1.U
    vtag_storage(i).io.write_mask := 1.U
    ptag_storage(i).io.write_mask := 1.U
  }

  s1.access_permissions_tag_output := accesstag_permissions_storage(0).io.read_data(0)
  s1.ptag_output := ptag_storage(0).io.read_data(0)
  s1.miss := true.B
  for(i <- 0 until ways) {
    when((accesstag_permissions_storage(i).io.read_data(0)(0) === 1.U) && (s1_vtag === vtag_storage(i).io.read_data(0))) {
      // hit
      s1.miss := false.B
      s1.access_permissions_tag_output := accesstag_permissions_storage(i).io.read_data(0)
      s1.ptag_output := ptag_storage(i).io.read_data(0)
    }.otherwise {
      // miss
      s1.miss := true.B
    }
  }
  
}
