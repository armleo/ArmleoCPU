package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._

// Both muxes assume that downstream is okay with data changing
// It also assumes AW has to be accepted first


class busMux[T <: dbus_t](t: T, n: Int, noise: Boolean = true)(implicit ccx: CCXParams) extends CCXModule {
  val io = IO(new Bundle {
    val upstream = Vec(n, Flipped(t.cloneType))
    val downstream = t.cloneType
  })

  val arb = Module(new Arbiter(t.ax.bits.cloneType, n))

  val chosen = Reg(arb.io.chosen.cloneType)
  val active = RegInit(false.B)


  /**************************************************************************/
  /* AR mux                                                                 */
  /**************************************************************************/
  for (i <- 0 until n) {
    arb.io.in(i) <> io.upstream(i).ax
  }

  arb.io.out <> io.downstream.ax

  
  /**************************************************************************/
  /* Locking mechanism                                                      */
  /**************************************************************************/
  when(io.downstream.r.valid && io.downstream.r.ready) {
    active := false.B
  }

  when(arb.io.out.valid && arb.io.out.ready) {
    chosen := arb.io.chosen
    active := true.B
  }

  /**************************************************************************/
  /* R mux                                                                  */
  /**************************************************************************/
  for (i <- 0 until n) {
    io.upstream(i).r.valid := false.B
    io.upstream(i).r.bits := (if (noise) FibonacciLFSR.maxPeriod(io.upstream(i).r.bits.asUInt.getWidth, reduction = XNOR) else 0.U).asTypeOf(io.upstream(i).r.bits)
  }
  io.downstream.r.ready := false.B

  when(active) {
    io.upstream(chosen).r <> io.downstream.r
  }
}


import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


import chisel3.stage._
object dbusMux_generator extends App {
  implicit val ccx:CCXParams = new CCXParams
  // Temorary disable memory configs as yosys does not know what to do with them
  // (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  ChiselStage.emitSystemVerilogFile(
  new busMux(new dbus_t, 4, noise = false),
    Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
    Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}