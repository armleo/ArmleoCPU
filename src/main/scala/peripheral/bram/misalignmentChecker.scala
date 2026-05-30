package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._
import armleocpu._

class ReadMisalignmentChecker(implicit bp: BusParams) extends Module {
  val io = IO(new ReadMisalignmentCheckerIO)

  val misaligned =
    if (bp.busBytes == 1) false.B
    else io.in.bits.addr(log2Ceil(bp.busBytes) - 1, 0) =/= 0.U

  io.misaligned := io.in.valid && misaligned
  io.out.valid := io.in.valid && !misaligned
  io.out.bits := io.in.bits
  io.in.ready := !misaligned && io.out.ready
}

class WriteMisalignmentChecker(implicit bp: BusParams) extends Module {
  val io = IO(new WriteMisalignmentCheckerIO)

  val misaligned =
    if (bp.busBytes == 1) false.B
    else io.in.bits.addr(log2Ceil(bp.busBytes) - 1, 0) =/= 0.U

  io.misaligned := io.in.valid && misaligned
  io.out.valid := io.in.valid && !misaligned
  io.out.bits := io.in.bits
  io.in.ready := !misaligned && io.out.ready
}

class MisalignmentChecker(implicit bp: BusParams) extends Module {
  val io = IO(new MisalignmentCheckerIO)

  val read = Module(new ReadMisalignmentChecker)
  val write = Module(new WriteMisalignmentChecker)

  read.io.in <> io.arIn
  io.arOut <> read.io.out
  write.io.in <> io.awIn
  io.awOut <> write.io.out

  io.readMisaligned := read.io.misaligned
  io.writeMisaligned := write.io.misaligned
}
