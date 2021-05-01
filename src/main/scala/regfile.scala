package armleocpu


import chisel3._
import chisel3.util._


import Consts._ // For xLen

class RegfileReadIf extends Bundle {
  val address = Input(UInt(5.W))
  val read = Input(Bool())
  val data = Output(UInt(xLen.W))
}

class RegfileWriteIf extends Bundle {
  val address = Input(UInt(5.W))
  val write = Input(Bool())
  val data = Input(UInt(xLen.W))
}


class Regfile extends Module {
  val io = IO(new Bundle{
    val rs1 = new RegfileReadIf();
    val rs2 = new RegfileReadIf();
    val rd = new RegfileWriteIf();
  })
  val storage0 = SyncReadMem(32, UInt(xLen.W), SyncReadMem.ReadFirst);
  val storage1 = SyncReadMem(32, UInt(xLen.W), SyncReadMem.ReadFirst);
  // Why this monstrosity?
  // Well, we need to read value from registers, and the output should
  // stay the same until next read request. So this monstrocity was created
  // It reads value when requested captures it and fixed it until
  // next read request

  val rs1_read_reg = RegNext(io.rs1.read)
  val rs2_read_reg = RegNext(io.rs2.read)

  val rs1_saved_data = RegNext(io.rs1.data)
  val rs2_saved_data = RegNext(io.rs2.data)

  val rs1_read_data = storage0.read(io.rs1.address, io.rs1.read)
  val rs2_read_data = storage1.read(io.rs2.address, io.rs2.read)

  io.rs1.data := Mux(rs1_read_reg, rs1_read_data, rs1_saved_data)
  io.rs2.data := Mux(rs2_read_reg, rs2_read_data, rs2_saved_data)
  
  
  // This scheme will clear register zero on reset and
    // not allow any write to it in future
  when(reset.asBool() || (io.rd.write && io.rd.address =/= 0.U)) {
    val addr = Mux(reset.asBool(), 0.U, io.rd.address)
    val wdata = Mux(reset.asBool(), 0.U, io.rd.data)
    storage0(
       addr// If reset set address to 0, else to write address
    ) := wdata
    storage1(
       addr// If reset set address to 0, else to write address
    ) := wdata
  }
}