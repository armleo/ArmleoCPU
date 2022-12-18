package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

/**************************************************************************/
/*                                                                        */
/*               MemoryPrivilege related bundles and enums                */
/*                                                                        */
/**************************************************************************/

object privilege_t extends ChiselEnum {
  val USER = 0x0.U(2.W)
  val S = 0x1.U(2.W)
  val M = 0x3.U(2.W)
}

object  satp_mode_t extends ChiselEnum {
  val bare = 0x0.U(4.W)
  val sv39 = 0x8.U(4.W)
}

/**************************************************************************/
/*                                                                        */
/*               PMP related bundles                                      */
/*                                                                        */
/**************************************************************************/
/*
TODO: PMP: Implement
class pmpcfg_t extends Bundle {
  val lock            = Bool()
  val reserved        = UInt(2.W)
  val addressMatching = UInt(2.W)
  val execute         = Bool()
  val write           = Bool()
  val read            = Bool()
}

class csr_pmp_t(c: CoreParams) extends Bundle {
  val pmpcfg  = Vec(c.pmpCount, new pmpcfg_t)
  val pmpaddr = Vec(c.pmpCount, UInt(c.xLen.W))
}
*/


/**************************************************************************/
/*                                                                        */
/*               Interrupts related bundles                               */
/*                                                                        */
/**************************************************************************/

class InterruptsInputs extends Bundle {
  val mtip = Bool()
  val stip = Bool()
  
  val meip = Bool()
  val seip = Bool()

  val msip = Bool()
  val ssip = Bool()
}

/**************************************************************************/
/*                                                                        */
/*               CSR related enums and constants                          */
/*                                                                        */
/**************************************************************************/
object csr_cmd extends ChiselEnum {
  val none, write, read, read_write, read_set, read_clear, interrupt, exception, mret, sret = Value
}

class exc_code(c: CoreParams) extends ChiselEnum{
  val INTERRUPT = (1.U << c.xLen - 1)


  val MACHINE_SOFTWATE_INTERRUPT = ((1.U) | INTERRUPT)
  val SUPERVISOR_SOFTWATE_INTERRUPT = ((3.U) | INTERRUPT)

  val MACHINE_TIMER_INTERRUPT = ((5.U) | INTERRUPT)
  val SUPERVISOR_TIMER_INTERRUPT = ((7.U) | INTERRUPT)

  val MACHINE_EXTERNAL_INTERRUPT = ((9.U)| INTERRUPT)
  val SUPERVISOR_EXTERNAL_INTERRUPT = ((11.U) | INTERRUPT)
  
  val INSTR_MISALIGNED              = 0.U
  val INSTR_ACCESS_FAULT            = 1.U
  val INSTR_ILLEGAL                 = 2.U
  val BREAKPOINT                    = 3.U
  val LOAD_MISALIGNED               = 4.U
  val LOAD_ACCESS_FAULT             = 5.U
  val STORE_AMO_ADDRESS_MISALIGNED  = 6.U
  val STORE_AMO_ACCESS_FAULT        = 7.U
  val UCALL                         = 8.U
  val SCALL                         = 9.U
  val MCALL                         = 11.U
  val INSTR_PAGE_FAULT              = 12.U
  val LOAD_PAGE_FAULT               = 13.U
  val STORE_AMO_PAGE_FAULT          = 15.U
  // FIXME: Add the exception codes
}


class CsrRegsOutput(c: CoreParams) extends Bundle {
  /**************************************************************************/
  /*                                                                        */
  /*               Hypervisor trapping related                              */
  /*                                                                        */
  /**************************************************************************/
  val tsr = Bool()
  val tvm = Bool()
  val  tw = Bool()


  /**************************************************************************/
  /*                                                                        */
  /*               Memory privilege related                                 */
  /*                                                                        */
  /**************************************************************************/
  val privilege = chiselTypeOf(privilege_t.M)

  
  
  val mode = UInt(4.W)
  val ppn = UInt((c.apLen - c.pgoff_len).W)
  
  //val asid = UInt(16.W)

