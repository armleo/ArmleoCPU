package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum


import armleocpu.utils._

class TlbParams(
  val entries:Int = 64,
  val ways:Int = 2
) {
  // FIXME: Add the entries check

}

class tlbmeta_t extends Bundle {
  val dirty   = Bool()
  val access  = Bool()
  val global  = Bool()
  val user    = Bool()
  val execute = Bool()
  val write   = Bool()
  val read    = Bool()
  val valid   = Bool()
}

/**************************************************************************/
/* Input/Output Bundles                                                   */
/**************************************************************************/

object tlb_cmd extends ChiselEnum {
  val none, resolve, invalidate, write = Value
}

class tlb_data_t(ptag_len: Int) extends Bundle {
  val meta = new tlbmeta_t
  val ptag = UInt(ptag_len.W)
}

/**************************************************************************/
/* TLB Module                                                             */
/* ways/entries are not extracted from CoreParams,                        */
/* because it depends on the itlb parameter                               */
/**************************************************************************/

class TLB(verbose: Boolean = true, instName: String = "itlb ", c: CoreParams, tp: TlbParams) extends Module {

  
  val ptag_len = c.archParams.apLen - c.archParams.pgoff_len
  val vtag_len = c.archParams.apLen - c.archParams.pgoff_len

  require(isPositivePowerOfTwo(tp.ways))
  require(isPositivePowerOfTwo(tp.entries))
  /**************************************************************************/
  /* Parameters from CoreParams                                             */
  /**************************************************************************/

  var ways      = tp.ways
  var entries   = tp.entries

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
  /* Input/Output                                                           */
  /**************************************************************************/
  
  // Command for TLB
  val s0 = IO(new Bundle {
    
    val cmd = Input(chiselTypeOf(tlb_cmd.write))
    val virt_address_top = Input(UInt(vtag_len.W))
    val write_data = Input(new tlb_data_t(ptag_len))
  })

  // Output stage of TLB
  // Only valid for one cycle because it uses memory units,
  // which keep the output valid only one cycle
  val s1 = IO(new Bundle {
    val miss = Output(Bool())

    val read_data = Output(new tlb_data_t(ptag_len = ptag_len))
  })

  val cycle = IO(Input(UInt(c.lp.verboseCycleWidth.W)))
  val log = new Logger(c.lp.coreName, instName, verbose, cycle)

  /**************************************************************************/
  /* Command decoding                                                       */
  /**************************************************************************/
  // Command decoding. CMD is used to guarantee that only one cmd is executed at the same time
  val s0_resolve          = s0.cmd === tlb_cmd.resolve
  val s0_write            = s0.cmd === tlb_cmd.write

  /**************************************************************************/
  /* Decomposition the virtual address                                      */
  /**************************************************************************/
  val s0_index = s0.virt_address_top(entries_index_width-1, 0)
  val s0_vtag = s0.virt_address_top(vtag_len-1, entries_index_width)

  /**************************************************************************/
  /* TLB Storage and state                                                  */
  /**************************************************************************/
  val entry_meta                          = SyncReadMem (entries, Vec(ways, UInt((new tlbmeta_t).getWidth.W)))
  val entry_vtag                          = SyncReadMem (entries, Vec(ways, UInt(vtag_len.W)))
  val entry_ptag                          = SyncReadMem (entries, Vec(ways, UInt(ptag_len.W)))
  val entry_meta_rdwr                     = entry_meta      (s0_index)
  val entry_vtag_rdwr                     = entry_vtag      (s0_index)
  val entry_ptag_rdwr                     = entry_ptag      (s0_index)
  
  // TODO: Add PTE storage for RVFI

  // Registers inputs for use in second cycle, to compute miss/hit logic
  val s1_vtag     = RegNext(s0_vtag)
  
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
  // TODO: Merge this cells?
  val s1_entries_meta        = VecInit.tabulate(ways) {way:Int => entry_meta_rdwr     (way)}
  val s1_entries_vtag        = VecInit.tabulate(ways) {way:Int => entry_vtag_rdwr     (way)}
  val s1_entries_ptag        = VecInit.tabulate(ways) {way:Int => entry_ptag_rdwr     (way)}

  /**************************************************************************/
  /* Write logic                                                            */
  /**************************************************************************/

  when(s0_write) {
    log("Write s0_index=0x%x, meta=0x%x, vtag=0x%x, ptag=0x%x", s0_index, s0.write_data.meta.asUInt, s0_vtag, s0.write_data.ptag)
    entry_meta_rdwr       (victim_way) := s0.write_data.meta.asUInt
    entry_vtag_rdwr       (victim_way) := s0_vtag
    entry_ptag_rdwr       (victim_way) := s0.write_data.ptag
  }

  /**************************************************************************/
  /* Invalidate logic                                                       */
  /**************************************************************************/

  when(s0.cmd === tlb_cmd.invalidate) {
    log("Invalidate s0_index=0x%x\n", s0_index)
    entry_meta_rdwr.foreach {
      f => f := 0.U // We dont need, because the syncreadmem cant use aggregate types .asTypeOf(new tlbmeta_t)
    }
  }

  

  /**************************************************************************/
  /* Output logic                                                           */
  /**************************************************************************/
  
  s1.read_data.meta       := s1_entries_meta(0).asTypeOf(new tlbmeta_t)
  s1.read_data.ptag       := s1_entries_ptag(0)

  
  s1.miss := true.B
  
  for(i <- 0 until ways) {
    when(s1_entries_meta(i).asTypeOf(new tlbmeta_t).valid && (s1_vtag === s1_entries_vtag(i))) {
      /**************************************************************************/
      /* Hit                                                                    */
      /**************************************************************************/
      
      s1.miss                 := false.B
      s1.read_data.meta       := s1_entries_meta(i).asTypeOf(new tlbmeta_t)
      s1.read_data.ptag       := s1_entries_ptag(i)
    }
  }
  
}
