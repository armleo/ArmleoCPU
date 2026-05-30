package armleocpu.peripheral

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.peripheral.bram._

class BRAM(
  val bp: BusParams,
  val idDepth: Int = 4
)(implicit ccx: CCXParams, memoryFile: MemoryFile) extends CCXModule {
  require(isPow2(bp.busBytes))
  require(bp.addrWidth >= log2Ceil(bp.busBytes))
  require(bp.lenWidth > 0)

  private implicit val outerBp: BusParams = bp
  private val innerBp: BRAMBusParams =
    new BRAMBusParams(addrWidth = bp.addrWidth, busBytes = bp.busBytes, lenWidth = bp.lenWidth)

  val io = IO(Flipped(new ReadWriteBus()(bp)))

  val misalignmentChecker = Module(new MisalignmentChecker)
  val misalignedHandler = Module(new MisalignedAccessHandler)
  val idYanker = Module(new IdYanker(idDepth)(outerBp, innerBp))
  val axbram = Module(new AXBRAM(innerBp)(ccx, memoryFile))

  val alignedWriteActive = RegInit(false.B)
  val misalignedWriteActive = RegInit(false.B)

  misalignmentChecker.io.awIn.valid := io.aw.valid && !misalignedWriteActive && !alignedWriteActive
  misalignmentChecker.io.awIn.bits := io.aw.bits
  misalignmentChecker.io.arIn.valid := io.ar.valid && !io.aw.valid && !misalignedWriteActive && !alignedWriteActive
  misalignmentChecker.io.arIn.bits := io.ar.bits

  misalignedHandler.io.aw.valid := io.aw.valid && misalignmentChecker.io.writeMisaligned && !misalignedWriteActive && !alignedWriteActive
  misalignedHandler.io.aw.bits := io.aw.bits
  misalignedHandler.io.ar.valid := io.ar.valid && !io.aw.valid && misalignmentChecker.io.readMisaligned && !misalignedWriteActive && !alignedWriteActive
  misalignedHandler.io.ar.bits := io.ar.bits
  misalignedHandler.io.w.valid := io.w.valid && misalignedWriteActive
  misalignedHandler.io.w.bits := io.w.bits
  misalignedHandler.io.b.ready := io.b.ready
  misalignedHandler.io.r.ready := io.r.ready

  idYanker.io.in.aw <> misalignmentChecker.io.awOut
  idYanker.io.in.ar <> misalignmentChecker.io.arOut
  idYanker.io.in.w.valid := io.w.valid && alignedWriteActive
  idYanker.io.in.w.bits := io.w.bits
  idYanker.io.in.b.ready := io.b.ready
  idYanker.io.in.r.ready := io.r.ready

  io.aw.ready := misalignmentChecker.io.awIn.ready || misalignedHandler.io.aw.ready
  io.ar.ready := misalignmentChecker.io.arIn.ready || misalignedHandler.io.ar.ready
  io.w.ready := Mux(misalignedWriteActive, misalignedHandler.io.w.ready, idYanker.io.in.w.ready)

  io.b.valid := misalignedHandler.io.b.valid || idYanker.io.in.b.valid
  io.b.bits := Mux(misalignedHandler.io.b.valid, misalignedHandler.io.b.bits, idYanker.io.in.b.bits)

  io.r.valid := misalignedHandler.io.r.valid || idYanker.io.in.r.valid
  io.r.bits := Mux(misalignedHandler.io.r.valid, misalignedHandler.io.r.bits, idYanker.io.in.r.bits)

  val axWrite = idYanker.io.out.aw.valid
  val axRead = idYanker.io.out.ar.valid && !axWrite

  axbram.io.ax.valid := axWrite || axRead
  axbram.io.ax.bits := 0.U.asTypeOf(axbram.io.ax.bits)
  axbram.io.ax.bits.write := axWrite
  axbram.io.ax.bits.op := Mux(axWrite, idYanker.io.out.aw.bits.op, idYanker.io.out.ar.bits.op)
  axbram.io.ax.bits.addr := Mux(axWrite, idYanker.io.out.aw.bits.addr, idYanker.io.out.ar.bits.addr)
  axbram.io.ax.bits.len := Mux(axWrite, idYanker.io.out.aw.bits.len, idYanker.io.out.ar.bits.len)

  idYanker.io.out.aw.ready := axWrite && axbram.io.ax.ready
  idYanker.io.out.ar.ready := axRead && axbram.io.ax.ready
  axbram.io.w <> idYanker.io.out.w
  idYanker.io.out.r <> axbram.io.r
  idYanker.io.out.b <> axbram.io.b

  when(io.aw.fire) {
    alignedWriteActive := !misalignmentChecker.io.writeMisaligned
    misalignedWriteActive := misalignmentChecker.io.writeMisaligned
    log(cf"BRAM AW: addr=0x${io.aw.bits.addr}%x len=0x${io.aw.bits.len}%x misaligned=${misalignmentChecker.io.writeMisaligned}")
  }

  when(io.w.fire && io.w.bits.last) {
    alignedWriteActive := false.B
    misalignedWriteActive := false.B
  }

  when(io.ar.fire) {
    log(cf"BRAM AR: addr=0x${io.ar.bits.addr}%x len=0x${io.ar.bits.len}%x misaligned=${misalignmentChecker.io.readMisaligned}")
  }

  when(io.r.fire) {
    log(cf"BRAM R: resp=0x${io.r.bits.resp}%x last=${io.r.bits.last}")
  }

  when(io.b.fire) {
    log(cf"BRAM B: resp=0x${io.b.bits.resp}%x")
  }
}

import _root_.circt.stage.ChiselStage
import chisel3.stage._

object BRAMGenerator extends App {
  implicit val ccx: CCXParams = new CCXParams
  implicit val memoryFile: MemoryFile = new HexMemoryFile("")

  ChiselStage.emitSystemVerilogFile(
    new BRAM(
      bp = new BusParams(addrWidth = 2, busBytes = 2, idWidth = 2, lenWidth = 8)
    ),
    Array("--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
    Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
}
