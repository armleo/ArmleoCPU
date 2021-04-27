/*package armleocpu

import chisel3._
import chisel3.util._


class exampleBundle extends Bundle {
    val a = UInt(10.W)
    val b = UInt(20.W)
}

object exampleBundle {
    def fromUInt(input_uint: UInt): exampleBundle = {
        val bndl = Wire(new exampleBundle)
        bndl.a := input_uint(9, 0)
        bndl.b := input_uint(29, 10)
        bndl
    }
}

class Experiment extends Module {
    val io = IO(new Bundle {
        val exampleBundle_out = Output(new exampleBundle)
        val a_in = Input(UInt(10.W))
        val b_in = Input(UInt(20.W))
        val c_in = Input(UInt(30.W))
    })
    
    io.exampleBundle_out := exampleBundle.fromUInt(io.c_in)
}*/