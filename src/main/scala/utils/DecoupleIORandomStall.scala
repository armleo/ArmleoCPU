package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._



class DecoupledIORandomStall[M <: Record](t: M, seed:Option[BigInt] = Some(1)) extends Module {
  val in = IO(Flipped(DecoupledIO(Input(t.cloneType))))
  val out = IO(DecoupledIO(Output(t.cloneType)))
  val increment = IO(Output(Bool()))
  val stall = IO(Output(Bool()))
  // If producer is not valid then this cycle was a stall
  // If producer is valid and ready that means it was accepted
  // If producer is valid but not ready that means we are already not stalling and producer needs to be kept the same
  
  increment := ((out.valid && out.ready) || !out.valid)
  stall := (FibonacciLFSR.maxPeriod(16, reduction = XNOR, increment = increment) & 1.U)(0).asBool

  out.valid := !stall && in.valid

  in.ready := out.ready && !stall
  
  out.bits := Mux(out.valid, in.bits.asUInt, FibonacciLFSR.maxPeriod(out.bits.asUInt.getWidth, reduction = XNOR, increment = increment)).asTypeOf(out.bits)
}