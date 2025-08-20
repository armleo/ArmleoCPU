package armleocpu

import chisel3._
import chisel3.util._
import armleocpu.busConst._

// Both muxes assume that downstream is okay with data changing
// It also assumes AW has to be accepted first


class AtomicMonitor(implicit ccx: CCXParams) extends CCXModule {
  val io = IO(new Bundle {
    val upstream = Flipped(new Bus)
    val downstream = new Bus
  })

  val active = RegInit(false.B) // Indicates active request

  val cntr = Reg(0.U(ccx.coreCount.W))
  val locks = RegInit(0.U(ccx.coreCount.W))
  val locksAddr = Reg(Vec(ccx.coreCount, UInt(ccx.apLen.W)))

  val locksAddrMatches = locksAddr.zip(locks.asBools).map {case (lineIdx, lock) => lock && lineIdx === io.upstream.ax.bits.addr}
  val locksAddrMatch = VecInit(locksAddrMatches).asUInt.orR
  val locksAddrMatchIdx = PriorityEncoder(locksAddrMatches)

  val convertResponseExokay = RegInit(false.B)
  val convertResponse = RegInit(false.B)


  when(!active) {
    when(io.upstream.ax.valid) {
      io.downstream.ax.valid := true.B
    }
    
  }
  /*
  
  when(io.upstream.ax.valid && io.upstream.ax.ready && ((io.upstream.ax.bits.op === OP_ATOMIC_WRITE) || (io.upstream.ax.bits.op === OP_WRITE))) {
    when(locksAddrMatches) {
      locks(locksAddrMatchesIdx) := false.B
      locksLineIdx(locksAddrMatchesIdx) := "hDEADBEEF".U
      convertResponse := io.upstream.ax.bits.op === OP_ATOMIC_WRITE
      convertResponseExokay := io.upstream.ax.bits.op === OP_ATOMIC_WRITE
    } .otherwise {
      convertResponse := false.B
    }

    active := true.B
  }

  when(active && io.downstream.r.valid) {
    io.upstream.r.valid := true.B
    when(convertResponse) {
      io.upstream.r.bits.resp := Mux(convertResponseExokay, EXOKAY, OKAY)
    }

    when(io.upstream.r.ready) {
      io.downstream.r.ready := true.B
    }
  }

  when(io.upstream.r.valid && io.upstream.r.ready) {
    convertResponse := false.B
    active := false.B
  }



  // Creating new locks
  when(io.upstream.ax.valid && io.upstream.ax.ready && (io.upstream.ax.bits.op === OP_ATOMIC_READ)) {
    when(locksAddrMatches) {
      // Already matching some index. Do not create new reservations
    } .otherwise {
      locks(cntr) := true.B
      locksLineIdx(cntr) := getTag(io.upstream.ax.bits.addr)
      convertResponse := false.B // To prevent returning EXOKAY for read request
      cntr := (cntr + 1.U) % ccx.coreCount.U
      active := true.B
    }
  }
  */
  assume(!(io.upstream.ax.valid & io.upstream.r.valid), "AtomicMonitor can only handle one request at a time")
}