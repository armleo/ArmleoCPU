package armleocpu

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

/**************************************************************************/
/*                                                                        */
/*               MemoryPrivilege related bundles and enums                */
/*                                                                        */
/**************************************************************************/

object Privilege extends ChiselEnum {
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

class CsrPmpCfg extends Bundle {
  val lock            = Bool()
  val reserved        = UInt(2.W)
  val addressMatching = UInt(2.W)
  val execute         = Bool()
  val write           = Bool()
  val read            = Bool()
}

class CsrPmp(implicit val ccx: CCXParams) extends Bundle {
  val pmpcfg  = new CsrPmpCfg
  val pmpaddr = UInt(ccx.xLen.W)
}



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

class exc_code(implicit val ccx: CCXParams) extends ChiselEnum{
  val INTERRUPT = (1.U << ccx.xLen - 1)


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

class CsrRegs(implicit val ccx: CCXParams) extends Bundle {
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
  val priv = chiselTypeOf(Privilege.M)
  
  
  val mode = UInt(4.W)
  val ppn = UInt(44.W)
  //val asid = UInt(16.W)

  val mprv = Bool()
  val mxr = Bool()
  val sum = Bool()
  val mpp = chiselTypeOf(Privilege.M)

  /**************************************************************************/
  /*                                                                        */
  /*               PMA/PMP                                                  */
  /*                                                                        */
  /**************************************************************************/
  val pmp = Vec(ccx.pmpCount, new CsrPmp)
}


class CsrRegsOutput(implicit ccx: CCXParams) extends CsrRegs {
  val vmPrivilege = UInt(2.W)
  val vmEnabled = Bool()
}

class SlicedCounter64IO extends Bundle {
  val incr = Input(Bool())               // increment enable
  val set  = Flipped(Valid(UInt(64.W)))  // direct assignment
  val out  = Output(UInt(64.W))          // current value
}

// 64-bit sliced counter (same-cycle ripple carry) with assign support
class SlicedCounter64(sliceBits: Int = 16) extends Module {
  require(64 % sliceBits == 0, "sliceBits must divide 64")

  val io = IO(new SlicedCounter64IO)

  val slices = 64 / sliceBits
  val segs   = Seq.fill(slices)(RegInit(0.U(sliceBits.W)))

  // increment logic
  val next0  = segs(0) + io.incr.asUInt
  val c0     = io.incr && (next0 === 0.U)

  when (io.set.valid) {
    // assign slices from io.set.bits
    for (i <- 0 until slices) {
      segs(i) := io.set.bits((i+1)*sliceBits-1, i*sliceBits)
    }
  } .otherwise {
    // normal increment mode
    segs(0) := next0
    var carry = c0
    for (i <- 1 until slices) {
      val en   = carry
      val next = segs(i) + en.asUInt
      when (en) { segs(i) := next }
      carry = en && (next === 0.U)
    }
  }

  io.out := segs.reverse.reduce(Cat(_, _))
}

class CSR(implicit ccx: CCXParams) extends CCXModule {

  // For reset vectors
  val dynRegs       = IO(Input(new DynamicROCsrRegisters))
  val staticRegs    = IO(Input(new StaticCsrRegisters))

  /**************************************************************************/
  /*                                                                        */
  /*                Input/Output                                            */
  /*                                                                        */
  /**************************************************************************/
  val io = IO(new Bundle {
    val regsOut           = Output (new CsrRegsOutput)
    val int               = Input  (new InterruptsInputs)

    // To retirement unit
    val instRetIncr       = Input  (Bool())
    val interruptPending  = Output (Bool())

    val cmd           = Input  (chiselTypeOf(csr_cmd.none))
    val addr          = Input  (UInt(12.W))
    val epc           = Input  (UInt(ccx.xLen.W))
    val cause         = Input  (UInt(ccx.xLen.W))
    val in            = Input  (UInt(ccx.xLen.W))
    val out           = Output (UInt(ccx.xLen.W))
    val next_pc       = Output (UInt(ccx.xLen.W))
    val err           = Output (Bool())
  })
  

