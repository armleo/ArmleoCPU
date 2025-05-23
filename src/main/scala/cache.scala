package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._


class CacheParams(
  val subBeatLog2: Int = 5,
  val waysLog2: Int  = 1,
  val beatIdxLog2: Int = 1,
  val earlyLog2: Int = 6,
  val lateLog2: Int = 2,
  //val invalidateLatency: Int = 2, // How long does it take to invalidate the memory
) {
  val cacheLineLog2 = subBeatLog2 + beatIdxLog2

  //require(isPow2(invalidateLatency))
  require(waysLog2 >= 1)
  // Make sure that ways is bigger than or equal to 2


  val ptag_log2 = 56 - lateLog2 - earlyLog2 - beatIdxLog2 - subBeatLog2

  require(cacheLineLog2 == 6) // 64 bytes per cache line
  require(cacheLineLog2 + earlyLog2 <= 12) // Make sure that 4K is maximum stored in early resolution as we dont have physical address yet
  if (lateLog2 != 0) {
    require(cacheLineLog2 + earlyLog2 == 12) // Make sure that 4K aligned if ANY amount of late resolution is used
  }

  
  //println(s"CacheParams: waysLog2=$waysLog2, subBeatLog2 = $subBeatLog2, earlyLog2=$earlyLog2, beatIdxLog2=$beatIdxLog2, lateLog2=$lateLog2")
  //println(s"CacheParams: ptag_log2=$ptag_log2")
}


class CacheMeta(cp: CacheParams) extends Bundle {
  val dirty       = Bool()
  val unique      = Bool()
  val ptag        = UInt(cp.ptag_log2.W)
}




class Decomposition(c: CoreParams, cp: CacheParams, address: UInt) extends Bundle {
  import cp._
  
  val beatIdx      = if (beatIdxLog2 != 0)        address(beatIdxLog2 + subBeatLog2 - 1,                         subBeatLog2)                            else 0.U(0.W)
  val earlyIdx     =                              address(beatIdxLog2 + subBeatLog2 + earlyLog2 - 1,             beatIdxLog2 + subBeatLog2)
  val lateIdx      = if (lateLog2 != 0)           address(beatIdxLog2 + subBeatLog2 + earlyLog2 + lateLog2 - 1,  beatIdxLog2 + subBeatLog2 + earlyLog2)  else 0.U(0.W)

  val ptag         =                              address(c.apLen - 1, beatIdxLog2 + subBeatLog2 + earlyLog2 + lateLog2)

  assert(Cat(ptag, lateIdx, earlyIdx, beatIdx, address(subBeatLog2 - 1, 0)) === address)
}


class Cache(verbose: Boolean, instName: String, c: CoreParams, cp: CacheParams) extends Module {
  import cp._
  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/

