package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._

class ibus_mux[T <: ibus_t](t: T, n: Int, noise: Boolean = true) extends Module {
    val io = IO(new Bundle {
        val upstream = Vec(n, Flipped(t.cloneType))
        val downstream = t.cloneType
    })

    val rarb = Module(new Arbiter(t.ar.bits.cloneType, n))

    for (i <- 0 until n) {
        rarb.io.in(i) <> io.upstream(i).ar
    }

    rarb.io.out <> io.downstream.ar

    val r_chosen = Reg(rarb.io.chosen.cloneType)
    val r_active = RegInit(false.B)

    when(io.downstream.r.valid && io.downstream.r.ready && io.downstream.r.bits.last) {
        r_active := false.B
    }

    when(rarb.io.out.valid && rarb.io.out.ready) {
        r_chosen := rarb.io.chosen
        r_active := true.B
    }

    for (i <- 0 until n) {
        io.upstream(i).r.valid := false.B
        io.upstream(i).r.bits := (if (noise) FibonacciLFSR.maxPeriod(io.upstream(i).r.bits.asUInt.getWidth, reduction = XNOR) else 0.U).asTypeOf(io.upstream(i).r.bits)
    }
    io.downstream.r.ready := false.B
    when(r_active) {
        io.upstream(r_chosen).r <> io.downstream.r
    }
}

class dbus_mux[T <: dbus_t](t: T, n: Int, noise: Boolean = true) extends ibus_mux(t, n, noise) {
    val warb = Module(new Arbiter(t.aw.bits.cloneType, n))

    for (i <- 0 until n) {
        warb.io.in(i) <> io.upstream(i).aw
    }
    warb.io.out <> io.downstream.aw

    val w_active = RegInit(false.B)
    val b_active = RegInit(false.B)
    val wb_chosen = Reg(warb.io.chosen.cloneType)

    when(io.downstream.w.valid && io.downstream.w.ready && io.downstream.w.bits.last) {
        w_active := false.B
    }


    when(io.downstream.b.valid && io.downstream.b.ready) {
        b_active := false.B
    }

    when(warb.io.out.valid && warb.io.out.ready) {
        wb_chosen := warb.io.chosen
        w_active := true.B
        b_active := true.B
    }

    for (i <- 0 until n) {
        io.upstream(i).w.ready := false.B
    }
    io.downstream.w.valid := false.B
    io.downstream.w.bits := (if (noise) FibonacciLFSR.maxPeriod(io.downstream.w.bits.asUInt.getWidth, reduction = XNOR) else 0.U).asTypeOf(io.downstream.w.bits)
    
    when(w_active) {
        io.upstream(wb_chosen).w <> io.downstream.w
    }

    for (i <- 0 until n) {
        io.upstream(i).b.valid := false.B
        io.upstream(i).b.bits := (if (noise) FibonacciLFSR.maxPeriod(io.upstream(i).b.bits.asUInt.getWidth, reduction = XNOR) else 0.U).asTypeOf(io.upstream(i).b.bits)
    }
    io.downstream.b.ready := false.B
    
    when(b_active) {
        io.upstream(wb_chosen).b <> io.downstream.b
    }
}



import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


import chisel3.stage._
object dbus_mux_generator extends App {
  // Temorary disable memory configs as yosys does not know what to do with them
  // (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  ChiselStage.emitSystemVerilogFile(
    new dbus_mux(new dbus_t(new CoreParams), 4, noise = false),
      Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
      Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}