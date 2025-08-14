package armleocpu

import chisel3._
import chisel3.util._

object AssociativeMemoryOp {
  val resolve = 1.U(2.W)
  val write   = 2.U(2.W)
  val flush   = 3.U(2.W)
}


class AssociativeMemoryParameters(val sets:Int, val ways:Int) {}


class AssociativeMemoryRes[T <: Data](t: T, p: AssociativeMemoryParameters) extends Bundle {
  import p._
  val readEntry = Output(Vec(ways, t))
  val valid     = Output(Vec(ways, Bool()))
}

class AssociativeMemoryReq[T <: Data](t: T, p: AssociativeMemoryParameters) extends Bundle {
  import p._
  val valid     = Input(Bool())
  val op        = Input(UInt(2.W))
  
  val idx       = Input(UInt(log2Ceil(sets).W))
  val writeEntryValid = Input(Bool())
  val writeEntry      = Input(t)
}

class AssociativeMemoryIO[T <: Data](t: T, p: AssociativeMemoryParameters) extends Bundle {
  import p._

  val req = new AssociativeMemoryReq(t = t, p = p)
  val res = new AssociativeMemoryRes(t = t, p = p)
}

class AssociativeMemory[T <: Data](
  // Primary parameters
  t: T,
  p: AssociativeMemoryParameters,
  // Simulation only
  ccx: CCXParams,
) extends CCXModule(ccx = ccx) {

  import p._
  /**************************************************************************/
  /* Parameters                                                             */
  /**************************************************************************/
  
  require(isPow2(ways))
  require(isPow2(sets))
  require(sets >= 2)
  
  /**************************************************************************/
  /* Input/Output                                                           */
  /**************************************************************************/
  val io = IO(new AssociativeMemoryIO(t = t, p = p))


  val flush   = io.req.valid && io.req.op === AssociativeMemoryOp.flush
  val write   = io.req.valid && io.req.op === AssociativeMemoryOp.write
  val resolve = io.req.valid && io.req.op === AssociativeMemoryOp.resolve

  /**************************************************************************/
  /* Simulation only                                                        */
  /**************************************************************************/

  when(flush)   {log(cf"Flush\n")}
  when(write)   {log(cf"Write idx: ${io.req.idx} entry: ${io.req.writeEntry}")}
  when(resolve) {log(cf"Resolve idx: ${io.req.idx}")}

  /**************************************************************************/
  /* Actual data storage                                                    */
  /**************************************************************************/
  val mem = SRAM.masked(sets, Vec(ways, t), 0, 0, 1)

  /**************************************************************************/
  /* States                                                                 */
  /**************************************************************************/
  val (victim, _) = Counter(
    0 until ways,
    enable = io.req.valid && io.req.op === AssociativeMemoryOp.write,
    reset = io.req.valid && io.req.op === AssociativeMemoryOp.flush
  )
  val s1_idx = RegNext(io.req.idx)

  /**************************************************************************/
  /* Read and write of the data                                             */
  /**************************************************************************/

  val entryValid     = RegInit(VecInit.tabulate(sets)      {idx: Int => 0.U(ways.W)})
  val regEntryValid    = Reg(entryValid(0).cloneType)

  when(io.req.valid && io.req.op === AssociativeMemoryOp.flush)    {
    entryValid                    := 0.U.asTypeOf(entryValid)
  } .elsewhen(io.req.valid && io.req.op === AssociativeMemoryOp.resolve)  {
    regEntryValid                   := entryValid(io.req.idx)
  } .elsewhen(io.req.valid && io.req.op === AssociativeMemoryOp.write)    {
    entryValid(io.req.idx)(victim) := io.req.writeEntryValid
  }

  io.res.valid := regEntryValid.asBools

  mem.readwritePorts(0).enable := write || resolve
  mem.readwritePorts(0).address := io.req.idx
  mem.readwritePorts(0).mask.get := (1.U << victim).asBools
  mem.readwritePorts(0).isWrite := write
  mem.readwritePorts(0).writeData := VecInit.tabulate(ways) {way: Int => io.req.writeEntry}

  io.res.readEntry := mem.readwritePorts(0).readData
}
