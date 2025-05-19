package armleocpu

import chisel3._
import chisel3.util._

import chisel3.util._


import armleocpu.utils._


// L0 is gigapages
// L1 is megapages
// L2 is 4K pages

class TlbParams(
  val l0_sets:Int = 64,
  val l1_sets:Int = 16,
  val l2_sets:Int = 4,

  val l0_ways:Int = 4,
  val l1_ways:Int = 8,
  val l2_ways:Int = 16
) {
  require(isPositivePowerOfTwo(l0_sets))
  require(isPositivePowerOfTwo(l1_sets))
  require(isPositivePowerOfTwo(l2_sets))
  require(l2_sets <= l1_sets)
  require(l1_sets <= l0_sets)

  require(log2Ceil(l2_sets) <= 9)
  require(log2Ceil(l1_sets) <= 18)
  require(log2Ceil(l0_sets) <= 27)

  require(isPositivePowerOfTwo(l0_ways))
  require(isPositivePowerOfTwo(l1_ways))
  require(isPositivePowerOfTwo(l2_ways))
  require(l2_ways <= l1_ways)
  require(l1_ways <= l0_ways)
  require(2 <= l0_ways)
  require(2 <= l1_ways)
  require(2 <= l2_ways)
}

/**************************************************************************/
/* Input/Output Bundles                                                   */
/**************************************************************************/

object tlb_cmd extends ChiselEnum {
  val none, resolve, flush, write = Value
}


class tlb_accessbits_t extends Bundle {
  val dirty   = Bool()
  val access  = Bool()
  val global  = Bool()
  val user    = Bool()
  val execute = Bool()
  val write   = Bool()
  val read    = Bool()

  // Trying to read this entry resulted in accessfault
  val accessfault = Bool()

  // Trying to resolve this entry resulted in pagefault
  val pagefault = Bool()
}


// This bundle is kept in the memory,
//    while valid bit is kept in registers due to flush invalidating every entry

class tlb_entry_t(c: CoreParams, lvl: Int) extends tlb_accessbits_t {
  require(lvl <= 2)
  require(lvl >= 0)
  // The accessbits are defined in tlb_accessbits_t we extends
  val vpn =
    if(lvl == 2)        UInt(9.W)
    else if (lvl == 1)  UInt(18.W)
    else                UInt(24.W)
  
  val ppn = UInt(44.W) // We keep the 44 bits as it can be a pointer to a subtree
  
  def is_leaf(): Bool = read || execute
}

class tlb_wentry_t(c: CoreParams, lvl: Int) extends tlb_entry_t(c, lvl = lvl)  {
  val valid = Bool()
}

class tlb_result_t(c: CoreParams, lvl: Int) extends tlb_wentry_t(c, lvl = lvl)  {
  val hit = Bool()
}

class TLBIO(c: CoreParams) extends Bundle {
  // Command for TLB
  val s0 = new Bundle {
    val cmd       = Input(chiselTypeOf(tlb_cmd.write))
    val lvl       = Input(UInt(3.W))
    val vpn       = Input(UInt(39.W))
    val wentry = Input(new Bundle {
      val l0 = Input(new tlb_wentry_t(c, lvl = 0))
      val l1 = Input(new tlb_wentry_t(c, lvl = 1))
      val l2 = Input(new tlb_wentry_t(c, lvl = 2))
    })
  }

  // Output stage of TLB
  // Only valid for one cycle because it uses memory units,
  // which keep the output valid only one cycle
  val s1 = new Bundle {
    val l0    = Output(new tlb_result_t(c, lvl = 0))
    val l1    = Output(new tlb_result_t(c, lvl = 1))
    val l2    = Output(new tlb_result_t(c, lvl = 2))
  }
}



class AssociativeMemoryIO[T <: Data](ways: Int, sets: Int, t: T) extends Bundle {
  val flush       = Input (Bool())
  val cplt        = Output(Bool())

  val resolve     = Input(Bool())
  val write       = Input(Bool())

