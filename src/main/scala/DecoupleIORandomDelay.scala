package armleocpu

import chisel3._
import chisel3.util._
import chisel3.reflect._
import chisel3.util.random._

// This needs testing. What if the producer is not connected to a Module???
// What is the data mirror gonna return

object DecoupledIORandomStall {
  def apply[T <: DecoupledIO[T]](producer: T): (DecoupledIO[T], Bool, Bool) = {
    val result = new DecoupledIO(producer.bits.cloneType)


    // If producer is not valid then this cycle was a stall
    // If producer is valid and ready that means it was accepted
    // If producer is valid but not ready that means we are already not stalling and producer needs to be kept the same
    
    val increment = ((result.valid && result.ready) || !result.valid)
    val stall = (FibonacciLFSR.maxPeriod(16, reduction = XNOR, increment = increment) & 1.U)(0).asBool
    

    result.valid := !stall && producer.valid
    
    result.bits.asUInt := Mux(result.valid, producer.bits, FibonacciLFSR.maxPeriod(result.bits.asUInt.getWidth, reduction = XNOR, increment = increment))
    
    return (result, increment, stall)
  }
}