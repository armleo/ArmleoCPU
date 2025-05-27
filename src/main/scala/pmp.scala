
package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._
import chisel3.experimental.dataview._


object operation_type extends ChiselEnum {
  val load      = 0.U(2.W)
  val store     = 1.U(2.W)
  val execute   = 2.U(2.W)
}

class PMP(
  c: CoreParams,
  verbose: Boolean = true, instName: String = "pmp  ",
) extends Module {
  val io = IO(new Bundle {
    val addr              = Input (UInt(c.apLen.W))
    val privilege_level   = Input (UInt(2.W))
    val operation_type    = Input (UInt(2.W))

    val accessfault       = Output(Bool())
  })

  val csrRegs = IO(Input(new CsrRegsOutput(c = c)))

  val matched = Wire(Bool())
  val allowed = Wire(Bool())

  matched := false.B
  allowed := false.B
  /*
  for(i <- 0 until c.pmpCount) {
    when(!matched) {
      when(csrRegs.pmp(i).pmpcfg.addressMatching === 3.U) {
        // Size of 2 ** (PopCount(~csrRegs.pmp(i).pmpaddr) + 2) in bytes to match
        when(io.addr >= ((csrRegs.pmp(i).pmpaddr << 2) & unmask)) {
          
        }
         + (2 ** (PopCount(~csrRegs.pmp(i).pmpaddr) + 2))
      }
    }
  }
    */
  /*
  csrRegs.pmpindexWhere
  */
}