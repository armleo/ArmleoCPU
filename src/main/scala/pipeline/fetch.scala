package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._
// FETCH
class FetchUop(implicit ccx: CCXParams) extends PrefetchUop {
  val instr               = UInt(ccx.iLen.W)
  val ifetchPagefault     = Bool()
  val ifetchAccessFault   = Bool()
  
  override def toPrintable: Printable = {
    cf"  $instr%x @ $pc%x; " + 
    cf"  ifetchPagefault   : $ifetchPagefault%x;" +
    cf"  ifetchAccessFault : $ifetchAccessFault%x"
  }
  // TODO: Reduce the printable
  // TODO: Add Instruction PTE storage for RVFI
}


class PipelineControlIO(implicit val ccx: CCXParams) extends Bundle {
    val kill              = Input(Bool())
    val jump              = Input(Bool())
    val flush             = Input(Bool())
    val busy              = Output(Bool())

    val newPc            = Input(UInt(ccx.apLen.W)) // It can be either physical or virtual address
}


class Fetch(implicit ccx: CCXParams) extends CCXModule {
  /**************************************************************************/
  /*  Interface                                                             */
  /**************************************************************************/
  val ctrl              = IO(new PipelineControlIO) // Pipeline command interface form control unit
  

  val cacheResp         = IO(Flipped(new CacheResp)) // Cache response channel (it requires some input as the memory stage might use this to rollback commands that it ordered)
  val in             = IO(Flipped(DecoupledIO(new PrefetchUop))) // From prefetch to fetch bus
  val out             = IO(DecoupledIO(new FetchUop)) // Fetch to decode bus
  val dynRegs           = IO(Input(new DynamicROCsrRegisters)) // For reset vectors
  val csr               = IO(Input(new CsrRegsOutput)) // From CSR

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


  /**************************************************************************/
  /*  State                                                                 */
  /**************************************************************************/
  val holdUop       = Reg(new FetchUop)
  val holdUopValid  = RegInit(false.B)
  val csrRegs       = Reg(new CsrRegsOutput)

  //val ppn  = Reg(chiselTypeOf(itlb.io.s0.wentry.ppn))
    // TLB.s1 is only valid in output stage, but not in refill.
    // Q: Why?
    // A: Turns out not every memory cell supports keeping output after read
    //    Yep, that is literally why we are wasting preciouse chip area... Portability

  in.ready                   := false.B
  out.valid                   := false.B
  out.bits                    := holdUop

  when(ctrl.kill || ctrl.flush || ctrl.jump) {
    in.ready := true.B
    holdUopValid := false.B
    holdUop := DontCare
    log(cf"KILL")
  } .elsewhen(holdUopValid) {
    out.bits := holdUop
    out.valid := true.B
    log(cf"HOLD     out: ${out.bits}")
    // FIXME: UOP_I.READY
  } .elsewhen(cacheResp.valid) {
    out.bits.viewAsSupertype(new PrefetchUop) := in.bits
    out.bits.ifetchAccessFault                := cacheResp.accessfault
    out.bits.ifetchPagefault                  := cacheResp.pagefault
    out.bits.instr       := cacheResp.rdata.asTypeOf(Vec(ccx.xLen / ccx.iLen, UInt(ccx.iLen.W)))(out.bits.pc(log2Ceil(ccx.xLen / ccx.iLen) + log2Ceil(ccx.iLen / 8) - 1,log2Ceil(ccx.iLen / 8)))
    holdUop                      := out.bits

    when(!out.ready) {
      holdUopValid  := true.B
      in.ready     := true.B
      log(cf"ACCEPTED out: ${out.bits}")

    } .otherwise {
      in.ready     := false.B
      log(cf"HOLDREQ  out: ${out.bits}")
    }

    assert(in.valid, "Cache request ready, while valid is low")
    in.ready := true.B
  } .otherwise {
    //log(cf"NOP")
    in.ready := false.B
  }

  // Never written
  cacheResp.writeData := VecInit(Seq.fill(ccx.xLenBytes)(0.U(8.W)))
  cacheResp.writeMask := 0.U(ccx.xLenBytes.W)

  cacheResp.read := in.valid
  cacheResp.write := false.B

  cacheResp.atomicRead := false.B
  cacheResp.atomicWrite := false.B

  ctrl.busy := cacheResp.valid || in.valid || out.valid
}

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation

object FetchGenerator extends App {
  
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Fetch()(new CCXParams()))))
}

