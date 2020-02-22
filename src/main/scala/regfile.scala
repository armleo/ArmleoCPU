package armleocpu


import chisel3._
import chisel3.util._

class RegfileReadIf extends Bundle {
    val address = Input(UInt(5.W))
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
    
    io.rs1.data := storage.read(io.rs1.address)

    io.rs2.data := storage.read(io.rs2.address)

    when(io.rd.write) {
        when(io.rd.address =/= 0.U) {
            if(log) {
                printf("[armleocpu/regfile] write to %d with value 0x%x\n", io.rd.address, io.rd.data)
            }
            storage.write(io.rd.address, io.rd.data)
        }
    }
    
}