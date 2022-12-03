package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._


import armleocpu.utils._


class CacheParams(
  val ways: Int  = 2, // How many ways there are
  val entries: Int = 32, // How many entries each way contains
  val entry_bytes: Int = 64, // in bytes
) {
}


object cache_cmd extends ChiselEnum {
    val none, request, write, invalidate = Value
}

class Cache(verbose: Boolean = true, instName: String = "inst$", c: CoreParams = new CoreParams, cp: CacheParams = new CacheParams) extends Module {
  
  /**************************************************************************/
  /* Parameters from CoreParams                                             */
  /**************************************************************************/

  val ways_width = log2Ceil(cp.ways)

  // bus_data_bytes used to be separate between Ibus and Dbus.
  // However, it would complicate PTW's bus connection and parametrization, so the idea was scrapped
  require(c.bp.data_bytes <= cp.entry_bytes)
  require(isPositivePowerOfTwo(cp.ways))
  require(isPositivePowerOfTwo(cp.entries))
  require(isPositivePowerOfTwo(cp.entry_bytes))


  // If it gets bigger than 4096 bytes, then it goes out of page boundry
  // This means that TLB has to be resolved before cache request is sent
  // Instead we just require that one way cant contain more than one page
  require(cp.entries * cp.entry_bytes <= 4096)


  val cache_ptag_width = c.archParams.apLen - log2Up(cp.entries * cp.entry_bytes)


  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/

  val s0 = IO(new Bundle {
    val cmd         = Input(chiselTypeOf(cache_cmd.none))
    val vaddr       = Input(UInt(c.archParams.avLen.W))

    // Write data command only
    // write_way_idx_in is used to determine to which way the data is written
    // The external relative to this module register is used to keep the victim
    val writepayload = new Bundle {
      val way_idx_in        = Input(UInt(ways_width.W)) // select way for write
      val paddr             = Input(UInt(c.archParams.apLen.W))
      val bus_aligned_data  = Input(Vec(c.bp.data_bytes, UInt(8.W)))
      val bus_mask          = Input(Vec(c.bp.data_bytes, Bool()))
      val valid             = Input(Bool())
    }
  })
  
  val s1 = IO(new Bundle {
    // paddr is used for calculation of output of miss/bus_aligned_read_data
    // Since this data is not available in cycle 0
    val paddr                 = Input (UInt(c.archParams.apLen.W))
    val response              = Output(new Bundle {
      val bus_aligned_data    = Vec(c.bp.data_bytes, UInt(8.W))
      val miss                = Bool()
    })
  })

  val log = new Logger(c.lp.coreName, instName, verbose)

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
  // cp.entries = 16
  // cp.entry_bytes = 64
  // c.bp.data_bytes = 32

  // cache_ptag_width = apLen - log2Ceil(cp.entries * cp.entry_bytes) = 34 - log2(16 * 64) = 24 bits
  // entry num width = log2Ceil(cp.entries) = 4
  // s0_entry_bus_num width = log2Ceil(cp.entry_bytes / c.bp.data_bytes) = 1
  // inbus_offset = log2Ceil(c.bp.data_bytes) = 5
  // 5 + 1 + 4 + 24 = 34
  // For virtual address that is 32/22 bits respectively. But we need to use the physical address for comparison, anyway

  // val s0_inbus_offset   = s0.vaddr(log2Ceil(c.bp.data_bytes) - 1, 0) Do we even need this?
  val s0_entry_bus_num  = s0.vaddr(log2Ceil(cp.entry_bytes) - 1, log2Ceil(c.bp.data_bytes))
  val s0_entry_num      = s0.vaddr(log2Ceil(cp.entries * cp.entry_bytes) - 1, log2Ceil(cp.entry_bytes))
  // val s0_cache_vtag     = s0.vaddr(avLen - 1, log2Ceil(cp.entries * cp.entry_bytes)) Do we even need this?

  // In s1, the cache ptag is NOT the same
  // While the pgoff section is shared, the vaddr's top part is not


  /**************************************************************************/
  /* Storage                                                                */
  /**************************************************************************/

  // ways * cp.entries * cp.entry_bytes bytes of cache
  
  // Q: Why is the cp.entries * cp.entry_bytes limited to 4KB (or single page)
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
  
