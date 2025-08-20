package armleocpu


import chisel3._
import chisel3.util._

import chisel3.util._
import chisel3.experimental.dataview._

import Instructions._


/** Output of Execute stage (same as yours) */
class execute_uop_t(implicit ccx: CCXParams) extends decode_uop_t with ExecResult {
}

/** Precomputed inputs every unit can use (Single Responsibility: Execute preps these once) */
class ExecCommonIn(implicit ccx: CCXParams) extends Bundle {
  val valid  = Bool()
  val uop    = new decode_uop_t

  // TODO: Handle below
  val rs1    = UInt(ccx.xLen.W)
  val rs2    = UInt(ccx.xLen.W)
  val simm12 = SInt(ccx.xLen.W)
  val shamt  = UInt(ccx.xLenLog2.W)
  val rs2Sh  = UInt(ccx.xLenLog2.W) // shift amount from rs2
}

/** Result fields that execution units produce */
trait ExecResult extends Bundle {
  implicit val ccx: CCXParams
  val aluOut      = SInt(ccx.xLen.W)
  val branchTaken = Bool()
}

/** Per-unit result (kept small and uniform) */
class ExecUnitOut(implicit val ccx: CCXParams) extends ExecResult {
  val handled      = Bool() // this unit recognized and handled the instruction
}

/** All execution units implement this Module interface (Dependency Inversion) */
abstract class ExecUnit(implicit ccx: CCXParams) extends CCXModule {
  val in  = IO(Input(new ExecCommonIn))
  val out = IO(Output(new ExecUnitOut))


  def execute_debug(instr: String): Unit = {
    when(in.valid) {
      log(cf"$instr instr=0x${in.uop.instr}%x, pc=0x${in.uop.pc}%x")
    }
  }

  def handle(instr: String): Unit = {
    execute_debug(instr)
    out.handled := true.B
  }
}

class Execute(implicit ccx: CCXParams) extends CCXModule {
  val ctrl              = IO(new PipelineControlIO) // Pipeline command interface form control unit

  val in         = IO(Flipped(DecoupledIO(new decode_uop_t)))
  val out         = IO(DecoupledIO(new execute_uop_t))
  

  val outBits        = Reg(new execute_uop_t)
  val outValid       = RegInit(false.B)

  out.valid       := outValid
  out.bits        := outBits
  
  /**************************************************************************/
  /*                Decode pipeline combinational signals                   */
  /**************************************************************************/
  val kill                = ctrl.kill || ctrl.flush || ctrl.jump
  ctrl.busy               := out.valid

  // The regfile has unknown register state for address 0
  // This is by-design
  // So instead we MUX zero at execute stage if its read from 0th register

  val execute_rs1 = Mux(in.bits.instr(19, 15) =/= 0.U, in.bits.rs1, 0.U)
  val execute_rs2 = Mux(in.bits.instr(24, 20) =/= 0.U, in.bits.rs2, 0.U)

  in.ready := false.B
  
  // FIXME: Create the exec units
  
  when(!outValid || (outValid && out.ready) || kill) {
    when(in.valid && !kill) {
      in.ready := true.B
      outBits.viewAsSupertype(chiselTypeOf(in.bits)) := in.bits
      outValid        := true.B
    } .otherwise { // Decode has no instruction. Or killed
      outValid := false.B
    }
  } .elsewhen(kill) {
    outValid := false.B
    log(cf"Instr killed")
  }
}