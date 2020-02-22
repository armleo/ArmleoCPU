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
  val ALU_MUL    = 16.U(5.W)
  val ALU_MULH   = 17.U(5.W)
  //val ALU_MULHSU = 18.U(5.W)
  val ALU_MULHU  = 19.U(5.W)
  val ALU_DIV    = 20.U(5.W)
  val ALU_DIVU   = 21.U(5.W)
  val ALU_REM    = 22.U(5.W)
  val ALU_REMU   = 23.U(5.W)
  val ALU_COPY_A = 29.U(5.W)
  val ALU_COPY_B = 30.U(5.W)
  val ALU_XXX    = 31.U(5.W)
}

// TODO: Fix REM, REMU, DIV, DIVU to correctly overflow and divide by zero
import ALU._

class Alu_imp extends Module {
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
			ALU_MUL  -> ((io.A.asSInt * io.B.asSInt)(31, 0)).asUInt,
			ALU_MULH -> ((io.A.asSInt * io.B.asSInt)(63, 32)).asUInt,
			ALU_MULHU-> ((io.A * io.B)(63, 32)).asUInt,
			// ALU_MULHSU -> // can't generate this one, because no synthezible code for this yet
			ALU_DIV  -> (io.A.asSInt / io.B.asSInt).asUInt,
			ALU_DIVU -> (io.A / io.B).asUInt,
			ALU_REM  -> (io.A.asSInt % io.B.asSInt).asUInt,
			ALU_REMU -> (io.A % io.B).asUInt,
			ALU_COPY_A -> io.A,
			ALU_COPY_B -> io.B
		)
	  )
}