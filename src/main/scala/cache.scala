package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._


class CacheParams(
  val cacheLineLog2: Int = 6,
  val waysLog2: Int  = 1,
  val earlyLog2: Int = 6,
  val lateLog2: Int = 2,
  //val invalidateLatency: Int = 2, // How long does it take to invalidate the memory
) {

  //require(isPow2(invalidateLatency))
  require(waysLog2 >= 1)
  // Make sure that ways is bigger than or equal to 2


  

  require(cacheLineLog2 == 6) // 64 bytes per cache line
  require(cacheLineLog2 + earlyLog2 <= 12) // Make sure that 4K is maximum stored in early resolution as we dont have physical address yet
  if (lateLog2 != 0) {
    require(cacheLineLog2 + earlyLog2 == 12) // Make sure that 4K aligned if ANY amount of late resolution is used
  }

  
  //println(s"CacheParams: waysLog2=$waysLog2, subBeatLog2 = $subBeatLog2, earlyLog2=$earlyLog2, beatIdxLog2=$beatIdxLog2, lateLog2=$lateLog2")
  //println(s"CacheParams: ptag_log2=$ptag_log2")
}


class CacheMeta() extends Bundle {
  val dirty       = Bool()
  val unique      = Bool()
}




class Decomposition(c: CoreParams, cp: CacheParams, address: UInt) extends Bundle {
  import cp._

  val earlyIdx     =                              address(cacheLineLog2 + earlyLog2 - 1, cacheLineLog2)
  val lateIdx      = if (lateLog2 != 0)           address(cacheLineLog2 + earlyLog2 + lateLog2 - 1,  cacheLineLog2 + earlyLog2)  else 0.U(0.W)

  val ptag         =                              address(c.apLen - 1, cacheLineLog2 + earlyLog2 + lateLog2)

  assert(Cat(ptag, lateIdx, earlyIdx, address(cacheLineLog2 - 1, 0)) === address)
}


