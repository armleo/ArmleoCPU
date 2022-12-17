/*
package armleocpu

import chisel3._
import chisel3.util._

// Untested

class ClearableQueue[T <: Data](gen: T,
                       entries: Int,
                       pipe: Boolean = false,
                       flow: Boolean = false)
                      (implicit compileOptions: chisel3.CompileOptions) extends Queue(gen = gen, entries = entries, pipe = pipe, flow = flow) {
  val clear_req = IO(Input(Bool()))

  def clear(): Unit = {
    clear_req := true.B
  }

  when(clear_req) {
    enq_ptr.value := 0.U
    deq_ptr.value := 0.U
    maybe_full := false.B
  }
}
*/