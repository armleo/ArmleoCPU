package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._

class DataArray(
  val sizeInWords: Int,
  val busBytes: Int,
  val memoryFile: MemoryFile
) extends Module {
  require(isPow2(sizeInWords))

  private val addrWidth = log2Ceil(sizeInWords * busBytes)

  val io = IO(new DataArrayIO(addrWidth, busBytes))

  private val memory =
    if (memoryFile.path != "") SRAM.masked(sizeInWords, Vec(busBytes, UInt(8.W)), 0, 0, 1, memoryFile)
    else                       SRAM.masked(sizeInWords, Vec(busBytes, UInt(8.W)), 0, 0, 1)

  private val port = memory.readwritePorts(0)

  port.address := io.req.addr / busBytes.U
  port.mask.get := io.req.wmask.asBools
  port.enable := io.req.read || io.req.write
  port.isWrite := io.req.write
  port.writeData := io.req.wdata.asTypeOf(port.writeData)

  io.resp.rdata := port.readData.asUInt
}