  val mprv = Bool()
  val mxr = Bool()
  val sum = Bool()
  val mpp = chiselTypeOf(privilege_t.M)

  def getVmSignals(): (Bool, UInt) = {
    val vm_privilege = Mux(((this.privilege === privilege_t.M) && this.mprv), this.mpp,  this.privilege)
    val vm_enabled = ((vm_privilege === privilege_t.S) || (vm_privilege === privilege_t.USER)) && (this.mode =/= satp_mode_t.bare)
    return (vm_enabled, vm_privilege)
  }

  // TODO: Add PMP signals
}


class CSR(c: CoreParams) extends Module {
  /**************************************************************************/
  /*                                                                        */
  /*                Input/Output                                            */
  /*                                                                        */
  /**************************************************************************/

  val regs_output   = IO(Output (new CsrRegsOutput(c)))
  val instret_incr  = IO(Input  (Bool()))
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

  // holds read modify write operations first operand,
  // because for mip, sip value used for RMW sequence is
  // different from the value written to register
  // See 3.1.9 Machine Interrupt Registers (mip and mie) in RISC-V Privileged spec

  val rmw_before          = Wire(UInt(c.xLen.W))

  
  val exists              = Wire(Bool())
  val exc_int_error       = Wire(Bool())

  /**************************************************************************/
  /*                                                                        */
  /*                State/CSR registers                                     */
  /*                                                                        */
  /**************************************************************************/

  val mtvec               = RegInit(c.mtvec_default.U(c.xLen.W))
  val stvec               = RegInit(c.stvec_default.U(c.xLen.W))
  
  val regs_output_default         = 0.U.asTypeOf(new CsrRegsOutput(c))
  regs_output_default.privilege  := privilege_t.M
  val regs                        = RegInit(regs_output_default)
  regs_output                    := regs

  val spp                 = Reg(UInt(1.W))

  val mpie                = Reg(Bool())
  val mie                 = Reg(Bool())
  val spie                = Reg(Bool())
  val sie                 = Reg(Bool())

  val mscratch            = Reg(UInt(c.xLen.W))
  val sscratch            = Reg(UInt(c.xLen.W))

  val mepc                = Reg(UInt(c.xLen.W))
  val sepc                = Reg(UInt(c.xLen.W))

  val mcause              = Reg(UInt(c.xLen.W))
  val scause              = Reg(UInt(c.xLen.W))

  val stval               = Reg(UInt(c.xLen.W))
  
  /**************************************************************************/
  /*                                                                        */
  /*                Counters state                                          */
  /*                                                                        */
  /**************************************************************************/
  
  val cycle_counter       = RegInit(0.U(64.W))
      cycle_counter      := cycle_counter + 1.U
  val instret_counter     = RegInit(0.U(64.W))
      instret_counter    := Mux(instret_incr, instret_counter + 1.U, instret_counter)

  /**************************************************************************/
  /*                                                                        */
  /*                Interrupt logic/state                                   */
  /*                                                                        */
  /**************************************************************************/
  
  val meie                = RegInit(false.B)
  val seie                = RegInit(false.B)
  val mtie                = RegInit(false.B)
  val stie                = RegInit(false.B)
  val msie                = RegInit(false.B)
  val ssie                = RegInit(false.B)

  val seip                = RegInit(false.B)
  val stip                = RegInit(false.B)
  val ssip                = RegInit(false.B)


  /**************************************************************************/
  /*                                                                        */
  /*                MISA                                                    */
  /*                                                                        */
  /**************************************************************************/
  
