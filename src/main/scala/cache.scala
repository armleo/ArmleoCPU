package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

object cache_cmd extends ChiselEnum {
    val none, request, write, invalidate = Value
}

class Cache(val is_icache: Boolean, val c: coreParams) extends Module {
  /**************************************************************************/
  /* Parameters from coreParams                                             */
  /**************************************************************************/

  
  var bus_data_bytes = c.bus_data_bytes
  var ways = c.dcache_ways
  var cache_entries = c.dcache_entries
  var cache_entry_bytes = c.dcache_entry_bytes
  var cache_ptag_width = c.dcache_ptag_width

  if(is_icache) {
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
    // write_way_idx_in is used to determine to which way the data is written
    // The external relative to this module register is used to keep the victim
    val write_way_idx_in        = Input(UInt(ways_width.W)) // select way for write
    val write_paddr             = Input(UInt(c.apLen.W))
    val write_bus_aligned_data  = Input(Vec(bus_data_bytes, UInt(8.W)))
    val write_bus_mask          = Input(Vec(bus_data_bytes, Bool()))
    val write_valid             = Input(Bool())
  })
  
  val s1 = IO(new Bundle {
    // paddr is used for calculation of output of miss/bus_aligned_read_data
    // Since this data is not available in cycle 0
    val paddr                 = Input (UInt(c.apLen.W))
    val response              = Output(new Bundle {
      val bus_aligned_data    = Vec(bus_data_bytes, UInt(8.W))
      val miss                = Bool()
    })
  })

  // Q: Why is cache address calculation so complex?
  // A: We want the flexibility of cache area
  //    Even at sacrifice of the readability
  //
  //    Otherwise we would require at least 4KB per way
  //    of Cache.
  //    BUT The milestone 1 needs a tapeout
  //    on sky130, which has very limited area
  //    and really would not fit 4KB of ICACHE and 4KB DCACHE
  
  // 
  // Example calculation for
  // cache_entries = 16
  // cache_entry_bytes = 64
  // bus_data_bytes = 32

  // Q: Why is bus_data_bytes limited to 32 bytes?
  // A: So that we only need to design the refill around guranteed burst access
  //    Otherwise, we would have 64 byte per cycle and all of the complex logic
  //    Of burst would have been avoided
  //    But then we would lose in area flexibility.
  //
  //    For milestone 1 we need sky130 tapeout and the area is very limited.
  //    Therefore the only reasonable option would be to keep our area change options as open
  //    as possible. Even at cost of perfomance

  // cache_ptag_width = apLen - log2Ceil(dcache_entries * dcache_entry_bytes) = 34 - log2(16 * 64) = 24 bits
  // entry num width = log2Ceil(dcache_entries) = 4
  // s0_entry_bus_num width = log2Ceil(dcache_entry_bytes / bus_data_bytes) = 1
  // inbus_offset = log2Ceil(bus_data_bytes) = 5
  // 5 + 1 + 4 + 24 = 34
  // For virtual address that is 32/22 bits respectively. But we need to use the physical address for comparison, anyway

  // val s0_inbus_offset   = s0.vaddr(log2Ceil(bus_data_bytes) - 1, 0) Do we even need this?
  val s0_entry_bus_num  = s0.vaddr(log2Ceil(cache_entry_bytes) - 1, log2Ceil(bus_data_bytes))
  val s0_entry_num      = s0.vaddr(log2Ceil(cache_entries * cache_entry_bytes) - 1, log2Ceil(cache_entry_bytes))
  // val s0_cache_vtag     = s0.vaddr(c.avLen - 1, log2Ceil(cache_entries * cache_entry_bytes)) Do we even need this?

  // In s1, the cache ptag is NOT the same
  // While the pgoff section is shared, the vaddr's top part is not


  /**************************************************************************/
  /* Storage                                                                */
  /**************************************************************************/

  // ways * cache_entries * cache_entry_bytes bytes of cache
  
  // Q: Why is the cache_entries * cache_entry_bytes limited to 4KB (or single page)
  // A: Because we need to start a read in the same cycle as TLB request
  //    to be able to sustain one complete read per cycle
  //    otherwise we would need to predict three cycles (instead of two) of PC.
  //
  //    Also, we would need more complex fetcher. The fetcher would issues requests preemptively
  //    and needs to be able to keep track of all three active requests
  //    Instead we go with Virtually indexed, physically tagged arrangment.
  //    The most common one. It obviously limites the maximum cache size per way to 4KB

  // Q: Then how do the current CPUs include like 200KB of L1 Cache if it is limited to 4KB per way?
  // A: By increasing the ways

  // Q: Why are there a vector of vectors for keeping data?
  // A: You need to use vectors, for masking
  
  // Q: Why are the data bus_data_bytes long? Why not xLen long?
  // A: If this CPU is used with DDR3 it will have 4:1 ratio of data per xLen.
  //    Therefore the 64 bit bus DDR3 controller will generate 256 bit of data per cycle.
  //    The best way to take advantage of this is to be able to change the 
  //    bus with from xLen up to 256 bits
  //    
  //    Keep in mind, that increase of bus width does not come with zero cost
  //    as wider buses will use more area. The milestone 1 requires
  //    a sky130 tapeout and the area is very limited

  val valid   = SyncReadMem(cache_entries, Vec(ways, Bool()))
  val cptags  = SyncReadMem(cache_entries, Vec(ways, UInt(cache_ptag_width.W)))
  val data    = Seq.tabulate(ways) {
    f:Int => SyncReadMem(cache_entries * cache_entry_bytes / bus_data_bytes, Vec(bus_data_bytes, UInt(8.W)))
  }

  val cptags_rdwr = cptags(s0_entry_num)
  val data_rdwr = Seq.tabulate(ways) {
    way: Int => data(way)(Cat(s0_entry_num, s0_entry_bus_num))
  }
  val valid_rdwr = valid(s0_entry_num)
  
  /**************************************************************************/
  /* Invalidate all                                                         */
  /**************************************************************************/
  when(s0.cmd === cache_cmd.invalidate) {
    printf("[Cache] Invalidating entry_num=0x%x\n", s0_entry_num)
    valid_rdwr := 0.U(ways.W).asBools()
  }

  /**************************************************************************/
  /* Write logic                                                            */
  /**************************************************************************/
  when(s0.cmd === cache_cmd.write) {
    valid_rdwr (s0.write_way_idx_in) := s0.write_valid
    cptags_rdwr(s0.write_way_idx_in) := s0.write_paddr(c.apLen - 1, log2Ceil(cache_entries * cache_entry_bytes))
    printf("[Cache] Write cptag/valid way: 0x%x, cptag: 0x%x, valid: 0x%x\n", s0.write_way_idx_in, s0.write_paddr(c.apLen - 1, log2Ceil(cache_entries * cache_entry_bytes)), s0.write_valid)

    for (way <- 0 until ways) {
      // Dont ask me what is going on here
      // TLDR: Its selecting the write_way_ix_in
      // Then writing s0.write_bus_aligned_data according byte
      // Depending on the mask value write_bus_mask

      when(s0.write_way_idx_in === way.U){
        for(bytenum <- 0 until bus_data_bytes) {
          when(s0.write_bus_mask(bytenum)) {
            data_rdwr(way)(bytenum) := s0.write_bus_aligned_data(bytenum)
            printf("[Cache] Write data way: 0x%x, bytenum: 0x%x, data: 0x%x\n", way.U(ways_width.W), bytenum.U(bus_data_bytes.W), s0.write_bus_aligned_data(bytenum))
          }
        }
      }
    }
  }

  /**************************************************************************/
  /* Read logic                                                             */
  /**************************************************************************/
  // Q: Why are we reading unconditionally?
  // A: Just saving area. No need to enable/disable read. Just always read
  //    Power saving would have been good, but we would need to fight the type
  //    System a little bit
  val valid_read  = VecInit.tabulate(ways) {
    way: Int => valid_rdwr(way)
  }
  val data_read   = VecInit.tabulate(ways) {
    way: Int => data_rdwr(way)
  }
  val cptags_read = VecInit.tabulate(ways) {
    way: Int => cptags_rdwr(way)
  }
  
  /**************************************************************************/
  /* Output logic                                                           */
  /**************************************************************************/
  val s1_cptag = s1.paddr(c.apLen - 1, log2Ceil(cache_entries * cache_entry_bytes))

  // Defaults (otherwise, would get an compilation error)
  s1.response.miss                  := true.B
  s1.response.bus_aligned_data      := data_read(0)

  for(i <- 0 until ways) {
    when(valid_read(i) && (s1_cptag === cptags_read(i))) {
      /**************************************************************************/
      /* Hit                                                                    */
      /**************************************************************************/
      
      s1.response.miss                 := false.B
      s1.response.bus_aligned_data     := data_read(i)
    }
  }
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object CacheGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Cache(false, new coreParams))))
}


