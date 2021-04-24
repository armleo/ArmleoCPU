package armleocpu

import chisel3._
import chisel3.util._

object ALU {
  val ALU_ADD    = 0.U(5.W)
  val ALU_SUB    = 1.U(5.W)
  val ALU_AND    = 2.U(5.W)
  val ALU_OR     = 3.U(5.W)
  val ALU_XOR    = 4.U(5.W)
  val ALU_SLT    = 5.U(5.W)
  val ALU_SLL    = 6.U(5.W)
  val ALU_SLTU   = 7.U(5.W)
  val ALU_SRL    = 8.U(5.W)
  val ALU_SRA    = 9.U(5.W)
  
  val ALU_COPY_A = 10.U(5.W)
  val ALU_COPY_B = 11.U(5.W)
  val ALU_XXX    = 12.U(5.W)
}



// TODO: Fix REM, REMU, DIV, DIVU to correctly overflow and divide by zero
import ALU._

class ALU_Impl extends Module {
	val io = IO(new Bundle {
        val op = Input(UInt(5.W))
        
        val A = Input(UInt(32.W))
        val B = Input(UInt(32.W))
        val shamt = Input(UInt(5.W))
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