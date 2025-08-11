package armleocpu

import chisel3._
import chisel3.util._

object operation_type extends ChiselEnum {
  val load      = 0.U(2.W)
  val store     = 1.U(2.W)
  val execute   = 2.U(2.W)
}

class PMP(
  ccx: CCXParams
) extends Module {
  val io = IO(new Bundle {
    val addr              = Input (UInt(ccx.apLen.W))
    val operation_type    = Input (UInt(2.W)) // 0: load, 1: store, 2: execute
    val accessfault       = Output(Bool())
  })

  val csrRegs = IO(Input(new CsrRegsOutput(ccx = ccx)))

  
  // Helper: NAPOT mask calculation
  def napotMask(pmpaddr: UInt): UInt = {
    val base = pmpaddr | 1.U
    ~(base ^ (base + 1.U))
  }
  def napotMatch(addr: UInt, pmpaddr: UInt): Bool = {
    val mask = napotMask(pmpaddr)
    (addr & mask) === (pmpaddr & mask)
  }

  // Determine effective privilege for PMP (MPRV logic)
  val eff_priv = Wire(UInt(2.W))
  when(csrRegs.mprv && (io.operation_type =/= operation_type.execute)) {
    eff_priv := csrRegs.mpp // Use MPP for data access if MPRV is set and not instruction fetch
  }.otherwise {
    eff_priv := csrRegs.privilege
  }

  // Compute matches for all PMP entries
  val matches = Wire(Vec(ccx.pmpCount, Bool()))
  for (i <- 0 until ccx.pmpCount) {
    val cfg = csrRegs.pmp(i).pmpcfg
    val paddr = csrRegs.pmp(i).pmpaddr
    val aField = cfg.addressMatching

    val torMatch = if (i == 0) {
      (io.addr < (paddr << 2))
    } else {
      (io.addr >= (csrRegs.pmp(i-1).pmpaddr << 2)) && (io.addr < (paddr << 2))
    }
    val na4Match = (io.addr >= (paddr << 2)) && (io.addr < ((paddr + 1.U) << 2))
    val napotMatchRes = napotMatch(io.addr, paddr)

    matches(i) :=
      Mux(aField === 0.U, false.B, // OFF
      Mux(aField === 1.U, torMatch, // TOR
      Mux(aField === 2.U, na4Match, // NA4
      napotMatchRes))) // NAPOT
  }

  // Find the first matching PMP entry (lowest index wins)
  val matchIdx = PriorityEncoder(matches.asUInt)
  val anyMatch = matches.asUInt.orR

  // Select the R/W/X bits from the matching entry, or 0 if no match
  val selCfg = WireDefault(0.U.asTypeOf(new pmpcfg_t))
  when(anyMatch) {
    selCfg := csrRegs.pmp(matchIdx).pmpcfg
  }

  // Permission logic
  val allowed =
    (io.operation_type === operation_type.load    && selCfg.read) ||
    (io.operation_type === operation_type.store   && selCfg.write) ||
    (io.operation_type === operation_type.execute && selCfg.execute)

  // If no PMP entry matched, allow access if in M-mode, else deny
  val mMode = eff_priv === 3.U
  io.accessfault := !((anyMatch && allowed) || (!anyMatch && mMode))
  
  //io.accessfault := false.B // FIXME: PMP is broken for now. Place holder
}