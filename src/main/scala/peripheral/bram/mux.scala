package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._

class RequestMuxControl extends Module {
  val io = IO(new RequestMuxControlIO)

  io.selectWriter := io.writerActive || io.writerStarting
  io.selectReader := !io.selectWriter && (io.readerActive || io.readerStarting)
  io.selectMisaligned := !io.selectWriter && !io.selectReader && (io.misalignedActive || io.misalignedStarting)
}

class DataArrayReqMux(addrWidth: Int, busBytes: Int) extends Module {
  val io = IO(new DataArrayReqMuxIO(addrWidth, busBytes))

  io.out := Mux(io.selectWriter, io.writer, io.reader)
}
