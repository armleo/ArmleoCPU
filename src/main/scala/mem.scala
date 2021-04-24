package armleo_common

import chisel3._
import chisel3.util._
import chisel3.experimental._

// WARNING: CycloneSyncMem is not capable of holding read value after write, so if write is request than readdata is modified to.

class Mem_1w1r(mem_emulate: Boolean, address_width: Int, data_width: Int) extends Module {
  val io = IO(new Bundle{
    val readaddress = Input(UInt(address_width.W))
    val read = Input(Bool())
    val readdata = Output(UInt(data_width.W))

    val writeaddress = Input(UInt(address_width.W))
    val write = Input(Bool())
    val writedata = Input(UInt(data_width.W))
  })
  if(mem_emulate) {
    val backmem = Mem(1 << address_width, UInt(data_width.W))
    val readdata = RegInit(0.U(data_width.W))
    io.readdata := readdata
    when(io.read) {
      readdata := backmem(io.readaddress)
    }
    when(io.write) {
      backmem.write(io.writeaddress, io.writedata)
    }
  } else {
    val mem = new Mem_1w1r_bb(address_width, data_width)
    io <> mem.io
  }
}

class Mem_1w1r_bb (address_width: Int, data_width: Int) extends BlackBox(
    Map(
      "ELEMENTS_W" -> new IntParam(address_width),
      "WIDTH" -> new IntParam(data_width))
  ) with HasBlackBoxResource {
  val io = IO(new Bundle{
    val readaddress = Input(UInt(address_width.W))
    val read = Input(Bool())
    val readdata = Output(UInt(data_width.W))

    val writeaddress = Input(UInt(address_width.W))
    val write = Input(Bool())
    val writedata = Input(UInt(data_width.W))
  })
  addResource("/mem_1w1r.v")
}
