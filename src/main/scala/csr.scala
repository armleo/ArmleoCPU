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
  
  val sv39 = 0x8.U(4.W)
  // val sv48 = 0x9.U(4.W) Do we need it? Temporary disabled
}

class MemoryPrivilegeState(c: coreParams) extends Bundle {
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

class InterruptsInputs extends Bundle {
  val mtip = Bool()
  val stip = Bool()
  
  val meip = Bool()
  val seip = Bool()

  val msip = Bool()
  val ssip = Bool()
}

// Used to emulate hypervisor support
// Part of MSTATUS
class hyptrap_t extends Bundle {
  val tsr = Bool()
  val tvm = Bool()
  val  tw = Bool()
}

object csr_cmd extends ChiselEnum {
  val none, write, read, read_write, read_set, read_clear, interrupt, exception, mret, sret = Value
}

abstract class csr_register(c: coreParams) {

}

class csr_partial_map(c: coreParams) {

}

class csr_scratch_t(c: coreParams) {

}

class CSR(c: coreParams) extends Module {

  /**************************************************************************/
  /*                                                                        */
  /*                Input/Output                                            */
  /*                                                                        */
  /**************************************************************************/

  val mem_priv_o    = IO(Output (new MemoryPrivilegeState(c)))
  val instret_incr  = IO(Input  (Bool()))
  val hyptrap_o     = IO(Output (new hyptrap_t))
  val int           = IO(Input  (new InterruptsInputs))
  val int_pending_o = IO(Output (Bool()))

  val cmd           = IO(Input  (chiselTypeOf(csr_cmd.none)))
  val addr          = IO(Input  (UInt(12.W)))
  val epc           = IO(Input  (UInt(c.xLen.W)))
  val cause         = IO(Input  (UInt(c.xLen.W)))
  val in            = IO(Input  (UInt(c.xLen.W)))
  val out           = IO(Output (UInt(c.xLen.W)))
  val next_pc       = IO(Output (UInt(c.xLen.W)))
  val err           = IO(Output (Bool()))

  /**************************************************************************/
  /*                                                                        */
  /*                Signal declarations                                     */
  /*                                                                        */
  /**************************************************************************/

  val rmw_before          = Wire(UInt(c.xLen.W))
  val rmw_after           = Wire(UInt(c.xLen.W))

  val readwrite           = Wire(Bool())
  val read                = Wire(Bool())
  val write               = Wire(Bool())

  
  val exists              = Wire(Bool())
  val exc_int_error       = Wire(Bool())

  val accesslevel_invalid = Wire(Bool())
  val write_invalid       = Wire(Bool())
  val invalid             = Wire(Bool())

  /**************************************************************************/
  /*                                                                        */
  /*                State/CSR registers                                     */
  /*                                                                        */
  /**************************************************************************/

  val mtvec               = RegInit(c.mtvec_default.U)
  val stvec               = RegInit(c.stvec_default.U)
  
  val mem_priv_default    = 0.U.asTypeOf(new MemoryPrivilegeState(c))
  mem_priv_default.privilege := privilege_t.M
  val mem_priv            = RegInit(mem_priv_default)

  val spp                 = Reg(UInt(1.W))

  val hyptrap             = RegInit(0.U.asTypeOf(new hyptrap_t))
  hyptrap_o              := hyptrap

  val mpie                = Reg(Bool())
  val mie                 = Reg(Bool())
  val spie                = Reg(Bool())
  val sie                 = Reg(Bool())

  val mscratch            = Reg(UInt(c.xLen.W))
  val sscratch            = Reg(UInt(c.xLen.W))
  
  /**************************************************************************/
  /*                                                                        */
  /*                Combinational logic                                     */
  /*                                                                        */
  /**************************************************************************/
  

  readwrite           :=  cmd === csr_cmd.read_write ||
                          cmd === csr_cmd.read_set ||
                          cmd === csr_cmd.read_clear
  write               :=  cmd === csr_cmd.write || readwrite
  read                :=  cmd === csr_cmd.read  || readwrite

  accesslevel_invalid :=  (write || read) && (mem_priv.privilege  < addr(9, 8))
  write_invalid       :=  write           && (BigInt("11", 2).U === addr(11, 10))
  invalid             :=  (read || write) && (accesslevel_invalid | write_invalid | !exists)
  err                 :=  invalid || exc_int_error
  
  // holds read modify write operations first operand,
  // because for mip, sip value used for RMW sequence is
  // different from the value written to register
  // See 3.1.9 Machine Interrupt Registers (mip and mie) in RISC-V Privileged spec

  
}
