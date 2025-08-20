package armleocpu

import chisel3._
import chisel3.util._

/** 
 * A Decoupled register slice (1-element skid buffer).
 * Decouples ready path while keeping throughput = 1 per cycle.
 */
class RegSlice[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(gen))
    val out = Decoupled(gen)
  })

  // Internal storage
  val dataReg  = Reg(gen)
  val fullReg  = RegInit(false.B)

  // Output side
  io.out.valid := fullReg || io.in.valid
  io.out.bits  := Mux(fullReg, dataReg, io.in.bits)

  // Input side ready
  io.in.ready := !fullReg

  when(io.in.fire && !io.out.ready) {
    // Input accepted but output not ready -> store
    dataReg := io.in.bits
    fullReg := true.B
  } .elsewhen(io.out.fire && fullReg) {
    // Output consumed the stored entry
    fullReg := false.B
  }
}
