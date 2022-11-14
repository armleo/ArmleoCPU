package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

object pagefault_cmd {
  val none = 0x0.U(2.W)
  val load = 0x1.U(2.W)
  val store = 0x2.U(2.W)
  val execute = 0x3.U(2.W)
  val enum_type = UInt(2.W)
}

 

class Pagefault(val c: coreParams) extends Module {
  val mem_priv  = IO(new MemoryPrivilegeState(c))

  val cmd       = IO(Input(pagefault_cmd.enum_type))
}