  /**************************************************************************/
  /*                                                                        */
  /*                RMW variables                                           */
  /*                                                                        */
  /**************************************************************************/

  // holds read modify write operations first operand,
  // because for mip, sip value used for RMW sequence is
  // different from the value written to register
  // See 3.1.9 Machine Interrupt Registers (mip and mie) in RISC-V Privileged spec

  val rmw_before          = Wire(UInt(ccx.xLen.W))

  
  val exists              = Wire(Bool())
  val exc_int_error       = Wire(Bool())

  

  /**************************************************************************/
  /*                                                                        */
  /*                Reset values                                            */
  /*                                                                        */
  /**************************************************************************/
  val regsReset         = 0.U.asTypeOf(new CsrRegs)
  regsReset.priv        := Privilege.M

  for(i <- 0 until ccx.pmpCount) {
    regsReset.pmp(i).pmpcfg := staticRegs.pmpcfg_default(i).asTypeOf(new CsrPmpCfg)
    regsReset.pmp(i).pmpaddr := staticRegs.pmpaddr_default(i)
  }

  /**************************************************************************/
  /*                                                                        */
  /*                State/CSR registers                                     */
  /*                                                                        */
  /**************************************************************************/
  val regs                        = RegInit(regsReset)
  io.regsOut.viewAsSupertype(regs.cloneType)                       := regs
  io.regsOut.vmPrivilege := Mux(((regs.priv === Privilege.M) && regs.mprv), regs.mpp,  regs.priv)
  io.regsOut.vmEnabled := ((io.regsOut.vmPrivilege === Privilege.S) || (io.regsOut.vmPrivilege === Privilege.USER)) && (regs.mode =/= satp_mode_t.bare)

  val mtvec               = RegInit(dynRegs.mtVector)
  val stvec               = RegInit(dynRegs.stVector)
  
  val spp                 = Reg(UInt(1.W))

  val mpie                = Reg(Bool())
  val mie                 = Reg(Bool())
  val spie                = Reg(Bool())
  val sie                 = Reg(Bool())

  val mscratch            = Reg(UInt(ccx.xLen.W))
  val sscratch            = Reg(UInt(ccx.xLen.W))

  val mepc                = Reg(UInt(ccx.xLen.W))
  val sepc                = Reg(UInt(ccx.xLen.W))

  val mcause              = Reg(UInt(ccx.xLen.W))
  val scause              = Reg(UInt(ccx.xLen.W))

  val stval               = Reg(UInt(ccx.xLen.W))

  
  /**************************************************************************/
  /*                                                                        */
  /*                Counters state                                          */
  /*                                                                        */
  /**************************************************************************/
  

  val cycle   = Module(new SlicedCounter64(16))
  val instret = Module(new SlicedCounter64(16))
  
  cycle.io.incr := true.B
  cycle.io.set.valid := false.B
  cycle.io.set.bits := 0.U

  instret.io.incr := io.instRetIncr
  instret.io.set.valid := false.B
  instret.io.set.bits := 0.U
  
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
 ) | (("b10".U(2.W)) << (ccx.xLen - 3)) // MXLEN = 64, only valid value

  /**************************************************************************/
  /*                                                                        */
  /*                RMW logic                                               */
  /*                                                                        */
  /**************************************************************************/
  
  val readwrite       =  io.cmd === csr_cmd.read_write ||
                          io.cmd === csr_cmd.read_set ||
                          io.cmd === csr_cmd.read_clear
  val write               =  io.cmd === csr_cmd.write || readwrite
  val read                =  io.cmd === csr_cmd.read  || readwrite

  val accesslevel_invalid =  (write || read) && (regs.priv  < io.addr(9, 8))
  val write_invalid       =  write           && (BigInt("11", 2).U === io.addr(11, 10))
  val invalid             =  (read || write) && (accesslevel_invalid | write_invalid | !exists)

