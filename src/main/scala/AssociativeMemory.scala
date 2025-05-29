package armleocpu

import chisel3._
import chisel3.util._

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
  ccx: CCXParameters,
) extends CCXModule(ccx = ccx) {
  /**************************************************************************/
  /* Parameters                                                             */
  /**************************************************************************/
  
  require(isPow2(ways))
  require(isPow2(sets))

  require(flushLatency >= 2, "FLush latency need to be higher than one cycle")
  require(isPow2(flushLatency))
  require((sets % flushLatency) == 0, "Set count needs to be divisible by flush latency")
  require(sets >= 2)
  
  /**************************************************************************/
  /* Input/Output                                                           */
  /**************************************************************************/
  val io = IO(new AssociativeMemoryIO(ways = ways, sets = sets, t = t))


  /**************************************************************************/
  /* Simulation only                                                        */
  /**************************************************************************/

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
  // s1_idx % flushLatency.U is the selection in the late stage


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
    /*mask = */((1.U << victim) | Fill(ways, io.flush)).asBools,
    /*en = */io.resolve || io.write || io.flush,
    /*isWrite = */io.write || io.flush
  )

  io.s1.valid := rvalid(s1_idx % flushLatency.U)
}
