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
      val bus_aligned_read_data = Vec(bus_data_bytes, UInt(8.W))
      val miss                  = Bool()
    })
  })

  // Example calculation
  // cache_entries = 16
  // cache_entry_bytes = 64
  // bus_data_bytes = 32

  // cache_ptag_width = apLen - log2Ceil(dcache_entries * dcache_entry_bytes) = 34 - log2(16 * 64) = 24 bits
  // entry num = log2Ceil(dcache_entries) = 4
  // entry_bus_idx_width = log2Ceil(dcache_entry_bytes / bus_data_bytes) = 1
  // inbus_offset = log2Ceil(bus_data_bytes) = 5
  // 5 + 1 + 4 + 24 = 34
  // For virtual address that is 32/22 bits respectively. But we need to the physical address for comparison, anyway

  // val s0_inbus_offset   = s0.vaddr(log2Ceil(bus_data_bytes) - 1, 0) Do we even need this?
  val s0_entry_bus_num  = s0.vaddr(log2Ceil(cache_entry_bytes / bus_data_bytes) + log2Ceil(bus_data_bytes) - 1, log2Ceil(bus_data_bytes))
  val s0_entry_num      = s0.vaddr(log2Ceil(cache_entries * cache_entry_bytes) - 1, log2Ceil(cache_entry_bytes))
  // val s0_cache_vtag     = s0.vaddr(c.avLen - 1, log2Ceil(cache_entries * cache_entry_bytes)) Do we even need this?

  // In s1, the cache ptag is NOT the same
  // While the pgoff section is shared, the vaddr's top part is not


  /**************************************************************************/
  /* Storage                                                                */
  /**************************************************************************/

  // ways * cache_entries * cache_entry_bytes bytes of cache
    
  val valid = RegInit(VecInit.tabulate(cache_entries) {f: Int => VecInit.tabulate(ways) {f:Int => false.B}})
  val data  = SyncReadMem(cache_entries * cache_entry_bytes / bus_data_bytes, Vec(ways, UInt(bus_data_bytes.W)))
  val cptags = SyncReadMem(cache_entries, Vec(ways, UInt(cache_ptag_width.W)))
  
  
  /**************************************************************************/
  /* Read logic                                                             */
  /**************************************************************************/
  

}