  def calculate_rmw_after(): UInt = {
    val rmw_after = Wire(UInt(ccx.xLen.W))
    rmw_after := io.in
    when((io.cmd === csr_cmd.read_write) || (io.cmd === csr_cmd.write)) {
      rmw_after := io.in
    } .elsewhen (io.cmd === csr_cmd.read_set) {
      rmw_after := rmw_before | io.in
    } .elsewhen (io.cmd === csr_cmd.read_clear) {
      rmw_after := rmw_before & (~io.in)
    }

    rmw_after
  }

  /**************************************************************************/
  /*                                                                        */
  /*                Interrupt logic                                         */
  /*                                                                        */
  /**************************************************************************/
  
  Mux(((regs.priv === Privilege.M) && regs.mprv), regs.mpp,  regs.priv)


  val machine = regs.priv === Privilege.M
  val supervisor = regs.priv === Privilege.S

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
    ) | (regs.priv === Privilege.USER)

  val calculated_seie = calculated_sie & seie
  val calculated_stie = calculated_sie & stie
  val calculated_ssie = calculated_sie & ssie

  
  val calculated_meip = io.int.meip
  val calculated_mtip = io.int.mtip
  val calculated_msip = io.int.msip

  val calculated_seip = io.int.seip | seip
  val calculated_stip = io.int.stip | stip
  val calculated_ssip = io.int.ssip | ssip

