package armleocpu

import chisel3._
import chisel3.util._

object MULDIV {
  val MULDIV_MUL    = 0.U(5.W)
  val MULDIV_MULH   = 1.U(5.W)
  val MULDIV_MULHSU = 2.U(5.W)
  val MULDIV_MULHU  = 3.U(5.W)
  val MULDIV_DIV    = 4.U(5.W)
  val MULDIV_DIVU   = 5.U(5.W)
  val MULDIV_REM    = 6.U(5.W)
  val MULDIV_REMU   = 7.U(5.W)
}



// TODO: Fix REM, REMU, DIV, DIVU to correctly overflow and divide by zero
import MULDIV._

class ALU_Impl extends Module {
	val io = IO(new Bundle {
        val op = Input(UInt(5.W))
        
        val A = Input(UInt(32.W))
        val B = Input(UInt(32.W))
        
        val out = Output(UInt(32.W))
    })
    io.out := MuxLookup(io.op, io.B, Seq(
			ALU_ADD  -> (io.A + io.B),
			ALU_SUB  -> (io.A - io.B),
			ALU_SRA  -> (io.A.asSInt >> io.shamt).asUInt,
			ALU_SRL  -> (io.A >> io.shamt),
			ALU_SLL  -> (io.A << io.shamt),
			ALU_SLT  -> (io.A.asSInt < io.B.asSInt),
			ALU_SLTU -> (io.A < io.B),
			ALU_AND  -> (io.A & io.B),
			ALU_OR   -> (io.A | io.B),
			ALU_XOR  -> (io.A ^ io.B),
			ALU_COPY_A -> io.A,
			ALU_COPY_B -> io.B
		)
	  )
}
  /*val MULDIV_MUL    = 16.U(5.W)
  val MULDIV_MULH   = 17.U(5.W)
  val MULDIV_MULHSU = 18.U(5.W)
  val MULDIV_MULHU  = 19.U(5.W)
  val MULDIV_DIV    = 20.U(5.W)
  val MULDIV_DIVU   = 21.U(5.W)
  val MULDIV_REM    = 22.U(5.W)
  val MULDIV_REMU   = 23.U(5.W)*/