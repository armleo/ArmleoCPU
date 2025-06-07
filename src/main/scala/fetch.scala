package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._
import chisel3.experimental.dataview._
// FETCH
class fetch_uop_t(ccx: CCXParams) extends prefetch_uop_t(ccx) {
  val instr               = UInt(ccx.iLen.W)
  val ifetch_pagefault   = Bool()
  val ifetch_accessfault = Bool()
  
  // TODO: Add Instruction PTE storage for RVFI
}


class PipelineControlIO(ccx: CCXParams) extends Bundle {
    val kill              = Input(Bool())
    val jump              = Input(Bool())
    val flush             = Input(Bool())
    val busy              = Output(Bool())

    val newPc            = Input(UInt(ccx.apLen.W)) // It can be either physical or virtual address
    val newPcPlus4       = Input(UInt(ccx.apLen.W))
}


class Fetch(ccx: CCXParams) extends CCXModule(ccx = ccx) {
  /**************************************************************************/
  /*  Interface                                                             */
  /**************************************************************************/
  val ctrl              = IO(new PipelineControlIO(ccx)) // Pipeline command interface form control unit
  val CacheS1           = IO(Flipped(new CacheS1IO(ccx))) // Cache response channel (it requires some input as the memory stage might use this to rollback commands that it ordered)
  val uop_i             = IO(DecoupledIO(new prefetch_uop_t(ccx))) // From prefetch to fetch bus
  val uop_o             = IO(DecoupledIO(new fetch_uop_t(ccx))) // Fetch to decode bus
  val dynRegs           = IO(Input(new DynamicROCsrRegisters(ccx))) // For reset vectors
  val csr               = IO(Input(new CsrRegsOutput(ccx))) // From CSR

  /**************************************************************************/
  /*  Submodules                                                            */
  /**************************************************************************/

  //val pagefault = Module(new Pagefault(c = c))
  // FIXME: Pagefault
  // FIXME: Accessfault
  // FIXME: PMA
  // FIXME: PMP
  
  // TODO: Add PTE storage for RVFI
  /**************************************************************************/
  /*  Combinational declarations                                            */
  /**************************************************************************/

  val uop_comb = Wire(new fetch_uop_t(ccx))


  /**************************************************************************/
  /*  State                                                                 */
  /**************************************************************************/
  val hold_uop              = Reg(new fetch_uop_t(ccx))
  val hold_uop_valid        = RegInit(false.B)
  val csrRegs               = Reg(new CsrRegsOutput(ccx))

  //val ppn  = Reg(chiselTypeOf(itlb.io.s0.wentry.ppn))
    // TLB.s1 is only valid in output stage, but not in refill.
    // Q: Why?
    // A: Turns out not every memory cell supports keeping output after read
    //    Yep, that is literally why we are wasting preciouse chip area... Portability

  uop_i.ready                   := false.B
  uop_o.valid                   := false.B
  uop_o.bits                    := hold_uop


  when(hold_uop_valid) {
    uop_o.bits := hold_uop
    uop_o.valid := true.B
    log(cf"HOLD     uop_o: ${uop_o.bits}")
    // FIXME: UOP_I.READY
  } .elsewhen(CacheS1.valid) {
    uop_o.bits                    := uop_i.bits
    uop_o.bits.ifetch_accessfault := CacheS1.accessfault
    uop_o.bits.ifetch_pagefault   := CacheS1.pagefault
    uop_o.bits.instr              := CacheS1.rdata.asTypeOf(Vec(ccx.xLen / ccx.iLen, UInt(ccx.iLen.W)))(uop_o.bits.pc(log2Ceil(ccx.xLen / ccx.iLen) + log2Ceil(ccx.iLen / 8) - 1,log2Ceil(ccx.iLen / 8)))
    hold_uop                      := uop_o.bits
    when(!uop_o.ready) {
      hold_uop_valid  := true.B
      uop_i.ready     := true.B
      log(cf"ACCEPTED uop_o: ${uop_o.bits}")

    } .otherwise {
      uop_i.ready     := false.B
      log(cf"HOLDREQ  uop_o: ${uop_o.bits}")
    }
  } .otherwise {
    log(cf"NOP")
    uop_i.ready := false.B
  }

  // Never written
  CacheS1.writeData := VecInit(Seq.fill(ccx.xLenBytes)(0.U(8.W)))
  CacheS1.writeMask := 0.U(ccx.xLenBytes.W)

  CacheS1.read := uop_i.valid
  CacheS1.write := false.B
}

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation

object FetchGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Fetch(new CCXParams()))))
}

