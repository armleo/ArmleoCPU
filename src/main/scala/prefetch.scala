package armleocpu

import chisel3._
import chisel3.util._


// PREFETCH
class prefetch_uop_t(val ccx: CCXParams) extends Bundle {
  val pc                  = UInt(ccx.apLen.W)
  val pc_plus_4           = UInt(ccx.apLen.W)
}




class Prefetch(ccx: CCXParams) extends CCXModule(ccx = ccx) {
  /**************************************************************************/
  /*  Interface                                                             */
  /**************************************************************************/

  val ctrl              = IO(new PipelineControlIO(ccx))
  val uop_o             = IO(DecoupledIO(new prefetch_uop_t(ccx)))
  val CacheS0           = IO(new CacheS0IO(ccx))
  val dynRegs           = IO(Input(new DynamicROCsrRegisters(ccx)))
  val csr               = IO(Input(new CsrRegsOutput(ccx)))

  /**************************************************************************/
  /*  State                                                                 */
  /**************************************************************************/
  val pc                      = Reg(UInt(ccx.apLen.W))
  val pc_plus_4               = Reg(UInt(ccx.apLen.W))
  val pc_restart              = RegInit(true.B) // Next pc should be PC register
  val requestAcceptedByCache  = RegInit(false.B)

  CacheS0.valid       := false.B
  CacheS0.bits.vaddr  := Mux(pc_restart, pc, pc_plus_4)
  uop_o.bits.pc         := pc
  uop_o.bits.pc_plus_4  := pc_plus_4

  val newRequestAllowed = WireDefault(false.B)

  when(ctrl.kill) {
    newRequestAllowed := false.B
    pc                := ctrl.newPc
    pc_plus_4         := ctrl.newPcPlus4
    pc_restart        := true.B
    requestAcceptedByCache := false.B // The cache operation has been killed
  } .elsewhen(ctrl.jump || ctrl.flush) {
    pc                  := ctrl.newPc
    pc_plus_4           := ctrl.newPcPlus4
    pc_restart          := true.B
    newRequestAllowed   := ctrl.jump
    requestAcceptedByCache := false.B // The cache operation has been killed
  } .otherwise {
    newRequestAllowed := !requestAcceptedByCache || (uop_o.valid && uop_o.ready)
  }

  val uop_reg       = Reg(new prefetch_uop_t(ccx))
  val uop_reg_valid = RegInit(false.B)

  when(newRequestAllowed) {
    CacheS0.valid       := true.B
    when(!pc_restart) {
      pc                        := pc_plus_4
      pc_plus_4                 := pc_plus_4 + 4.U
    }
    when(CacheS0.ready) {
      requestAcceptedByCache    := true.B
      uop_reg.pc                := pc
      uop_reg.pc_plus_4         := pc_plus_4
      uop_reg_valid             := true.B
    } .otherwise {
      pc_restart                 := true.B // Prevent the PC to be incremented until the cache has accepted the request
    }
  }

  uop_o.bits  := uop_reg
  uop_o.valid := uop_reg_valid

  when(reset.asBool) {
    pc := dynRegs.resetVector
    pc_plus_4 := dynRegs.resetVector + 4.U
    pc_restart := true.B
  }
}