  val s0 = IO(new Bundle {
    val invalidate  = Input(Bool()) // Removes all entries from the cache (only happens after reset)
    val fill        = Input(Bool()) // Fills a beat to the cache line
    val resolve     = Input(Bool()) // Reads a data sample from the cache line
    val write       = Input(Bool()) // Writes a data sample to the cache line
    // Difference between fill and write is that fill is used to fill the cache line from memory,
    // while write is used to write to the cache line by the CPU according to the instruction

    val vaddr       = Input(UInt(c.apLen.W)) // Virtual address or physical address for early resolves
    // It also contains the physical address for write/fill commands, due to late resolution


    // Regardless, the physical address is used for comparison
    // The virtual address is used for the cache read request index

    // Write data command only
    // register external relative to this module is used to keep the victim
    // We also use this to decide which way to write, otherwise we would need to compare the ptag
    // with the cache ptag
    val writepayload = new Bundle {
      val wayIdxIn          = Input(UInt(waysLog2.W)) // select way for write
      val fillData          = Input(Vec(c.busBytes, UInt(8.W)))
      val meta              = Input(new CacheMeta(cp))

      val writeData         = Input(Vec(c.xLen_bytes, UInt(8.W)))
      val writeMask         = Input(UInt(c.xLen_bytes.W))

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
        val rdata              = Vec(1 << waysLog2, Vec(c.busBytes, UInt(8.W)))
        val meta               = Vec(1 << waysLog2, new CacheMeta(cp))
        val valid              = Vec(1 << waysLog2, Bool())
    })
  })


  val log = new Logger(c.lp.coreName, instName, verbose)


  /**************************************************************************/
  /* Simulation only                                                         */
  /**************************************************************************/
  when(s0.invalidate) {
    assert(!s0.write)
    assert(!s0.resolve)
  }
  when(s0.write) {
    assert(!s0.invalidate)
    assert(!s0.resolve)
  }
  when(s0.resolve) {
    assert(!s0.invalidate)
    assert(!s0.write)
  }
  
  val s0_dec = new Decomposition(c, cp, s0.vaddr)

  val data      = SyncReadMem               (1 << (earlyLog2 + beatIdxLog2),       Vec(1 << (waysLog2 + lateLog2 + cacheLineLog2), UInt(8.W)))
  val meta      = SyncReadMem               (1 << (earlyLog2),                     Vec(1 << (waysLog2 + lateLog2), new CacheMeta(cp)))
  val valid     = RegInit(VecInit.tabulate( (1 << (earlyLog2)))      {idx: Int => 0.U((1 << (waysLog2 + lateLog2)).W)})


  
  when(s0.invalidate) {
    valid := 0.U.asTypeOf(valid) // Invalidate all
    log("Invalidating")
  }

  
  val meta_mask = (1.U << Cat(s0.writepayload.wayIdxIn, s0_dec.lateIdx))

  val meta_rdata = meta.readWrite(
    /*idx = */s0_dec.earlyIdx,
    /*writeData = */VecInit.tabulate(1 << (waysLog2 + lateLog2)) {idx: Int => s0.writepayload.meta},
    /*mask = */meta_mask.asBools,
    /*en = */s0.resolve || s0.write || s0.fill,
    /*isWrite = */s0.write
  )




  val data_mask = Seq.tabulate(1 << (waysLog2 + lateLog2 + cacheLineLog2)) 
          {idx: Int =>
            Mux((idx.U >> cacheLineLog2.U) === Cat(s0.writepayload.wayIdxIn, s0_dec.lateIdx),
              s0.writepayload.mask.asBools(idx % (1 << cacheLineLog2)),
              false.B
            )
          }

  val data_rdata = data.readWrite(
    /*idx = */Cat(s0_dec.earlyIdx, s0_dec.beatIdx),
    /*writeData = */VecInit.tabulate(1 << (waysLog2 + lateLog2 + cacheLineLog2)) {idx: Int => s0.writepayload.wdata(idx % (1 << cacheLineLog2))},
    /*mask = */data_mask,
    /*en = */s0.resolve || s0.write,
    /*isWrite = */s0.write
  )

  when(s0.write) {
    valid(s0_dec.earlyIdx)(Cat(s0.writepayload.wayIdxIn, s0_dec.lateIdx)) := s0.writepayload.valid
    log(s"Writing to way ${s0.writepayload.wayIdxIn} at ${s0_dec.earlyIdx} with mask ${s0.writepayload.mask} and data ${s0.writepayload.wdata} and meta ${s0.writepayload.meta}")
  }
  
  s1.response.rdata := VecInit.tabulate((1 << waysLog2)) {wayIdx:Int => data_rdata(Cat(wayIdx.U(waysLog2.W), s0_dec.lateIdx))}
  s1.response.meta := VecInit.tabulate((1 << waysLog2)) {wayIdx:Int => meta_rdata(Cat(wayIdx.U(waysLog2.W), s0_dec.lateIdx))}
  s1.response.valid := RegNext(VecInit.tabulate((1 << waysLog2)) {wayIdx:Int => valid(s0_dec.earlyIdx)(Cat(wayIdx.U(waysLog2.W), s0_dec.lateIdx))}, init = 0.U.asTypeOf(valid(0)))
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
