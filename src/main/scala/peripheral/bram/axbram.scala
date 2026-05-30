package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.busConst._

class AXBRAM(
  val bp: BRAMBusParams
)(implicit ccx: CCXParams, memoryFile: MemoryFile) extends CCXModule {
  require(isPow2(bp.busBytes))
  require(bp.addrWidth >= log2Ceil(bp.busBytes))
  require(bp.lenWidth > 0)

  private implicit val bramBp: BRAMBusParams = bp
  private val sizeInWords = 1 << (bp.addrWidth - log2Ceil(bp.busBytes))

  val io = IO(Flipped(new AXBus()(bp)))

  val dataArray = Module(new DataArray(sizeInWords, bp.busBytes, memoryFile))
  val requestKeeper = Module(new RequestKeeper)
  val burstManager = Module(new BurstManager)

  val sIdle :: sRead :: sWrite :: sWriteResp :: Nil = Enum(4)
  val state = RegInit(sIdle)
  val readValid = RegInit(false.B)
  val activeRead = state === sRead
  val activeWrite = state === sWrite
  val start = state === sIdle && io.ax.fire
  val startRead = start && !io.ax.bits.write
  val startWrite = start && io.ax.bits.write
  val readDone = io.r.fire && requestKeeper.io.last
  val writeBeat = activeWrite && io.w.fire
  val writeDone = writeBeat && requestKeeper.io.last
  val bDone = io.b.fire

  io.ax.ready := state === sIdle

  requestKeeper.io.stage.start := start
  requestKeeper.io.request := 0.U.asTypeOf(requestKeeper.io.request)
  requestKeeper.io.request.addr := io.ax.bits.addr
  requestKeeper.io.request.op := io.ax.bits.op
  requestKeeper.io.request.len := io.ax.bits.len
  requestKeeper.io.decrement := io.r.fire || writeBeat

  burstManager.io.stage.start := start
  burstManager.io.requestAddr := io.ax.bits.addr
  burstManager.io.saveIncrementedAddr := (io.r.fire && !requestKeeper.io.last) || (writeBeat && !requestKeeper.io.last)
  burstManager.io.finish := readDone || bDone

  io.r.valid := readValid
  io.r.bits := 0.U.asTypeOf(io.r.bits)
  io.r.bits.data := dataArray.io.resp.rdata
  io.r.bits.resp := OKAY
  io.r.bits.last := requestKeeper.io.last

  io.w.ready := activeWrite

  io.b.valid := state === sWriteResp
  io.b.bits := 0.U.asTypeOf(io.b.bits)
  io.b.bits.resp := OKAY

  val readArray = startRead || (io.r.fire && !requestKeeper.io.last)
  val writeArray = writeBeat
  dataArray.io.req.addr := Mux(startRead, io.ax.bits.addr, Mux(writeArray, burstManager.io.addr, burstManager.io.incrementedAddr))
  dataArray.io.req.read := readArray
  dataArray.io.req.write := writeArray
  dataArray.io.req.wdata := io.w.bits.data
  dataArray.io.req.wmask := io.w.bits.strb

  switch(state) {
    is(sIdle) {
      when(startRead) {
        state := sRead
        readValid := true.B
        log(cf"AXBRAM READ ADDR: 0x${io.ax.bits.addr}%x len=0x${io.ax.bits.len}%x")
      } .elsewhen(startWrite) {
        state := sWrite
        log(cf"AXBRAM WRITE ADDR: 0x${io.ax.bits.addr}%x len=0x${io.ax.bits.len}%x")
      }
    }

    is(sRead) {
      when(readDone) {
        state := sIdle
        readValid := false.B
      }
      when(io.r.fire) {
        log(cf"AXBRAM READ BEAT: data=0x${io.r.bits.data}%x last=${io.r.bits.last}")
      }
    }

    is(sWrite) {
      when(writeDone) {
        state := sWriteResp
      }
      when(io.w.fire) {
        log(cf"AXBRAM WRITE BEAT: data=0x${io.w.bits.data}%x strb=0x${io.w.bits.strb}%x last=${io.w.bits.last}")
      }
    }

    is(sWriteResp) {
      when(bDone) {
        state := sIdle
      }
    }
  }
}
