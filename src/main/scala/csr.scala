package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

object privilege_t extends ChiselEnum {
  val USER = 0x0.U(2.W)
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
  val privilege = chiselTypeOf(privilege_t.M)

  
  // TODO: xLen based mode/ppn/asid switching
  // val mode = UInt(4.W)
  // val ppn = UInt(44.W)
  val mode = UInt(1.W)
  val ppn = UInt(22.W)
  
  //val asid = UInt(16.W)

  val mprv = Bool()
  val mxr = Bool()
  val sum = Bool()
  val mpp = chiselTypeOf(privilege_t.M)
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

class exc_code(c: coreParams) extends ChiselEnum{
  val INTERRUPT = (1.U << c.xLen - 1)


  val MACHINE_SOFTWATE_INTERRUPT = ((1.U) | INTERRUPT)
  val SUPERVISOR_SOFTWATE_INTERRUPT = ((3.U) | INTERRUPT)

  val MACHINE_TIMER_INTERRUPT = ((5.U) | INTERRUPT)
  val SUPERVISOR_TIMER_INTERRUPT = ((7.U) | INTERRUPT)

  val MACHINE_EXTERNAL_INTERRUPT = ((9.U)| INTERRUPT)
  val SUPERVISOR_EXTERNAL_INTERRUPT = ((11.U) | INTERRUPT)
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

  // holds read modify write operations first operand,
  // because for mip, sip value used for RMW sequence is
  // different from the value written to register
  // See 3.1.9 Machine Interrupt Registers (mip and mie) in RISC-V Privileged spec

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

  val mtvec               = RegInit(c.mtvec_default.U(c.xLen.W))
  val stvec               = RegInit(c.stvec_default.U(c.xLen.W))
  
  val mem_priv_default    = 0.U.asTypeOf(new MemoryPrivilegeState(c))
  mem_priv_default.privilege := privilege_t.M
  val mem_priv            = RegInit(mem_priv_default)
  mem_priv_o             := mem_priv

  val spp                 = Reg(UInt(1.W))

  val hyptrap             = RegInit(0.U.asTypeOf(new hyptrap_t))
  hyptrap_o              := hyptrap

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
  
  val cycle_counter       = RegInit(0.U(c.xLen.W))
      cycle_counter      := cycle_counter + 1.U
  val instret_counter     = RegInit(0.U(c.xLen.W))
      instret_counter    := Mux(instret_incr, instret_counter + 1.U, instret_counter)


  val meie                = RegInit(false.B)
  val seie                = RegInit(false.B)
  val mtie                = RegInit(false.B)
  val stie                = RegInit(false.B)
  val msie                = RegInit(false.B)
  val ssie                = RegInit(false.B)

  val seip                = RegInit(false.B)
  val stip                = RegInit(false.B)
  val ssip                = RegInit(false.B)


 val isa = Cat(
    "b01".U(2.W), // MXLEN = 32, only valid value // TODO: RV64 Fix mxlen to be muxed depending on xLen
    "b0000".U(4.W), // Reserved
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
    "b1".U(1.W), // I - RV32I
    "b0".U(1.W), // H
    "b0".U(1.W), // G
    "b0".U(1.W), // F
    "b0".U(1.W), // E
    "b0".U(1.W), // D
    "b0".U(1.W), // C
    "b0".U(1.W), // B
    "b0".U(1.W)  // A // TODO: Atomic access in ISA
 )
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
  
  


  rmw_after := in
  when((cmd === csr_cmd.read_write) || (cmd === csr_cmd.write)) {
    rmw_after := in
  } .elsewhen (cmd === csr_cmd.read_set) {
    rmw_after := rmw_before | in
  } .elsewhen (cmd === csr_cmd.read_clear) {
    rmw_after := rmw_before & (~in)
  }

