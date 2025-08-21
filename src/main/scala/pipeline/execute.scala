package armleocpu


import chisel3._
import chisel3.util._

import chisel3.util._
import chisel3.experimental.dataview._

import Instructions._


/** Output of Execute stage (same as yours) */
class ExecuteUop(implicit ccx: CCXParams) extends DecodeUop {
  val aluOut      = SInt(ccx.xLen.W)
  val branchTaken = Bool()
}

/** Precomputed inputs every unit can use (Single Responsibility: Execute preps these once) */
class ExecCommonIn(implicit ccx: CCXParams) extends Bundle {
  val valid  = Bool()
  val uop    = new DecodeUop
}


/** Per-unit result (kept small and uniform) */
class ExecUnitOut(implicit val ccx: CCXParams) extends Bundle {
  val aluOut      = SInt(ccx.xLen.W)
  val branchTaken = Bool()
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

  val in         = IO(Flipped(DecoupledIO(new DecodeUop)))
  val out         = IO(DecoupledIO(new ExecuteUop))
  

  val outBits        = Reg(new ExecuteUop)
  val outValid       = RegInit(false.B)

  out.valid       := outValid
  out.bits        := outBits
  
  /**************************************************************************/
  /*                Decode pipeline combinational signals                   */
  /*********************ExecUnitOut*****************************************************/
  val kill                = ctrl.kill || ctrl.flush || ctrl.jump
  ctrl.busy               := out.valid

  val rs1 = Mux(in.bits.instr(19, 15) =/= 0.U, in.bits.rs1, 0.U)
  val rs2 = Mux(in.bits.instr(24, 20) =/= 0.U, in.bits.rs2, 0.U)

  in.ready := false.B
  
  val units: Seq[ExecUnit] = Seq(Module(new ExecuteAluUnit), Module(new ExecuteBranchUnit), Module(new ExecuteJalrUnit), Module(new ExecuteLoadStoreUnit))
  units.foreach(f => {
    f.in.valid := in.valid
    f.in.uop := in.bits
  })
  val handled = units.map(_.out.handled)
  val anyHandled = VecInit(handled).asUInt.orR
  val handleIdx = PriorityEncoder(handled)

  when(!outValid || (outValid && out.ready) || kill) {
    when(in.valid && !kill) {
      in.ready := true.B
      outBits.viewAsSupertype(chiselTypeOf(in.bits)) := in.bits
      outValid        := true.B
    
      when(anyHandled) {
        outBits.aluOut      := VecInit(units.map(f => f.out))(handleIdx).aluOut
        outBits.branchTaken := VecInit(units.map(f => f.out))(handleIdx).branchTaken
      }

    } .otherwise { // Decode has no instruction. Or killed
      outValid := false.B
    }
  } .elsewhen(kill) {
    outValid := false.B
    log(cf"Instr killed")
  }
}