package armleocpu

import chisel3._
import chisel3.util._

class ibus_mux[T <: ibus_t](t: T, n: Int) extends Module {
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
    val r_active = Reg(Bool())

    when(io.downstream.r.valid && io.downstream.r.ready && io.downstream.r.bits.last) {
        r_active := false.B
    }

    when(rarb.io.out.valid && rarb.io.out.ready) {
        r_chosen := rarb.io.chosen
        r_active := true.B
    }

    when(r_active) {
        io.upstream(r_chosen).r <> io.downstream.r
    } .otherwise {
        for (i <- 0 until n) {
            io.upstream(i).r.valid := false.B
            io.upstream(i).r.bits := DontCare
        }
        io.downstream.r.ready := false.B
    }

}

class dbus_mux[T <: dbus_t](t: T, n: Int) extends ibus_mux(t, n) {
    val warb = Module(new Arbiter(t.aw.bits.cloneType, n))

    for (i <- 0 until n) {
        warb.io.in(i) <> io.upstream(i).aw
    }
    warb.io.out <> io.downstream.aw

    val w_active = Reg(Bool())
    val b_active = Reg(Bool())
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

    when(w_active) {
        io.upstream(wb_chosen).w <> io.downstream.w
    } .otherwise {
        for (i <- 0 until n) {
            io.upstream(i).w.ready := false.B
            io.downstream.w.valid := false.B
            io.downstream.w.bits := DontCare
        }
    }

    when(b_active) {
        io.upstream(wb_chosen).b <> io.downstream.b
    } .otherwise {
        for (i <- 0 until n) {
            io.upstream(i).b.valid := false.B
            io.upstream(i).b.bits := DontCare
            io.downstream.b.ready := false.B
        }
    }
}
