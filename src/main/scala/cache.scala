package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._


class cache_meta_t extends Bundle {
  val valid       = Bool()
  val dirty       = Bool()
  val unique      = Bool()
}


class CacheParams(
  val subBeatLog2: Int = 5,
  val waysLog2: Int  = 1,
  val beatIdxLog2: Int = 1,
  val earlyLog2: Int = 6,
  val lateLog2: Int = 2,
  val flushLatency: Int = 2, // How long does it take to flush the memory
) {
  val cacheLineLog2 = subBeatLog2 + beatIdxLog2

  require(isPow2(flushLatency))
  require(waysLog2 >= 1)
  // Make sure that 


  val ptag_log2 = 56 - lateLog2 - earlyLog2 - beatIdxLog2 - subBeatLog2

  require(cacheLineLog2 == 6) // 64 bytes per cache line
  require(cacheLineLog2 + earlyLog2 <= 12)
  if (lateLog2 != 0) {
    require(cacheLineLog2 + earlyLog2 == 12) // Make sure that 4K aligned
  }

  
  println(s"CacheParams: waysLog2=$waysLog2, subBeatLog2 = $subBeatLog2, earlyLog2=$earlyLog2, beatIdxLog2=$beatIdxLog2, lateLog2=$lateLog2, flushLatency=$flushLatency")
  println(s"CacheParams: ptag_log2=$ptag_log2")
}




class Decomposition(c: CoreParams, cp: CacheParams, address: UInt) extends Bundle {
  import cp._
  
  val beatIdx      = if (beatIdxLog2 != 0) Some(  address(beatIdxLog2 + subBeatLog2 - 1,                         subBeatLog2))                            else None
  val earlyIdx     =                              address(beatIdxLog2 + subBeatLog2 + earlyLog2 - 1,             beatIdxLog2 + subBeatLog2)
  val lateIdx      = if (lateLog2 != 0) Some(     address(beatIdxLog2 + subBeatLog2 + earlyLog2 + lateLog2 - 1,  beatIdxLog2 + subBeatLog2 + earlyLog2))  else None

  val ptag         =                              address(c.apLen - 1, beatIdxLog2 + subBeatLog2 + earlyLog2 + lateLog2)

  assert(Cat(ptag, lateIdx.getOrElse(0.U(0.W)), earlyIdx, beatIdx.getOrElse(0.U(0.W)), address(subBeatLog2 - 1, 0)) === address)
}


class Cache(verbose: Boolean, instName: String, c: CoreParams, cp: CacheParams) extends Module {
  import cp._
  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/

  val s0 = IO(new Bundle {
    val flush       = Input(Bool())
    val write       = Input(Bool())
    val resolve     = Input(Bool())


    val vaddr       = Input(UInt(c.apLen.W)) // Virtual address or physical address for early resolves
    // It also contains the physical address for write command, due to late resolution


    // Regardless, the physical address is used for comparison
    // The virtual address is used for the cache read request index

    // Write data command only
    // register external relative to this module is used to keep the victim
    // We also use this to decide which way to write, otherwise we would need to compare the ptag
    // with the cache ptag
    val writepayload = new Bundle {
      val wayIdxIn        = Input(UInt(waysLog2.W)) // select way for write
      val paddr             = Input(UInt(c.apLen.W))
      val wdata             = Input(Vec(c.bp.dataBytes, UInt(8.W)))
      val mask              = Input(Vec(c.bp.dataBytes, Bool()))
      val valid             = Input(Bool())
    }
  })
  
  val s1 = IO(new Bundle {
    // paddr is used for calculation of output of miss/bus_aligned_read_data
    // Since this data is not available in cycle 0
    val paddr                 = Input (UInt(c.apLen.W))
    // Goes high for every completed command
    val cplt                  = Output(Bool())
    val response              = Output(new Bundle {
        val rdata               = Vec(c.bp.dataBytes, UInt(8.W))
        val hit                 = Bool()
    })
  })


  val log = new Logger(c.lp.coreName, instName, verbose)


  /**************************************************************************/
  /* Simulation only                                                         */
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
  
  val s0_dec = new Decomposition(c, cp, s0.vaddr)

  val data      = SyncReadMem(1 << (earlyLog2 + beatIdxLog2),    Vec(1 << (waysLog2 + lateLog2 + cacheLineLog2), UInt(8.W)))
  val ptag      = SyncReadMem(1 << (earlyLog2),                    Vec(1 << (waysLog2 + lateLog2), UInt(ptag_log2.W)))
  val meta      = SyncReadMem(1 << (earlyLog2 / flushLatency),    Vec(1 << (waysLog2 + lateLog2), Vec(flushLatency, new cache_meta_t)))

  
  when(s0.flush) {
    log("Invalidating")
  }

  meta.readWrite(
    /*idx = */s0_dec.earlyIdx / flushLatency.U,
    /*writeData = */0.U,
    /*mask = */s0_dec.earlyIdx % flushLatency.U,
    /*en = */s0.resolve || s0.write || s0.flush,
    /*isWrite = */s0.write || s0.flush
  )

  /*
  ptag.readWrite(
    /*idx = */s0_dec.earlyIdx,
    /*writeData = */VecInit.tabulate(1 << (waysLog2 + lateLog2)) {idx: Int => VecInit(Seq.fill(, s0.writepayload.paddr())}.asUInt,
    /*mask = */(1.U << s0.writepayload.wayIdxIn).asBools,
    /*en = */s0.resolve || s0.write,
    /*isWrite = */s0.write
  )
  */
  /*
  val data_rdwr = Seq.tabulate(cp.ways) {
    way: Int => data(way)(Cat(s0_entry_num, s0_entry_bus_num))
  }
  
  /**************************************************************************/
  /* Flush all                                                         */
  /**************************************************************************/
  
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
    meta_rdwr(s0.writepayload.wayIdxIn) := meta_write.asUInt
    //meta_rdwr(s0.writepayload.wayIdxIn).valid := s0.writepayload.valid
    //meta_rdwr(s0.writepayload.wayIdxIn).cptag := s0.writepayload.paddr(cp.apLen - 1, log2Ceil(cp.sets * cp.sets_bytes))
    log("Write cptag/valid way: 0x%x, entry_num: 0x%x, entry_bus_num: 0x%x, cptag: 0x%x, valid: 0x%x",
      s0.writepayload.wayIdxIn, s0_entry_num, s0_entry_bus_num, s0_writepayload_cptag, s0.writepayload.valid)

    for (way <- 0 until cp.ways) {
      // Dont ask me what is going on here
      // TLDR: Its selecting the write_way_ix_in
      // Then writing s0.writepayload.bus_aligned_data according byte
      // Depending on the mask value write_bus_mask

      when(s0.writepayload.wayIdxIn === way.U){
        for(bytenum <- 0 until c.bp.dataBytes) {
          when(s0.writepayload.bus_mask(bytenum)) {
            data_rdwr(way)(bytenum) := s0.writepayload.bus_aligned_data(bytenum)
            log("Write data way: 0x%x, entry_num: 0x%x, entry_bus_num: 0x%x, bytenum: 0x%x, data: 0x%x", way.U(ways_width.W), s0_entry_num, s0_entry_bus_num, bytenum.U(c.bp.dataBytes.W), s0.writepayload.bus_aligned_data(bytenum))
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
  }*/
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
    val c = new CoreParams
  ChiselStage.emitSystemVerilogFile(new Cache(true, "icache", c = c, cp = c.icache), args=chiselArgs)
}