  io.interruptPending := 
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
    when(io.addr === ro_addr) {
      exists := true.B
      io.out := value

    }
  }

  def scratch(a: UInt, r: UInt): Unit = {
    when(io.addr === a) {
      exists := true.B
      io.out := r
      rmw_before := r
      when(!invalid && write) {
        r := calculate_rmw_after()
      }
    }
  }

  def partial(a: UInt, top: Int, bot: Int, w: UInt, r: UInt): Unit = {
    when(io.addr === a) {
      exists := true.B
      io.out := w
      rmw_before := w
      when(!invalid && write) {
        r := calculate_rmw_after()(top, bot)
      }
    }
  }

  def addr_reg(a: UInt, r: UInt): Unit = {
    when(io.addr === a) {
      exists := true.B
      io.out := r
      rmw_before := r
      when(!invalid && write) {
        // TODO: Is this an okay requirement?
        r := calculate_rmw_after() & ~(3.U(ccx.xLen.W))
      }
    }
  }

  def counter(a: UInt, counterio: SlicedCounter64IO): Unit = {
    when(io.addr === a) {
      exists := true.B
      io.out := counterio.out
      rmw_before := counterio.out
      when(!invalid && write) {
        calculate_rmw_after()
        counterio.set.bits := calculate_rmw_after()
        counterio.set.valid
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
  io.next_pc := mtvec
  io.out := 0.U
  
  /**************************************************************************/
  /*                                                                        */
  /*                Interrupt                                               */
  /*                                                                        */
  /**************************************************************************/
  when(io.cmd === csr_cmd.interrupt) {
    // Note: Order matters, checkout the interrupt priority in RISC-V Privileged Manual
    when( calculated_meip &  calculated_meie) { // MEI
        mcause := new exc_code().MACHINE_EXTERNAL_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_msip &  calculated_msie) { // MSI
        mcause := new exc_code().MACHINE_SOFTWATE_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_mtip &  calculated_mtie) { // MTI
        mcause := new exc_code().MACHINE_TIMER_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_seip &  calculated_seie) { // SEI
        mcause := new exc_code().SUPERVISOR_EXTERNAL_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_ssip &  calculated_ssie) { // SSI
        mcause := new exc_code().SUPERVISOR_SOFTWATE_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .elsewhen( calculated_stip &  calculated_stie) { // STI
        mcause := new exc_code().SUPERVISOR_TIMER_INTERRUPT; // Calculated by the CSR
        exc_int_error := false.B
    } .otherwise {
        exc_int_error := true.B
    }

    when(!exc_int_error) {
        mpie := mie
        mie := false.B
        regs.mpp := regs.priv
        regs.priv := Privilege.M
        
        mepc := io.epc
        io.next_pc := mtvec
    }
  /**************************************************************************/
  /*                                                                        */
  /*                Exception                                               */
  /*                                                                        */
  /**************************************************************************/
  } .elsewhen(io.cmd === csr_cmd.exception) {
    mpie := mie
    mie := false.B
    regs.mpp := regs.priv
    regs.priv := Privilege.M
    mepc := io.epc
    io.next_pc := mtvec
    mcause := io.cause
    exc_int_error := false.B
  /**************************************************************************/
  /*                                                                        */
  /*                MRET                                                    */
  /*                                                                        */
  /**************************************************************************/
  } .elsewhen (io.cmd === csr_cmd.mret) {
    assume(machine)
    mie := mpie
    mpie := true.B
    regs.mpp := Privilege.USER
    when(regs.mpp =/= Privilege.M) {
      regs.mprv := false.B
    }
    regs.priv := regs.mpp

    io.next_pc := mepc
    exc_int_error := false.B
  /**************************************************************************/
  /*                                                                        */
  /*                SRET                                                    */
  /*                                                                        */
  /**************************************************************************/
  } .elsewhen (io.cmd === csr_cmd.sret) {
    assume(supervisor || machine)
    assume(!regs.tsr)
    sie := spie
    spie := Privilege.S
    regs.priv := spp
    spp := Privilege.USER

    // SPP cant hold machine mode, so no need to check for SPP to be MACHINE
    // No need to check the privilege because SPEC does not require any conditions
    // For clearing the MPRV
    regs.mprv := false.B
    io.next_pc := sepc
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
    
    ro      ("hF11".U, dynRegs.mvendorid)
    ro      ("hF12".U, dynRegs.marchid)
    ro      ("hF13".U, dynRegs.mimpid)
    ro      ("hF14".U, dynRegs.mhartid)
    ro      ("hF15".U, dynRegs.mconfigptr)
    
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
    when(io.addr === "h180".U) {
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
    partial ("h100".U, 19, 19,  sstatus, regs.mxr) // FIXME: Check if this needs to be removed from sstatus

    when(io.addr === "h344".U) { // MIP
      exists := true.B

      rmw_before := Cat(
        io.int.meip, 0.U(1.W), seip, 0.U(1.W),
        io.int.mtip, 0.U(1.W), stip, 0.U(1.W),
        io.int.msip, 0.U(1.W), ssip, 0.U(1.W)
      )

      io.out := rmw_before

      when(!invalid && write) {
        // csr_mip_m*ip is read only
        // From machine mode, s*ip can be both cleared and set
        calculate_rmw_after()
        seip :=calculate_rmw_after()(9)
        stip :=calculate_rmw_after()(5)
        ssip :=calculate_rmw_after()(1)
      }
    }

    when(io.addr === "h144".U) { // SIP
      exists := true.B

      rmw_before := Cat(
        0.U(2.W), seip, 0.U(1.W),
        0.U(2.W), stip, 0.U(1.W),
        0.U(2.W), ssip, 0.U(1.W)
      )

      io.out := rmw_before

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
    counter ("hB00".U, cycle.io)
    counter ("hB02".U, instret.io)
    
    // TODO: Proper HPM events support
    when((io.addr >= "hB03".U) && (io.addr <= "hB1F".U)) { // HPM Counters
      exists := true.B
    }
    when((io.addr >= "h323".U) && (io.addr <= "h33F".U)) { // HPM Event Counters
      exists := true.B
    }
    // FIXME: Add the Lock bit check
    // FIXME: Correct the pmpcfg
    /*for(i <- 0 until 16 by 2) {
      partial("h3A0".U + i.U,  7, 0, pmpcfg(8 * i + 0), pmpcfg(8 * i + 0))
    }*/
  } .elsewhen(io.cmd === csr_cmd.none) {
    exc_int_error := 0.U
  } .otherwise {
    exc_int_error := 1.U
  }

  // If CSR read write is invalid (see invalid's definition above)
  // Or the exception/interrupt logic returned error then signal it to pipeline
  io.err :=  invalid || exc_int_error
  
}

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation

object CSRGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new CSR()(new CCXParams()))))
}
