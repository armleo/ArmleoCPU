package armleocpu.peripheral

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.busConst._
import armleocpu.peripheral.bram._

class BRAM(
  val bp: BusParams
)(implicit ccx: CCXParams, memoryFile: MemoryFile) extends CCXModule {
  require(isPow2(bp.busBytes))
  require(bp.addrWidth >= log2Ceil(bp.busBytes))

  private val busBytesLog2 = log2Ceil(bp.busBytes)
  private val sizeInWords = 1 << (bp.addrWidth - busBytesLog2)

  private implicit val bramBp: BusParams = bp

  val io = IO(Flipped(new ReadWriteBus()(bp)))

  private def misaligned(addr: UInt): Bool = {
    if (bp.busBytes == 1) false.B else addr(busBytesLog2 - 1, 0) =/= 0.U
  }

  val dataArray = Module(new DataArray(sizeInWords, bp.busBytes, memoryFile))

  val reader = Module(new Reader(bp.addrWidth, bp.busBytes))
  val writer = Module(new Writer(bp.addrWidth, bp.busBytes))
  val misalignedHandler = Module(new MisalignedAccessHandler)

  val requestKeeper = Module(new RequestKeeper)
  val burstManager = Module(new BurstManager)

  val idle = !reader.io.stage.active && !writer.io.stage.active && !misalignedHandler.io.stage.active
  val awMisaligned = misaligned(io.aw.bits.addr)
  val arMisaligned = misaligned(io.ar.bits.addr)
  val startMisalignedWrite = idle && io.aw.valid && awMisaligned
  val startWrite = idle && io.aw.valid && !awMisaligned
  val startMisalignedRead = idle && !io.aw.valid && io.ar.valid && arMisaligned
  val startRead = idle && !io.aw.valid && io.ar.valid && !arMisaligned
  val selectWriter = writer.io.stage.active || startWrite

  requestKeeper.io.stage.start := reader.io.requestKeeper.stage.start || writer.io.requestKeeper.stage.start
  requestKeeper.io.request := Mux(
    selectWriter,
    writer.io.requestKeeper.request,
    reader.io.requestKeeper.request
  )
  requestKeeper.io.decrement := Mux(
    selectWriter,
    writer.io.requestKeeper.decrement,
    reader.io.requestKeeper.decrement
  )

  reader.io.requestKeeper.stage.active := requestKeeper.io.stage.active
  reader.io.requestKeeper.stage.done := requestKeeper.io.stage.done
  reader.io.requestKeeper.id := requestKeeper.io.id
  reader.io.requestKeeper.burstRemaining := requestKeeper.io.burstRemaining
  reader.io.requestKeeper.last := requestKeeper.io.last

  writer.io.requestKeeper.stage.active := requestKeeper.io.stage.active
  writer.io.requestKeeper.stage.done := requestKeeper.io.stage.done
  writer.io.requestKeeper.id := requestKeeper.io.id
  writer.io.requestKeeper.burstRemaining := requestKeeper.io.burstRemaining
  writer.io.requestKeeper.last := requestKeeper.io.last

  burstManager.io.stage.start := reader.io.burstManager.stage.start || writer.io.burstManager.stage.start
  burstManager.io.requestAddr := Mux(
    selectWriter,
    writer.io.burstManager.requestAddr,
    reader.io.burstManager.requestAddr
  )
  burstManager.io.saveIncrementedAddr := Mux(
    selectWriter,
    writer.io.burstManager.saveIncrementedAddr,
    reader.io.burstManager.saveIncrementedAddr
  )
  burstManager.io.finish := Mux(
    selectWriter,
    writer.io.burstManager.finish,
    reader.io.burstManager.finish
  )

  reader.io.burstManager.stage.active := burstManager.io.stage.active
  reader.io.burstManager.stage.done := burstManager.io.stage.done
  reader.io.burstManager.incrementedAddr := burstManager.io.incrementedAddr
  reader.io.burstManager.addr := burstManager.io.addr

  writer.io.burstManager.stage.active := burstManager.io.stage.active
  writer.io.burstManager.stage.done := burstManager.io.stage.done
  writer.io.burstManager.incrementedAddr := burstManager.io.incrementedAddr
  writer.io.burstManager.addr := burstManager.io.addr

  reader.io.stage.start := startRead
  writer.io.stage.start := startWrite
  misalignedHandler.io.stage.start := startMisalignedWrite || startMisalignedRead

  reader.io.resp := OKAY
  writer.io.resp := OKAY

  reader.io.dataArrayResp := dataArray.io.resp

  writer.io.aw.valid := startWrite
  writer.io.aw.bits := io.aw.bits
  writer.io.w.valid := io.w.valid
  writer.io.w.bits := io.w.bits
  writer.io.b.ready := io.b.ready

  reader.io.ar.valid := startRead
  reader.io.ar.bits := io.ar.bits
  reader.io.r.ready := io.r.ready

  misalignedHandler.io.aw.valid := startMisalignedWrite
  misalignedHandler.io.aw.bits := io.aw.bits
  misalignedHandler.io.ar.valid := startMisalignedRead
  misalignedHandler.io.ar.bits := io.ar.bits
  misalignedHandler.io.w.valid := io.w.valid
  misalignedHandler.io.w.bits := io.w.bits
  misalignedHandler.io.b.ready := io.b.ready
  misalignedHandler.io.r.ready := io.r.ready

  io.aw.ready := Mux(startMisalignedWrite, misalignedHandler.io.aw.ready, writer.io.aw.ready)
  io.ar.ready := Mux(startMisalignedRead, misalignedHandler.io.ar.ready, reader.io.ar.ready)
  io.w.ready := Mux(misalignedHandler.io.stage.active, misalignedHandler.io.w.ready, writer.io.w.ready)

  io.b.valid := writer.io.b.valid || misalignedHandler.io.b.valid
  io.b.bits := Mux(misalignedHandler.io.b.valid, misalignedHandler.io.b.bits, writer.io.b.bits)

  io.r.valid := reader.io.r.valid || misalignedHandler.io.r.valid
  io.r.bits := Mux(misalignedHandler.io.r.valid, misalignedHandler.io.r.bits, reader.io.r.bits)

  val writeArrayReq = writer.io.dataArrayReq.write
  dataArray.io.req := Mux(writeArrayReq, writer.io.dataArrayReq, reader.io.dataArrayReq)

  when(reader.io.ar.fire) {
    log(cf"READ ADDR: 0x${io.ar.bits.addr}%x, len: 0x${io.ar.bits.len}%x")
  }

  when(reader.io.r.fire) {
    log(cf"READ BEAT: data=0x${io.r.bits.data}%x, resp=0x${io.r.bits.resp}%x, last=${io.r.bits.last}")
  }

  when(writer.io.aw.fire) {
    log(cf"WRITE ADDR: 0x${io.aw.bits.addr}%x, len: 0x${io.aw.bits.len}%x")
  }

  when(writer.io.w.fire) {
    log(cf"WRITE BEAT: data=0x${io.w.bits.data}%x, strb=0x${io.w.bits.strb}%x, last=${io.w.bits.last}")
  }

  when(writer.io.b.fire) {
    log(cf"WRITE RESP: resp=0x${io.b.bits.resp}%x")
  }

  when(misalignedHandler.io.ar.fire) {
    log(cf"MISALIGNED READ: addr=0x${io.ar.bits.addr}%x, len=0x${io.ar.bits.len}%x")
  }

  when(misalignedHandler.io.aw.fire) {
    log(cf"MISALIGNED WRITE: addr=0x${io.aw.bits.addr}%x, len=0x${io.aw.bits.len}%x")
  }
}

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation
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