  val s0 = new Bundle {
    val idx       = Input(UInt(log2Ceil(sets).W))
    val valid     = Input(Bool())
    val wentry    = Input(t)
  }

  val s1 = new Bundle {
    val rentry    = Output(Vec(ways, t))
    val valid     = Output(Vec(ways, Bool()))
  }
}


class AssociativeMemory[T <: Data](
  // Primary parameters
  t: T,
  
  sets:Int, ways:Int,
  
  // How long does it take to flush the memory
  // It is up to the designer to decide if they want to flush
  // in two cycle or more.
  // More cycles means more Fmax, less cycles means lower flush latency
  flushLatency: Int,

  // Simulation only
  verbose: Boolean, instName: String, c: CoreParams, 
) extends Module {
  /**************************************************************************/
  /* Parameters                                                             */
  /**************************************************************************/
  
  require(isPositivePowerOfTwo(ways))
  require(isPositivePowerOfTwo(sets))

  require(flushLatency >= 2, "FLush latency need to be higher than one cycle")
  require(isPositivePowerOfTwo(flushLatency))
  require((sets % flushLatency) == 0, "Set count needs to be divisible by flush latency")
  require(sets >= 2)
  
  /**************************************************************************/
  /* Input/Output                                                           */
  /**************************************************************************/
  val io = IO(new AssociativeMemoryIO(ways, sets, t))


  /**************************************************************************/
  /* Simulation only                                                        */
  /**************************************************************************/
  val log = new Logger(c.lp.coreName, instName, verbose)

  // If previous command is flush AND current command is not flush then require that previous cycle was a full flush completion
  when(RegNext(io.flush) && !io.flush) {
    assert(RegNext(io.cplt) && (RegNext(io.flush)))
  }

  val ever_invalidated = RegNext(io.flush && io.cplt)

  when(io.resolve || io.write) {assert(ever_invalidated)}

  when(io.flush)   {assert(!io.resolve && !io.write)}
  when(io.resolve) {assert(!io.flush   && !io.write)}
  when(io.write)   {assert(!io.flush   && !io.resolve)}

  when(io.flush)   {log("Flush\n")}
  when(io.write)   {log("Write\n")}
  when(io.resolve)   {log("Resolve\n")}

  /**************************************************************************/
  /* Actual data storage                                                    */
  /**************************************************************************/
  val mem   = SyncReadMem (sets, Vec(ways, t))
  val valid = SyncReadMem (sets / flushLatency, Vec(flushLatency, Vec(ways, Bool())))

  /**************************************************************************/
  /* Victim selection                                                       */
  /**************************************************************************/
  val (victim, _) = Counter(0 until ways, enable = io.write, reset = io.flush)

  /**************************************************************************/
  /* Flush counter                                                          */
  /**************************************************************************/
  val (flush_idx, _) = Counter(0 until flushLatency, enable = io.flush, reset = io.flush)
  io.cplt := flush_idx === (flushLatency - 1).U

  /**************************************************************************/
  /* States                                                                 */
  /**************************************************************************/
  val s1_idx = RegNext(io.s0.idx)
  // s1_idx % flushLatency.U


  /**************************************************************************/
  /* Read and write of the data                                             */
  /**************************************************************************/

  val rdata = mem.readWrite(
    /*idx = */io.s0.idx,
    /*writeData = */VecInit.tabulate(ways) {way: Int => io.s0.wentry},
    /*mask = */(1.U << victim).asBools,
    /*en = */io.resolve || io.write,
    /*isWrite = */io.write
  )

  
  val rvalid = valid.readWrite(
    /*idx = */Mux(io.flush, flush_idx, io.s0.idx / flushLatency.U),
    /*writeData = */VecInit.tabulate(ways) {way: Int => VecInit.tabulate(flushLatency) {fidx: Int => Mux(io.flush, false.B, io.s0.valid)}},
    /*mask = */(1.U << victim).asBools,
    /*en = */io.resolve || io.write,
    /*isWrite = */io.write
  )

  io.s1.valid := rvalid(s1_idx % flushLatency.U)
}