  // Q: Why are the data c.bp.data_bytes long? Why not xLen long?
  // A: If this CPU is used with DDR3 it will have 4:1 ratio of data per xLen.
  //    Therefore the 64 bit bus DDR3 controller will generate 256 bit of data per cycle.
  //    The best way to take advantage of this is to be able to change the 
  //    bus with from xLen up to 256 bits
  //    
  //    Keep in mind, that increase of bus width does not come with zero cost
  //    as wider buses will use more area. The milestone 1 requires
  //    a sky130 tapeout and the area is very limited. That's why its configurable

  class cache_meta_t extends Bundle {
    val valid = Bool()
    val cptag = UInt(cache_ptag_width.W)
  }
  
  // Unfortunetly if we need the FIRRTL memory structues, the Vec() needs to use non aggregate types
  // And not have any partial/masked writes
  val meta    = SyncReadMem(cp.entries, Vec(cp.ways, UInt((new cache_meta_t).getWidth.W)))
  val data    = Seq.tabulate(cp.ways) {
    f:Int => SyncReadMem(cp.entries * cp.entry_bytes / c.bp.data_bytes, Vec(c.bp.data_bytes, UInt(8.W)))
  }

  val meta_rdwr = meta(s0_entry_num)
  val data_rdwr = Seq.tabulate(cp.ways) {
    way: Int => data(way)(Cat(s0_entry_num, s0_entry_bus_num))
  }
  
  /**************************************************************************/
  /* Invalidate all                                                         */
  /**************************************************************************/
  when(s0.cmd === cache_cmd.invalidate) {
    log("Invalidating entry_num=0x%x", s0_entry_num)
    meta_rdwr.foreach(f => {
      val meta_invalidate = Wire(new cache_meta_t)
      meta_invalidate.valid := false.B
      meta_invalidate.cptag := 0.U

      f := meta_invalidate.asUInt
    })
  }

  /**************************************************************************/
  /* Write logic                                                            */
  /**************************************************************************/
  val s0_writepayload_cptag = s0.writepayload.paddr(c.archParams.apLen - 1, log2Ceil(cp.entries * cp.entry_bytes))
  when(s0.cmd === cache_cmd.write) {
    // TODO: No separate writes
    val meta_write = Wire(new cache_meta_t)
    meta_write.valid := s0.writepayload.valid
    meta_write.cptag := s0_writepayload_cptag
    meta_rdwr(s0.writepayload.way_idx_in) := meta_write.asUInt
    //meta_rdwr(s0.writepayload.way_idx_in).valid := s0.writepayload.valid
    //meta_rdwr(s0.writepayload.way_idx_in).cptag := s0.writepayload.paddr(cp.apLen - 1, log2Ceil(cp.entries * cp.entry_bytes))
    log("Write cptag/valid way: 0x%x, entry_num: 0x%x, entry_bus_num: 0x%x, cptag: 0x%x, valid: 0x%x",
      s0.writepayload.way_idx_in, s0_entry_num, s0_entry_bus_num, s0_writepayload_cptag, s0.writepayload.valid)

    for (way <- 0 until cp.ways) {
      // Dont ask me what is going on here
      // TLDR: Its selecting the write_way_ix_in
      // Then writing s0.writepayload.bus_aligned_data according byte
      // Depending on the mask value write_bus_mask

      when(s0.writepayload.way_idx_in === way.U){
        for(bytenum <- 0 until c.bp.data_bytes) {
          when(s0.writepayload.bus_mask(bytenum)) {
            data_rdwr(way)(bytenum) := s0.writepayload.bus_aligned_data(bytenum)
            log("Write data way: 0x%x, entry_num: 0x%x, entry_bus_num: 0x%x, bytenum: 0x%x, data: 0x%x", way.U(ways_width.W), s0_entry_num, s0_entry_bus_num, bytenum.U(c.bp.data_bytes.W), s0.writepayload.bus_aligned_data(bytenum))
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
  val meta_read  = VecInit.tabulate(cp.ways) {
    way: Int => meta_rdwr(way)
  }
  val data_read   = VecInit.tabulate(cp.ways) {
    way: Int => data_rdwr(way)
  }
  
  /**************************************************************************/
  /* Output logic                                                           */
  /**************************************************************************/
  val s1_cptag = s1.paddr(c.archParams.apLen - 1, log2Ceil(cp.entries * cp.entry_bytes))

  // Defaults (otherwise, would get an compilation error)
  s1.response.miss                  := true.B
  s1.response.bus_aligned_data      := data_read(0)

  for(i <- 0 until cp.ways) {
    when(meta_read(i).asTypeOf(new cache_meta_t).valid && (s1_cptag === meta_read(i).asTypeOf(new cache_meta_t).cptag)) {
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
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Cache)))
}


