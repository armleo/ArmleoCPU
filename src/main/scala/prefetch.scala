package armleocpu

import chisel3._
import chisel3.util._


// PREFETCH
class prefetch_uop_t(val ccx: CCXParams) extends Bundle {
  val pc                  = UInt(ccx.apLen.W)
  val pc_plus_4           = UInt(ccx.apLen.W)

  override def toPrintable: Printable = {
    cf"  pc        : $pc%x\n" +
    cf"  pc_plus_4 : $pc_plus_4%x\n"
  }
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
  val requested               = RegInit(false.B)

  CacheS0.valid             := false.B
  CacheS0.bits.vaddr        := Mux(pc_restart, pc, pc_plus_4)
  CacheS0.bits.read         := true.B
  CacheS0.bits.write        := false.B
  CacheS0.bits.atomicRead   := false.B
  CacheS0.bits.atomicWrite  := false.B

  uop_o.bits.pc             := pc
  uop_o.bits.pc_plus_4      := pc_plus_4

  val newRequestAllowed = WireDefault(false.B)

  val uop_reg       = Reg(new prefetch_uop_t(ccx))
  val uop_reg_valid = RegInit(false.B)

  when(newRequestAllowed) {
    CacheS0.valid       := true.B
    
    when(CacheS0.ready) {
      requested                 := true.B
      uop_reg.pc                := pc
      uop_reg.pc_plus_4         := pc_plus_4
      uop_reg_valid             := true.B
      pc_restart                := false.B

      pc                        := pc_plus_4
      pc_plus_4                 := pc_plus_4 + 4.U
      log(cf"PREFETCH: Requested from 0x${pc}%x and accepted by ICACHE")
    } .otherwise {
      pc_restart                := true.B // Prevent the PC to be incremented until the cache has accepted the request
      requested                 := false.B
      log(cf"PREFETCH: Requested from 0x${pc}%x rejected")
    }
  }

  uop_o.bits  := uop_reg
  uop_o.valid := uop_reg_valid


  when(ctrl.kill) {
    newRequestAllowed := false.B
    pc                := ctrl.newPc
    pc_plus_4         := ctrl.newPc + 4.U
    pc_restart        := true.B
    requested         := false.B // The cache operation has been killed
  } .elsewhen(ctrl.jump || ctrl.flush) {
    pc                  := ctrl.newPc
    pc_plus_4           := ctrl.newPc + 4.U
    pc_restart          := true.B
    newRequestAllowed   := ctrl.jump
    requested           := false.B // The cache operation has been killed
  } .otherwise {
    newRequestAllowed := !requested || (uop_o.valid && uop_o.ready)
  }

  


  ctrl.busy   := requested || uop_o.valid

  when(reset.asBool) {
    pc := dynRegs.resetVector
    pc_plus_4 := dynRegs.resetVector + 4.U
    pc_restart := true.B
  }
}

