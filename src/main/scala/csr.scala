package armleocpu

import chisel3._
import chisel3.util._


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
	val PRV_U = 0x0.U(2.W)
	val PRV_S = 0x1.U(2.W)
	val PRV_M = 0x3.U(2.W)

	// temp replacement for mstatus
	val csr_mtvec    = 0x305.U(12.W)
	val csr_prv      = 0x307.U(12.W) // custom
	val csr_mscratch = 0x340.U(12.W)
	val csr_mepc     = 0x341.U(12.W)
	val csr_mcause   = 0x342.U(12.W)
	val csr_mtval    = 0x343.U(12.W)
	//val csr_mip      = 0x344.U(12.W)
	
	//val csr_mpie     = 
	// custom CSRs
  // mcie goes low when interrupt happens
  // mcie is enable signal for interrupt
	val csr_mcie      = 0x381.U(12.W) // custom

  // mcatp is pointer to address translation table
  // that has same layout as satp, but instead
  // of using satp, satp traps, l0bootloader sets mcatp
  // if csr status is written, write will trap, so
  // level 0 bootloader can disable or enable mcatp acording
  // to requirments of supervisor
	val csr_mcatp     = 0x380.U(12.W) // custom
  // everything else traps
	// so l0bootloader can handle it
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
  
}

/*
	val PRV = RegInit(PRV_M)
	val MTVEC = RegInit(0.U(32.W))

*/