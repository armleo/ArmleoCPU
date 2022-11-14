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

/*
object Cause {
  val InstAddrMisaligned  = 0.U
  val InstAccessFault     = 1.U
  val IllegalInst         = 2.U
  val Breakpoint          = 3.U
  val LoadAddrMisaligned  = 4.U
  val LoadAccessFault     = 5.U
  val StoreAddrMisaligned = 6.U
  val StoreAccessFault    = 7.U
  val Ecall               = 8.U
  //9,10,11 is ecall
  val InstPageFault        = 12.U
  val LoadPageFault        = 13.U
  // reserved              = 14.U
  val StorePageFault       = 15.U
}


object CSR {
  // no command
  val N = 0.U(3.W)
  // write, set, clear
  val W = 1.U(3.W)
  val S = 2.U(3.W)
  val C = 3.U(3.W)

  // private command
  val P = 4.U(3.W)

  // Supports machine & user modes

}


class CSR extends Module{
  val io = IO(new Bundle{
    val stall = Input(Bool())
    val cmd   = Input(UInt(3.W))
    val in    = Input(UInt(32.W))
    val out   = Output(UInt(32.W))
    // Expection
    val pc       = Input(UInt(32.W))
    val addr     = Input(UInt(32.W))
    val inst     = Input(UInt(32.W))
    val illegal  = Input(Bool())
    val st_type  = Input(UInt(2.W))
    val ld_type  = Input(UInt(3.W))
    val pc_check = Input(Bool())
    val inst_access_fault = Input(Bool())
    val access_fault	  = Input(Bool())
    val inst_page_fault   = Input(Bool())
    val page_fault        = Input(Bool())
    val expt     = Output(Bool())
    val evec     = Output(UInt(32.W))
    val epc      = Output(UInt(32.W))
  })
  
}*/
