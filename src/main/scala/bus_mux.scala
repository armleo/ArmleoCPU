package armleocpu

import chisel3._
import chisel3.util._

class ibus_mux[T <: ibus_t](t: T, n: Int) extends Module {
    val io = IO(new Bundle {
        val upstream = Vec(n, Flipped(t.cloneType))
        val downstream = t.cloneType
    })

    val rarb = Module(new Arbiter(t.ar.bits.cloneType, n))

    for (way <- 0 until n) {
        rarb.io.in(n) <> io.upstream(n).ar
    }

    rarb.io.out <> io.downstream.ar

    val ar_chosen = Reg(rarb.io.chosen.cloneType)

    when(rarb.io.out.valid && rarb.io.out.ready) {
        ar_chosen := rarb.io.chosen
    }

    io.upstream(ar_chosen).r <> io.downstream.r
}

class dbus_mux[T <: dbus_t](t: T, n: Int) extends ibus_mux(t, n) {
    val warb = Module(new Arbiter(t.aw.bits.cloneType, n))

    for (way <- 0 until n) {
        warb.io.in(n) <> io.upstream(n).aw
    }
    warb.io.out <> io.downstream.aw

    val w_chosen = Reg(warb.io.chosen.cloneType)
    when(warb.io.out.valid && warb.io.out.ready) {
        w_chosen := warb.io.chosen
    }

    io.upstream(w_chosen).w <> io.downstream.w
    io.upstream(w_chosen).b <> io.downstream.b
}