 val isa = Cat(
    "b0".U(1.W), // Z
    "b0".U(1.W), // Y
    "b0".U(1.W), // X
    "b0".U(1.W), // W
    "b0".U(1.W), // V
    "b1".U(1.W), // U - User mode, present
    "b0".U(1.W), // T
    "b1".U(1.W), // S - Supervisor mode, present
    "b0".U(1.W), // R
    "b0".U(1.W), // Q
    "b0".U(1.W), // P
    "b0".U(1.W), // O
    "b0".U(1.W), // N
    "b0".U(1.W), // M - Multiply/Divide, Present // TODO: Multiply/Divide in ISA
    "b0".U(1.W), // L
    "b0".U(1.W), // K
    "b0".U(1.W), // J
    "b1".U(1.W), // I - RV64I
    "b0".U(1.W), // H
    "b0".U(1.W), // G
    "b0".U(1.W), // F
    "b0".U(1.W), // E
    "b0".U(1.W), // D
    "b0".U(1.W), // C
    "b0".U(1.W), // B
    "b0".U(1.W)  // A // TODO: Atomic access in ISA
 ) | (("b10".U(2.W)) << (c.xLen - 3)) // MXLEN = 64, only valid value
  // FIXME: Check this value

  /**************************************************************************/
  /*                                                                        */
  /*                RMW logic                                               */
  /*                                                                        */
  /**************************************************************************/
  
  val readwrite       =  cmd === csr_cmd.read_write ||
                          cmd === csr_cmd.read_set ||
                          cmd === csr_cmd.read_clear
  val write               =  cmd === csr_cmd.write || readwrite
  val read                =  cmd === csr_cmd.read  || readwrite

  val accesslevel_invalid =  (write || read) && (regs.privilege  < addr(9, 8))
  val write_invalid       =  write           && (BigInt("11", 2).U === addr(11, 10))
  val invalid             =  (read || write) && (accesslevel_invalid | write_invalid | !exists)

  def calculate_rmw_after(): UInt = {
    val rmw_after = Wire(UInt(c.xLen.W))
    rmw_after := in
    when((cmd === csr_cmd.read_write) || (cmd === csr_cmd.write)) {
      rmw_after := in
    } .elsewhen (cmd === csr_cmd.read_set) {
      rmw_after := rmw_before | in
    } .elsewhen (cmd === csr_cmd.read_clear) {
      rmw_after := rmw_before & (~in)
    }

    rmw_after
  }

  /**************************************************************************/
  /*                                                                        */
  /*                Interrupt logic                                         */
  /*                                                                        */
  /**************************************************************************/
  

  val machine = regs.privilege === privilege_t.M
  val supervisor = regs.privilege === privilege_t.S

  val machine_supervisor = machine | supervisor

  val calculated_mie =
    (
        (machine)
        & mie
    ) | (!machine)

  val calculated_meie = calculated_mie & meie
  val calculated_mtie = calculated_mie & mtie
  val calculated_msie = calculated_mie & msie

  val calculated_sie =
    (
        (supervisor)
        & sie
    ) | (regs.privilege === privilege_t.USER)

  val calculated_seie = calculated_sie & seie
  val calculated_stie = calculated_sie & stie
  val calculated_ssie = calculated_sie & ssie

  
  val calculated_meip = int.meip
  val calculated_mtip = int.mtip
  val calculated_msip = int.msip

  val calculated_seip = int.seip | seip
  val calculated_stip = int.stip | stip
  val calculated_ssip = int.ssip | ssip

  int_pending_o := 
        (calculated_meip & calculated_meie) |
        (calculated_mtip & calculated_mtie) |
        (calculated_msip & calculated_msie) |
        (calculated_seip & calculated_seie) |
        (calculated_stip & calculated_stie) |
        (calculated_ssip & calculated_ssie);

  /**************************************************************************/
  /*                                                                        */
  /*                CSR Shorthands                                          */
  /*                                                                        */
  /**************************************************************************/
  
  def ro(ro_addr: UInt, value: UInt): Unit = {
    when(addr === ro_addr) {
      exists := true.B
      out := value

    }
  }

  def scratch(a: UInt, r: UInt): Unit = {
    when(addr === a) {
      exists := true.B
      out := r
      rmw_before := r
      when(!invalid && write) {
        r := calculate_rmw_after()
      }
    }
  }

  def partial(a: UInt, top: Int, bot: Int, w: UInt, r: UInt): Unit = {
    when(addr === a) {
      exists := true.B
      out := w
      rmw_before := w
      when(!invalid && write) {
        r := calculate_rmw_after()(top, bot)
      }
    }
  }

