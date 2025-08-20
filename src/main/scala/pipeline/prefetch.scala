package armleocpu

import chisel3._
import chisel3.util._

// PREFETCH
class PrefetchUop(implicit val ccx: CCXParams) extends Bundle {
  val pc                  = UInt(ccx.apLen.W)
  val pcPlus4           = UInt(ccx.apLen.W)

  override def toPrintable: Printable = {cf"@ $pc%x\n"}
}




class Prefetch(implicit ccx: CCXParams) extends CCXModule {
  /**************************************************************************/
  /*  Interface                                                             */
  /**************************************************************************/

  val ctrl              = IO(new PipelineControlIO)
  val out               = IO(DecoupledIO(new PrefetchUop))

  val cacheReq          = IO(new CacheReq)

  val dynRegs           = IO(Input(new DynamicROCsrRegisters))
  val csr               = IO(Input(new CsrRegsOutput))

  /**************************************************************************/
  /*  State                                                                 */
  /**************************************************************************/
  val pc                    = Reg(UInt(ccx.apLen.W))
  val pcPlus4               = Reg(UInt(ccx.apLen.W))
  val pcRestart             = RegInit(true.B) // Next pc should be PC register
  val active                = RegInit(false.B)

  cacheReq.valid            := false.B
  cacheReq.bits.vaddr       := Mux(pcRestart, pc, pcPlus4)
  cacheReq.bits.read        := true.B
  cacheReq.bits.write       := false.B
  cacheReq.bits.atomicRead  := false.B
  cacheReq.bits.atomicWrite := false.B

  out.bits.pc               := pc
  out.bits.pcPlus4          := pcPlus4

  val stall                 = WireDefault(true.B)

  val outReg                = Reg(new PrefetchUop)
  val outRegValid           = RegInit(false.B)

  when(!stall) {
    cacheReq.valid       := true.B
    
    when(cacheReq.ready) {
      active                  := true.B
      outReg.pc               := pc
      outReg.pcPlus4          := pcPlus4
      outRegValid             := true.B
      pcRestart               := false.B

      pc                      := pcPlus4
      pcPlus4                 := pcPlus4 + 4.U
      log(cf"PREFETCH: active from 0x${pc}%x and accepted by ICACHE")
    } .otherwise {
      pcRestart               := true.B // Prevent the PC to be incremented until the cache has accepted the request
      active                  := false.B
      log(cf"PREFETCH: active from 0x${pc}%x rejected")
    }
  }

  out.bits  := outReg
  out.valid := outRegValid


  when(ctrl.kill) {
    stall     := true.B
    pc        := ctrl.newPc
    pcPlus4   := ctrl.newPc + 4.U
    pcRestart := true.B
    active    := false.B // The cache operation has been killed
  } .elsewhen(ctrl.jump || ctrl.flush) {
    pc        := ctrl.newPc
    pcPlus4   := ctrl.newPc + 4.U
    pcRestart := true.B
    stall     := !ctrl.jump
    active    := false.B // The cache operation has been killed
  } .otherwise {
    stall     := !(!active || (out.valid && out.ready))
  }

  
  ctrl.busy   := active || out.valid

  when(reset.asBool) {
    pc := dynRegs.resetVector
    pcPlus4 := dynRegs.resetVector + 4.U
    pcRestart := true.B
  }
}

