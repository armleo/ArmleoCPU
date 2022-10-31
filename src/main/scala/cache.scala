package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

object cache_cmd extends ChiselEnum {
    val none, request, write, invalidate_all = Value
}

class cache(val is_icache: Boolean, val c: coreParams) extends Module {
  /**************************************************************************/
  /* Parameters from coreParams                                             */
  /**************************************************************************/

  
  var bus_data_bytes = c.dbus_data_bytes
  var ways = c.dcache_ways
  var cache_entries = c.dcache_entries
  var cache_entry_bytes = c.dcache_entry_bytes
  var cache_ptag_width = c.dcache_ptag_width

  if(is_icache) {
    bus_data_bytes = c.ibus_data_bytes
    ways = c.icache_ways
    cache_entries = c.icache_entries
    cache_entry_bytes = c.icache_entry_bytes
    cache_ptag_width = c.icache_ptag_width
  }

  val ways_width = log2Ceil(ways)

  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/

  val s0 = IO(new Bundle {
    val cmd         = Input(chiselTypeOf(cache_cmd.none))
    val vaddr       = Input(UInt(c.avLen.W))

    // Write data command only
    // way_idx_in is used to determine to which way the data is written
    // The external relative to this module register is used to keep the victim
    val way_idx_in              = Input(UInt(ways_width.W)) // select way for write
    val bus_aligned_write_data  = Input(Vec(bus_data_bytes, UInt(8.W)))
    val bus_mask                = Input(Vec(bus_data_bytes, Bool()))
  })
  
  val s1 = IO(new Bundle {
    // paddr is used for calculation of output of miss/bus_aligned_read_data
    // Since this data is not available in cycle 0
    val paddr                 = Input (UInt(c.apLen.W))
    val response              = Output(new Bundle {
      val bus_aligned_read_data = UInt(c.xLen.W)
      val miss                  = Bool()
    })
  })

  /**************************************************************************/
  /* Storage                                                                */
  /**************************************************************************/

  // ways * icache_entries * icache_entry_bytes bytes of icache
    
  val valid = RegInit(VecInit.tabulate(cache_entries) {f: Int => VecInit.tabulate(ways) {f:Int => false.B}})
  val data  = SyncReadMem(c.icache_entries * c.icache_entry_bytes / c.ibus_data_bytes, Vec(c.icache_ways, UInt(c.ibus_data_bytes.W)))
  val ptags = SyncReadMem(c.icache_entries, Vec(c.icache_ways, UInt(cache_ptag_width.W)))
  
  
  /**************************************************************************/
  /* Read logic                                                             */
  /**************************************************************************/
  

}
