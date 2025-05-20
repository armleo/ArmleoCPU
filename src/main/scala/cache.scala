package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._





class CacheParams(
  val ways: Int  = 2,
  val early_log2: Int = 8,
  val bus_log2: Int = 1,
  val late_log2: Int = 2,
  val flush_latency: Int = 2, // How long does it take to flush the memory
) {
  require(isPow2(ways))
  require(isPow2(early_log2))

  require(isPow2(bus_log2) || bus_log2 == 0)
  require(isPow2(late_log2) || late_log2 == 0)

  require(isPow2(flush_latency))
  require(ways >= 2)
  require(bus_log2 + 3 + early_log2 <= 12)
  // Make sure that 

  val ptag_log2 = 56 - late_log2 - bus_log2 - 3 - early_log2

  println(s"CacheParams: ways=$ways, early_log2=$early_log2, bus_log2=$bus_log2, late_log2=$late_log2, flush_latency=$flush_latency")
  println(s"CacheParams: ptag_log2=$ptag_log2")
}




class Decomposition(c: CoreParams, cp: CacheParams, address: UInt) extends Bundle {
  import cp._
  val sub_idx       = address(2, 0)
  val bus_idx       = if (bus_log2 != 0)  Some(address(3 + bus_log2 - 1,                           3))                         else None
  val early_idx     =                          address(3 + bus_log2 + early_log2 - 1,              3 + bus_log2)
  val late_idx      = if (late_log2 != 0) Some(address(3 + bus_log2 + early_log2 + late_log2 - 1,  3 + bus_log2 + early_log2)) else None

  val ptag          =                          address(c.apLen - 1, 3 + bus_log2 + early_log2 + late_log2)

  assert(Cat(ptag, late_idx.getOrElse(0.U(0.W)), early_idx, bus_idx.getOrElse(0.U(0.W)), sub_idx) === address)
}


class Cache(verbose: Boolean, instName: String, c: CoreParams, cp: CacheParams) extends Module {
  import cp._
  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/

  when(s0.flush) {
    assert(!s0.write)
    assert(!s0.resolve)
  }
  when(s0.write) {
    assert(!s0.flush)
    assert(!s0.resolve)
  }
  when(s0.resolve) {
    assert(!s0.flush)
    assert(!s0.write)
  }

  val s0 = IO(new Bundle {
    val flush       = Input(Bool())
    val write       = Input(Bool())
    val resolve     = Input(Bool())

    // Goes high for every completed command
    val cplt        = Output(Bool())


    val vaddr       = Input(UInt(c.apLen.W)) // Virtual address or physical address
    // Regardless, the physical address is used for comparison
    // The virtual address is used for the cache read request index

    // Write data command only
    // register external relative to this module is used to keep the victim
    // We also use this to decide which way to write, otherwise we would need to compare the ptag
    // with the cache ptag
    val writepayload = new Bundle {
      val way_idx_in        = Input(UInt(log2Ceil(ways).W)) // select way for write
      val paddr             = Input(UInt(c.apLen.W))
      val wdata             = Input(Vec(c.bp.data_bytes, UInt(8.W)))
      val mask              = Input(Vec(c.bp.data_bytes, Bool()))
      val valid             = Input(Bool())
    }
  })
  
  val s1 = IO(new Bundle {
    // paddr is used for calculation of output of miss/bus_aligned_read_data
    // Since this data is not available in cycle 0
    val paddr                 = Input (UInt(c.apLen.W))
    val response              = Output(new Bundle {
        val rdata               = Vec(c.bp.data_bytes, UInt(8.W))
        val hit                 = Bool()
    })
  })

  val log = new Logger(c.lp.coreName, instName, verbose)

  
  // val s0_inbus_offset   = s0.vaddr(log2Ceil(c.bp.data_bytes) - 1, 0) Do we even need this?
  val s0_entry_bus_num  = new Decomposition(c, cp, s0.vaddr(log2Ceil(cp.sets_bytes) - 1, log2Ceil(c.bp.data_bytes)))
  val s0_entry_num      = s0.vaddr(log2Ceil(cp.sets * cp.sets_bytes) - 1, log2Ceil(cp.sets_bytes))
  //val s0_cache_vtag     = s0.vaddr(avLen - 1, log2Ceil(cp.sets * cp.sets_bytes)) Do we even need this?

  val lateidx = s1.paddr(late_idx_width - 1, 0)
  // In s1, the cache ptag is NOT the same
  // While the pgoff section is shared, the vaddr's top part is not


  /**************************************************************************/
  /* Storage                                                                */
  /**************************************************************************/

  // ways * cp.sets * cp.sets_bytes bytes of cache
  
  // Q: Why is the cp.sets * cp.sets_bytes limited to 4KB (or single page)
  // A: Because we need to start a read in the same cycle as TLB request
  //    to be able to sustain one complete read per cycle
  //    otherwise we would need to predict three cycles (instead of two) of PC.
  //
  //    Also, we would need more complex fetcher. The fetcher would issues requests preemptively
  //    and needs to be able to keep track of all three active requests
  //    Instead we go with Virtually indexed, physically tagged arrangment.
  //    The most common one. It obviously limites the maximum cache size per way to 4KB

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

  val ptag    = SyncReadMem(cp.sets, Vec(cp.ways, UInt(cache_ptag_width.W)))
  val valid   = SyncReadMem(cp.sets / flush_latency, Vec(cp.ways, UInt(flush_latency.W)))
  val data    = Seq.tabulate(cp.ways) {
    f:Int => 
      val data_storage = SyncReadMem(cp.sets * cp.sets_bytes / c.bp.data_bytes, Vec(late_idx_width, Vec(c.bp.data_bytes, UInt(8.W))))

      data_storage
  }


  ptag.readWrite(
    /*idx = */s0_entry_num,
    /*writeData = */VecInit.tabulate(ways) {way: Int => s0.writepayload.paddr(c.apLen - 1, log2Ceil(cp.sets * cp.sets_bytes) + 12)},
    /*mask = */(1.U << s0.writepayload.way_idx_in).asBools,
    /*en = */s0.resolve || s0.write,
    /*isWrite = */s0.write
  )


  val data_rdwr = Seq.tabulate(cp.ways) {
    way: Int => data(way)(Cat(s0_entry_num, s0_entry_bus_num))
  }
  
  /**************************************************************************/
  /* Flush all                                                         */
  /**************************************************************************/
  when(s0.flush) {
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
  val s0_writepayload_cptag = s0.writepayload.paddr(c.apLen - 1, log2Ceil(cp.sets * cp.sets_bytes))
  when(s0.write) {
    // TODO: No separate writes
    val meta_write = Wire(new cache_meta_t)
    meta_write.valid := s0.writepayload.valid
    meta_write.cptag := s0_writepayload_cptag
    meta_rdwr(s0.writepayload.way_idx_in) := meta_write.asUInt
    //meta_rdwr(s0.writepayload.way_idx_in).valid := s0.writepayload.valid
    //meta_rdwr(s0.writepayload.way_idx_in).cptag := s0.writepayload.paddr(cp.apLen - 1, log2Ceil(cp.sets * cp.sets_bytes))
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
  val s1_cptag = s1.paddr(c.apLen - 1, log2Ceil(cp.sets * cp.sets_bytes))

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

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation



object CacheGenerator extends App {
  val chiselArgs =
    Array(
      "--target",
      "systemverilog",
      "--target-dir",
      "generated_vlog",
    )
  ChiselStage.emitSystemVerilogFile(new Cache, args=chiselArgs)
}
