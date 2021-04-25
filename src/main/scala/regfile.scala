package armleocpu


import chisel3._
import chisel3.util._

class RegfileReadIf extends Bundle {
  val address = Input(UInt(5.W))
  val read = Input(Bool())
  val data = Output(UInt(32.W))
}

class RegfileWriteIf extends Bundle {
  val address = Input(UInt(5.W))
  val write = Input(Bool())
  val data = Input(UInt(32.W))
}


class Regfile(log: Boolean) extends Module {
  val io = IO(new Bundle{
    val rs1 = new RegfileReadIf();
    val rs2 = new RegfileReadIf();
    val rd = new RegfileWriteIf();
  })
  val storage = Mem(32, UInt(32.W));
  

  val rs1_data_reg = RegEnable(storage.read(io.rs1.address), io.rs1.read)
  val rs2_data_reg = RegEnable(storage.read(io.rs2.address), io.rs2.read)
  
  io.rs1.data := rs1_data_reg
  io.rs2.data := rs2_data_reg

  when(reset.asBool() || io.rd.write) {
    when(reset.asBool()) {
      storage.write(0.U, 0.U)
    }.elsewhen(io.rd.write && io.rd.address =/= 0.U) {
      storage.write(io.rd.address, io.rd.data)
    }
  }
}