  def addr_reg(a: UInt, r: UInt): Unit = {
    when(addr === a) {
      exists := true.B
      out := r
      rmw_before := r
      when(!invalid && write) {
        // TODO: Is this an okay requirement?
        r := calculate_rmw_after() & ~(3.U(c.xLen.W))
      }
    }
  }

  def counter(a: UInt, ah: UInt, r: UInt): Unit = {
    when(addr === a) {
      exists := true.B
      out := r
      rmw_before := r
      when(!invalid && write) {
        calculate_rmw_after()
        r := calculate_rmw_after()
      }
    }
  }

  
  /**************************************************************************/
  /*                                                                        */
  /*                CSR internal combinational signals defaults             */
  /*                                                                        */
  /**************************************************************************/
  
  rmw_before := 0.U
  exists := false.B

  exc_int_error := true.B
  next_pc := mtvec
  out := 0.U
  
  /**************************************************************************/
  /*                                                                        */
  /*                Interrupt                                               */
  /*                                                                        */
  /**************************************************************************/
  when(cmd === csr_cmd.interrupt) {
    // Note: Order matters, checkout the interrupt priority in RISC-V Privileged Manual
    when( calculated_meip &  calculated_meie) { // MEI
        mcause := new exc_code(c).MACHINE_EXTERNAL_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_msip &  calculated_msie) { // MSI
        mcause := new exc_code(c).MACHINE_SOFTWATE_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_mtip &  calculated_mtie) { // MTI
        mcause := new exc_code(c).MACHINE_TIMER_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_seip &  calculated_seie) { // SEI
        mcause := new exc_code(c).SUPERVISOR_EXTERNAL_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_ssip &  calculated_ssie) { // SSI
        mcause := new exc_code(c).SUPERVISOR_SOFTWATE_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_stip &  calculated_stie) { // STI
        mcause := new exc_code(c).SUPERVISOR_TIMER_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .otherwise {
        exc_int_error := true.B
    }

    when(!exc_int_error) {
        mpie := mie
        mie := false.B
        regs.mpp := regs.privilege
        regs.privilege := privilege_t.M
        
        mepc := epc
        next_pc := mtvec
    }
  /**************************************************************************/
  /*                                                                        */
  /*                Exception                                               */
  /*                                                                        */
  /**************************************************************************/
  } .elsewhen(cmd === csr_cmd.exception) {
    mpie := mie
    mie := false.B
    regs.mpp := regs.privilege
    regs.privilege := privilege_t.M
    mepc := epc
    next_pc := mtvec
    mcause := cause
    exc_int_error := false.B
  /**************************************************************************/
  /*                                                                        */
  /*                MRET                                                    */
  /*                                                                        */
  /**************************************************************************/
  } .elsewhen (cmd === csr_cmd.mret) {
    assert(machine)
    mie := mpie
    mpie := true.B
    regs.mpp := privilege_t.USER
    when(regs.mpp =/= privilege_t.M) {
      regs.mprv := false.B
    }
    regs.privilege := regs.mpp

    next_pc := mepc
    exc_int_error := false.B
  /**************************************************************************/
  /*                                                                        */
  /*                SRET                                                    */
  /*                                                                        */
  /**************************************************************************/
  } .elsewhen (cmd === csr_cmd.sret) {
    assert(supervisor || machine)
    assert(!regs.tsr)
    sie := spie
    spie := privilege_t.S
    regs.privilege := spp
    spp := privilege_t.USER

    // SPP cant hold machine mode, so no need to check for SPP to be MACHINE
    // No need to check the privilege because SPEC does not require any conditions
    // For clearing the MPRV
    regs.mprv := false.B
    next_pc := sepc
    exc_int_error := false.B
  /**************************************************************************/
  /*                                                                        */
  /*                FIXME: ECALL                                            */
  /*                                                                        */
  /**************************************************************************/
  /**************************************************************************/
  /*                                                                        */
  /*                FIXME: EBREAK                                           */
  /*                                                                        */
  /**************************************************************************/
  /**************************************************************************/
  /*                                                                        */
  /*                FIXME: DEBUG Registers                                  */
  /*                                                                        */
  /**************************************************************************/
  /**************************************************************************/
  /*                                                                        */
  /*                Read/Write                                              */
  /*                                                                        */
  /**************************************************************************/
  } .elsewhen (read || write) {
    exc_int_error := false.B
    
    /**************************************************************************/
    /*                Read only regs                                          */
    /**************************************************************************/
    
    ro      ("hF11".U, c.mvendorid.U)
    ro      ("hF12".U, c.marchid.U)
    ro      ("hF13".U, c.mimpid.U)
    ro      ("hF14".U, c.mhartid.U)
    ro      ("hF15".U, c.mconfigptr.U)
    
    /**************************************************************************/
    /*                mstatus                                                 */
    /**************************************************************************/
    
    val mstatus = Cat(
      "h0".U(9.W), // Padding SD, 8 empty bits
      regs.tsr, regs.tw, regs.tvm, // trap enable bits
      regs.mxr, regs.sum, regs.mprv, //machine privilege mode
      "b00"U(2.W), "b00"U(2.W), // xs, fs
      regs.mpp, "b00"U(2.W), spp, // MPP, 2 bits (reserved by spec), SPP
      mpie, "b0"U(1.W), spie, "b0"U(1.W),
      mie, "b0"U(1.W), sie, "b0"U(1.W)
    )
    partial ("h300".U, 1, 1,    mstatus,      sie)
    partial ("h300".U, 3, 3,    mstatus,      mie)
    partial ("h300".U, 5, 5,    mstatus,      spie)
    partial ("h300".U, 7, 7,    mstatus,      mpie)
    partial ("h300".U, 8, 8,    mstatus,      spp)
    partial ("h300".U, 12, 11,  mstatus, regs.mpp)
    partial ("h300".U, 17, 17,  mstatus, regs.mprv)
    partial ("h300".U, 18, 18,  mstatus, regs.sum)
    partial ("h300".U, 19, 19,  mstatus, regs.mxr)
    partial ("h300".U, 20, 20,  mstatus, regs.tvm)
    partial ("h300".U, 21, 21,  mstatus, regs.tw)
    partial ("h300".U, 22, 22,  mstatus, regs.tsr)

    /**************************************************************************/
    /*                misa                                                    */
    /**************************************************************************/
    
    // TODO: F ISA writable
    ro      ("h301".U, isa)

    /**************************************************************************/
    /*                machine level interrupt/exception related               */
    /**************************************************************************/
    
    addr_reg("h305".U, mtvec)
    scratch ("h340".U, mscratch)
    addr_reg("h341".U, mepc)
    scratch ("h342".U, mcause)
    ro      ("h343".U, 0.U) // MTVAL is hardwired to zero, in case it never gets written
    ro      ("h302".U, 0.U) // MEDELEG
    ro      ("h303".U, 0.U) // MIDELEG
    
    // MIE:
    val mie_reg = Cat(
      meie, 0.U(1.W), seie, 0.U(1.W),
      mtie, 0.U(1.W), stie, 0.U(1.W),
      msie, 0.U(1.W), ssie, 0.U(1.W),
    )
    partial("h304".U, 11, 11, mie_reg, meie)
    partial("h304".U,  9,  9, mie_reg, seie)
    partial("h304".U,  7,  7, mie_reg, mtie)
    partial("h304".U,  5,  5, mie_reg, stie)
    partial("h304".U,  3,  3, mie_reg, msie)
    partial("h304".U,  1,  1, mie_reg, ssie)
    


    /**************************************************************************/
    /*                Supervisor level interrupt/exception related            */
    /**************************************************************************/
    addr_reg("h105".U, stvec)
    scratch ("h140".U, sscratch)
    addr_reg("h141".U, sepc)
    scratch ("h142".U, scause)
    scratch ("h143".U, stval)
    // STVAL is NOT hardwired to zero
    // because it needs to be written by M-level bootloader
    // To pass the interrupt/exceptions


    val satp = Cat(regs.mode, "h0".U(16.W), regs.ppn)

    partial ("h180".U, 63, 60, satp, regs.mode)
    partial ("h180".U, 43, 0,  satp, regs.ppn)
    when(addr === "h180".U) {
      exists := !(regs.tvm && supervisor)
    }

    val sie_reg = Cat(
      0.U(2.W), seie, 0.U(1.W),
      0.U(2.W), stie, 0.U(1.W),
      0.U(2.W), ssie, 0.U(1.W),
    )
    partial("h104".U,  9,  9, sie_reg, seie)
    partial("h104".U,  5,  5, sie_reg, stie)
    partial("h104".U,  1,  1, sie_reg, ssie)


    val sstatus = Cat(
      "h0".U(9.W), // Padding SD, 8 empty bits
      "b000".U(3.W), // trap enable bits
      regs.mxr, regs.sum, 0.U(1.W), //machine privilege mode
      "b00"U(2.W), "b00"U(2.W), // xs, fs
      "b00"U(2.W), "b00"U(2.W), spp, // MPP, 2 bits (reserved by spec), SPP
      "b00"U(2.W), spie, "b0"U(1.W),
      "b00"U(2.W), sie, "b0"U(1.W)
    )
    partial ("h100".U, 1, 1,    sstatus,          sie)
    partial ("h100".U, 5, 5,    sstatus,          spie)
    partial ("h100".U, 8, 8,    sstatus,          spp)
    partial ("h100".U, 18, 18,  sstatus, regs.sum)
    partial ("h100".U, 19, 19,  sstatus, regs.mxr)

    when(addr === "h344".U) { // MIP
      exists := true.B

      rmw_before := Cat(
        int.meip, 0.U(1.W), seip, 0.U(1.W),
        int.mtip, 0.U(1.W), stip, 0.U(1.W),
        int.msip, 0.U(1.W), ssip, 0.U(1.W)
      )

      out := rmw_before

      when(!invalid && write) {
        // csr_mip_m*ip is read only
        // From machine mode, s*ip can be both cleared and set
        calculate_rmw_after()
        seip :=calculate_rmw_after()(9)
        stip :=calculate_rmw_after()(5)
        ssip :=calculate_rmw_after()(1)
      }
    }

    when(addr === "h144".U) { // SIP
      exists := true.B

      rmw_before := Cat(
        0.U(2.W), seip, 0.U(1.W),
        0.U(2.W), stip, 0.U(1.W),
        0.U(2.W), ssip, 0.U(1.W)
      )

      out := rmw_before

      when(!invalid && write) {
        // s*ip can only be cleared
        when(calculate_rmw_after()(9) === 0.U) {
          seip := calculate_rmw_after()(9)
        }
        when(calculate_rmw_after()(5) === 0.U) {
          stip := calculate_rmw_after()(5)
        }
        when(calculate_rmw_after()(1) === 0.U) {
          ssip := calculate_rmw_after()(1)
        }
      }
    }

    
    /**************************************************************************/
    /*                Counters                                                 */
    /**************************************************************************/
    
    ro      ("h306".U, 0.U) // mcounteren
    ro      ("h106".U, 0.U) // scounteren

    // TODO: Test coverage of this
    counter ("hB00".U, "hB80".U, cycle_counter)
    counter ("hB02".U, "hB82".U, instret_counter)
    
    // TODO: Proper HPM events support
    when((addr >= "hB03".U) && (addr >= "hB1F".U)) { // HPM Counters
      exists := true.B
    }
    when((addr >= "h323".U) && (addr >= "h33F".U)) { // HPM Event Counters
      exists := true.B
    }
  } .elsewhen(cmd === csr_cmd.none) {
    exc_int_error := 0.U
  } .otherwise {
    exc_int_error := 1.U
  }

  // If CSR read write is invalid (see invalid's definition above)
  // Or the exception/interrupt logic returned error then signal it to pipeline
  err :=  invalid || exc_int_error
  
}

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object CSRGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new CSR(new CoreParams()))))
}