/*
class Cache(verbose: Boolean, instName: String, c: CoreParams, cp: CacheParams) extends Module {
  import cp._
  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/

  val ptag_log2 = c.apLen - lateLog2 - earlyLog2 - cacheLineLog2
  val busy        = Output(Bool()) // Is the cache busy with some operation
  val enable      = Input(Bool()) // Can proceed to the next stage

  val s0 = IO(new Bundle {
    val read        = Input(Bool()) // Reads a data sample from the cache line
    val write       = Input(Bool()) // Writes a data sample to the cache line
    val flush       = Input(Bool()) // Flush the cache

    val vaddr       = Input(UInt(c.apLen.W)) // Virtual address or physical address for early resolves
  })

  val s1 = IO(new Bundle {
    val kill        = Input(Bool()) // Kill the current operation

    
    val read        = Input(Bool()) // Read command
    val write       = Input(Bool()) // Write command
    val flush       = Input(Bool()) // Flush the cache

    val paddr       = Input(UInt(c.apLen.W)) // Physical address for refills

    val rdata              = Output(Vec(c.xLen_bytes, UInt(8.W))) // Read data from the cache
    val meta               = Output(new CacheMeta)

    // Write data command only
    val writeData         = Input(Vec(c.xLen_bytes, UInt(8.W)))
    val writeMask         = Input(UInt(c.xLen_bytes.W))
  })


  // TODO: Make corebus isntead of dbus. For now we are using dbus
  val corebus = IO(new dbus_t(c))

  val log = new Logger(c.lp.coreName, instName, verbose)


  val s0_vdec = new Decomposition(c, cp, s0.vaddr)

  val (victimWayIdx, _) = Counter(0 until (1 << (waysLog2)), enable = false.B) // TODO: Add the enable condition



  val valid     = RegInit(VecInit.tabulate( (1 << (earlyLog2)))      {idx: Int => 0.U((1 << (waysLog2 + lateLog2)).W)})
  


  val meta = SRAM.masked((1 << (earlyLog2)), Vec(1 << (waysLog2 + lateLog2), new CacheMeta()), 0, 0, 1)

  meta.readwritePorts(0).address := s0_vdec.earlyIdx // TODO: s2 for write or refill
  meta.readwritePorts(0).writeData := VecInit.tabulate(1 << (waysLog2 + lateLog2)) {idx: Int => 0.U.asTypeOf(new CacheMeta)} // TODO: Fix this
  meta.readwritePorts(0).mask.get := (1.U << Cat(victimWayIdx, s0_vdec.lateIdx)).asBools
  meta.readwritePorts(0).enable := s0.read || s2.write
  meta.readwritePorts(0).isWrite := s2.write



  val data = SRAM.masked(1 << (earlyLog2), Vec(1 << (waysLog2 + lateLog2 + cacheLineLog2), UInt(8.W)), 0, 0, 1)

  data.readwritePorts(0).address := s0_vdec.earlyIdx // TODO: s2 for write or refill
  data.readwritePorts(0).writeData := VecInit.tabulate(1 << (waysLog2 + lateLog2 + cacheLineLog2)) {idx: Int => s2.writeData(idx % c.xLen_bytes)}
  data.readwritePorts(0).mask.get := VecInit.tabulate(1 << (waysLog2 + lateLog2 + cacheLineLog2))  {idx: Int => false.B}// TODO: Add mask calculation
  data.readwritePorts(0).enable := s0.read || (corebus.r.valid && corebus.r.ready) || s2.write
  data.readwritePorts(0).isWrite := (corebus.r.valid && corebus.r.ready) || s2.write


  /*
  val data_mask = Seq.tabulate(1 << (waysLog2 + lateLog2 + cacheLineLog2)) 
          {idx: Int =>
            Mux((idx.U >> cacheLineLog2.U) === Cat(s0.writepayload.wayIdxIn, s0_dec.lateIdx),
              s0.writepayload.mask.asBools(idx % (1 << cacheLineLog2)),
              false.B
            )
          }

  val data_rdata = data.readWrite(
    /*idx = */Cat(s0_dec.earlyIdx, s0_dec.beatIdx),
    /*writeData = */VecInit.tabulate(1 << (waysLog2 + lateLog2 + subbeatIdxLog2)) {idx: Int => s0.writepayload.wdata(idx % (1 << cacheLineLog2))},
    /*mask = */data_mask,
    /*en = */s0.resolve || s0.write,
    /*isWrite = */s0.write
  )
  */
  /*
  when(s0.write) {
    valid(s0_dec.earlyIdx)(Cat(s0.writepayload.wayIdxIn, s0_dec.lateIdx)) := s0.writepayload.valid
    log(s"Writing to way ${s0.writepayload.wayIdxIn} at ${s0_dec.earlyIdx} with mask ${s0.writepayload.mask} and data ${s0.writepayload.wdata} and meta ${s0.writepayload.meta}")
  }
  
  s1.response.rdata := VecInit.tabulate((1 << waysLog2)) {wayIdx:Int => data_rdata(Cat(wayIdx.U(waysLog2.W), s0_dec.lateIdx))}
  s1.response.meta := VecInit.tabulate((1 << waysLog2)) {wayIdx:Int => meta_rdata(Cat(wayIdx.U(waysLog2.W), s0_dec.lateIdx))}
  s1.response.valid := RegNext(VecInit.tabulate((1 << waysLog2)) {wayIdx:Int => valid(s0_dec.earlyIdx)(Cat(wayIdx.U(waysLog2.W), s0_dec.lateIdx))}, init = 0.U.asTypeOf(valid(0)))
  */

  val s2_killed             = RegInit(false.B)
  val s2_arCplt             = RegInit(false.B)
  val s2_refillErrors       = RegInit(false.B)

  corebus.ar.bits.len     := 0.U // Single beat only
  corebus.ar.bits.size    := log2Ceil(c.busBytes).U
  corebus.ar.valid        := false.B
  corebus.ar.bits.addr    := Cat(s1_paddr).asSInt
  corebus.r.ready         := false.B

  when(s1_refillInProgress) {
    ibus.ar.valid := !ar_done
    
    when(ibus.ar.ready) {
      ar_done := true.B
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
    val c = new CoreParams
  ChiselStage.emitSystemVerilogFile(new Cache(true, "icache", c = c, cp = c.icache), args=chiselArgs)
}
*/