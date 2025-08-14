package armleocpu

import chisel3._
import chisel3.util._

class AssociativeMemoryIO[T <: Data](ways: Int, sets: Int, t: T) extends Bundle {
  val flush       = Input (Bool())
  val cplt        = Output(Bool())

  val resolve     = Input(Bool())
  val write       = Input(Bool())

  val s0 = new Bundle {
    val valid     = Input(Bool())

    val idx       = Input(UInt(log2Ceil(sets).W))
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

  // Simulation only
  ccx: CCXParams,
) extends CCXModule(ccx = ccx) {
  /**************************************************************************/
  /* Parameters                                                             */
  /**************************************************************************/
  
  require(isPow2(ways))
  require(isPow2(sets))
  require(sets >= 2)
  
  /**************************************************************************/
  /* Input/Output                                                           */
  /**************************************************************************/
  val io = IO(new AssociativeMemoryIO(ways = ways, sets = sets, t = t))


  /**************************************************************************/
  /* Simulation only                                                        */
  /**************************************************************************/

  
  when(io.flush)   {assert(!io.resolve && !io.write)}
  when(io.resolve) {assert(!io.flush   && !io.write)}
  when(io.write)   {assert(!io.flush   && !io.resolve)}

  when(io.flush)   {log(cf"Flush\n")}
  when(io.write)   {log(cf"Write idx: ${io.s0.idx} entry: ${io.s0.wentry}")}
  when(io.resolve)   {log(cf"Resolve idx: ${io.s0.idx}")}

  /**************************************************************************/
  /* Actual data storage                                                    */
  /**************************************************************************/
  val mem   = SyncReadMem (sets, Vec(ways, t))

  /**************************************************************************/
  /* Victim selection                                                       */
  /**************************************************************************/
  val (victim, _) = Counter(0 until ways, enable = io.write, reset = io.flush)

  
  

  /**************************************************************************/
  /* States                                                                 */
  /**************************************************************************/
  val s1_idx = RegNext(io.s0.idx)
  // s1_idx % flushLatency.U is the selection in the late stage


  /**************************************************************************/
  /* Read and write of the data                                             */
  /**************************************************************************/


  val valid     = RegInit(VecInit.tabulate(sets)      {idx: Int => 0.U(ways.W)})
  val rvalid    = Reg(valid(0).cloneType)
  when(io.flush) {valid := 0.U.asTypeOf(valid)}
  when(io.resolve) {rvalid := valid(io.s0.idx)}
  when(io.write) {valid(io.s0.idx)(victim)}

  io.s1.valid := rvalid.asBools
  io.cplt := true.B

  val rdata = mem.readWrite(
    /*idx = */io.s0.idx,
    /*writeData = */VecInit.tabulate(ways) {way: Int => io.s0.wentry},
    /*mask = */(1.U << victim).asBools,
    /*en = */io.resolve || io.write,
    /*isWrite = */io.write
  )

  io.s1.rentry := rdata
}
