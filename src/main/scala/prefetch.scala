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
  val pc                    = Reg(UInt(ccx.apLen.W))
  val pc_plus_4             = Reg(UInt(ccx.apLen.W))
  val pc_restart            = RegInit(true.B) // Next pc should be PC register
  val active                = RegInit(false.B)


  ctrl.busy := active

  CacheS0.valid       := false.B

  when(!active || (uop_o.valid && uop_o.ready)) {
    active := false.B
    when(ctrl.kill) {
      // Register the PC and do not start any new requests
      pc            := ctrl.newPc
      pc_plus_4     := ctrl.newPcPlus4
      pc_restart    := true.B
    } .elsewhen(ctrl.jump) {
      // Register the PC and start new request
      pc                  := ctrl.newPc
      pc_plus_4           := ctrl.newPcPlus4
      nextPc              := ctrl.newPc
      pc_restart          := true.B
      CacheS0.valid       := true.B
      CacheS0.bits.vaddr  := nextPc
    } .elsewhen(ctrl.flush) {
      // Register the PC and start new request
    } .otherwise {
      
    }
  }
  
  when(active) {
    uop_o.valid           := true.B
    uop_o.bits.pc         := pc
    uop_o.bits.pc_plus_4  := pc_plus_4
  }


  when(reset.asBool) {
    pc := dynRegs.resetVector
    pc_plus_4 := dynRegs.resetVector + 4.U
    pc_restart := true.B
  }
}