  val machine = mem_priv.privilege === privilege_t.M
  val supervisor = mem_priv.privilege === privilege_t.S

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
    ) | (mem_priv.privilege === privilege_t.USER)

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
        r := rmw_after
      }
    }
  }

  def partial(a: UInt, top: Int, bot: Int, w: UInt, r: UInt): Unit = {
    when(addr === a) {
      exists := true.B
      out := w
      rmw_before := w
      when(!invalid && write) {
        r := rmw_after(top, bot)
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
        r := rmw_after & ~(3.U(c.xLen))
      }
    }
  }

  
  exc_int_error := true.B
  next_pc := mtvec
  out := 0.U
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
        mem_priv.mpp := mem_priv.privilege
        mem_priv.privilege := privilege_t.M
        
        mepc := epc
        next_pc := mtvec
    }
  } .elsewhen ((cmd === csr_cmd.mret) && machine) {
    mie := mpie
    mpie := true.B
    mem_priv.mpp := privilege_t.USER
    when(mem_priv.mpp =/= privilege_t.M) {
      mem_priv.mprv := false.B
    }
    mem_priv.privilege := mem_priv.mpp

    next_pc := mepc
    exc_int_error := false.B
  } .elsewhen ((cmd === csr_cmd.sret) && supervisor) {
    sie := spie
    spie := privilege_t.S
    mem_priv.privilege := spp
    spp := privilege_t.USER

    // SPP cant hold machine mode, so no need to check for SPP to be MACHINE
    // No need to check the privilege because SPEC does not require any conditions
    // For clearing the MPRV
    mem_priv.mprv := false.B
    next_pc := sepc
    exc_int_error := false.B
  } .elsewhen(cmd === csr_cmd.exception) {
    mpie := mie
    mie := false.B
    mem_priv.mpp := mem_priv.privilege
    mem_priv.privilege := privilege_t.M
    mepc := epc
    next_pc := mtvec
    mcause := cause
    exc_int_error := false.B
  } .elsewhen (read || write) {
    exc_int_error := false.B
    
    ro      ("hF11".U, c.mvendorid.U)
    ro      ("hF12".U, c.marchid.U)
    ro      ("hF13".U, c.mimpid.U)
    ro      ("hF14".U, c.mhartid.U)
    ro      ("hF15".U, c.mconfigptr.U)
    


    val mstatus = Cat(
      "h0".U(9.W), // Padding SD, 8 empty bits
      hyptrap.tsr, hyptrap.tw, hyptrap.tvm, // trap enable bits
      mem_priv.mxr, mem_priv.sum, mem_priv.mprv, //machine privilege mode
      "b00"U(2.W), "b00"U(2.W), // xs, fs
      mem_priv.mpp, "b00"U(2.W), spp, // MPP, 2 bits (reserved by spec), SPP
      mpie, "b0"U(1.W), spie, "b0"U(1.W),
      mie, "b0"U(1.W), sie, "b0"U(1.W)
    )
    partial ("h300".U, 1, 1,    mstatus,          sie)
    partial ("h300".U, 3, 3,    mstatus,          mie)
    partial ("h300".U, 5, 5,    mstatus,          spie)
    partial ("h300".U, 7, 7,    mstatus,          mpie)
    partial ("h300".U, 8, 8,    mstatus,          spp)
    partial ("h300".U, 12, 11,  mstatus, mem_priv.mpp)
    partial ("h300".U, 17, 17,  mstatus, mem_priv.mprv)
    partial ("h300".U, 18, 18,  mstatus, mem_priv.sum)
    partial ("h300".U, 19, 19,  mstatus, mem_priv.mxr)
    partial ("h300".U, 20, 20,  mstatus, hyptrap .tvm)
    partial ("h300".U, 21, 21,  mstatus, hyptrap .tw)
    partial ("h300".U, 22, 22,  mstatus, hyptrap .tsr)

    // MSTATUSH
    // Added in v1.12 of privileged spec
    ro      ("h310".U, 0.U)

    // TODO: F ISA writable
    ro      ("h301".U, isa)

    ro      ("h306".U, 0.U) // mcounteren
    ro      ("h106".U, 0.U) // scounteren

    addr_reg("h305".U, mtvec)
    scratch ("h340".U, mscratch)
    addr_reg("h341".U, mepc)
    scratch ("h340".U, mcause)
    ro      ("h343".U, 0.U) // MTVAL is hardwired to zero, in case it never gets written
    
    // Supervisor
    addr_reg("h105".U, stvec)
    scratch ("h140".U, sscratch)
    addr_reg("h141".U, sepc)
    scratch ("h142".U, scause)
    scratch ("h143".U, stval)
    // STVAL is NOT hardwired to zero
    // because it needs to be written by M-level bootloader
    // To pass the interrupt/exceptions

    
    if(c.xLen == 32) {
      scratch ("hB00".U, cycle_counter)
      scratch ("hB80".U, cycle_counter(63, 32))
      scratch ("hB00".U, instret_counter)
      scratch ("hB82".U, instret_counter(63, 32))
    } else {
      scratch ("hB00".U, cycle_counter)
      scratch ("hB00".U, instret_counter)
    }

    ro      ("h302".U, 0.U) // MEDELEG
    ro      ("h303".U, 0.U) // MIDELEG

    // TODO: Add SATP
  }
  
  rmw_before := 0.U
  exists := false.B
}

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object CSRGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new CSR(new coreParams()))))
}

