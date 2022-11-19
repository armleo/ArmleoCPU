package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

object privilege_t extends ChiselEnum {
  val U = 0x0.U(2.W)
  val S = 0x1.U(2.W)
  val M = 0x3.U(2.W)
}

object  satp_mode_t extends ChiselEnum {
  val bare = 0x0.U(1.W)
  val sv32 = 0x1.U(1.W)
  /*
  val sv39 = 0x8.U(4.W)
  val sv48 = 0x9.U(4.W)
  */
  // TODO: SV64 xLen based switching
}

class MemoryPrivilegeState(c: coreParams) extends Bundle{
  val privilege = chiselTypeOf(privilege_t.U)

  
  // TODO: xLen based mode/ppn/asid switching
  // val mode = UInt(4.W)
  // val ppn = UInt(44.W)
  val mode = UInt(1.W)
  val ppn = UInt(22.W)
  
  //val asid = UInt(16.W)

  val mprv = Bool()
  val mxr = Bool()
  val sum = Bool()
  val mpp = chiselTypeOf(privilege_t.U)
}

class CSR(c: coreParams) extends Module{
  val mem_priv = IO(Output(new MemoryPrivilegeState(c)))
  
  val instret_incr = IO(Input(Bool()))

  
}
