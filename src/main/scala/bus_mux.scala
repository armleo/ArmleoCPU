package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._

// Both muxes assume that downstream is okay with data changing
// It also assumes AW has to be accepted first


class dbus_mux[T <: dbus_t](t: T, n: Int, noise: Boolean = true)(implicit ccx: CCXParams) extends CCXModule {
  val io = IO(new Bundle {
    val upstream = Vec(n, Flipped(t.cloneType))
    val downstream = t.cloneType
  })

  val rarb = Module(new Arbiter(t.ax.bits.cloneType, n))

  val r_chosen = Reg(rarb.io.chosen.cloneType)
  val r_active = RegInit(false.B)


  /**************************************************************************/
  /* AR mux                                                                 */
  /**************************************************************************/
  for (i <- 0 until n) {
    rarb.io.in(i) <> io.upstream(i).ax
  }

  rarb.io.out <> io.downstream.ax

  

  /**************************************************************************/
  /* Locking mechanism                                                      */
  /**************************************************************************/
  when(io.downstream.r.valid && io.downstream.r.ready) {
    r_active := false.B
  }

  when(rarb.io.out.valid && rarb.io.out.ready) {
    r_chosen := rarb.io.chosen
    r_active := true.B
  }

  /**************************************************************************/
  /* R mux                                                                  */
  /**************************************************************************/
  for (i <- 0 until n) {
    io.upstream(i).r.valid := false.B
    io.upstream(i).r.bits := (if (noise) FibonacciLFSR.maxPeriod(io.upstream(i).r.bits.asUInt.getWidth, reduction = XNOR) else 0.U).asTypeOf(io.upstream(i).r.bits)
  }
  io.downstream.r.ready := false.B
  when(r_active) {
    io.upstream(r_chosen).r <> io.downstream.r
  }
}


import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


import chisel3.stage._
object dbus_mux_generator extends App {
  implicit val ccx:CCXParams = new CCXParams
  // Temorary disable memory configs as yosys does not know what to do with them
  // (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  ChiselStage.emitSystemVerilogFile(
  new dbus_mux(new dbus_t, 4, noise = false),
    Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
    Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}