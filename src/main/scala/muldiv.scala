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

import Consts._ // For xLen == 64

// TODO: implement and test
// TODO: Shorter implementations for DW_32

class Divider extends Module {
  val io = IO(new Bundle {
    val s0 = new Bundle {
      val valid = Input(Bool())

      val dividend = Input(UInt(xLen.W))
      val divisor = Input(UInt(xLen.W))
    }
    val s1 = new Bundle {
      val ready = Output(Bool())
      val quotient = Output(UInt(xLen.W))
      val remainder = Output(UInt(xLen.W))
      val division_by_zero = Output(UInt(xLen.W))
    }
  })

  val busy = RegInit(false.B)
  val ready = RegInit(false.B)
  io.s1.ready := ready

  val remainder = Reg(UInt(xLen.W))
  io.s1.remainder := remainder

  val quotient = Reg(UInt(xLen.W))
  io.s1.quotient := quotient
  
  val divisor_r = Reg(UInt(xLen.W)) // Contains registered version
  val dividend_r = Reg(UInt(xLen.W)) // Contains dividend

  val difference = remainder - divisor_r
  val positive = remainder >= divisor_r

  val division_by_zero = RegInit(false.B)
  io.s1.division_by_zero := division_by_zero

  val counter = Reg(UInt((log2Ceil(xLen) + 1).W))

  when(!busy) {
    // Not active
    ready := false.B
    counter := 0.U
    
    divisor_r := io.s0.divisor
    dividend_r := io.s0.dividend

    when(io.s0.valid) {
      busy := true.B
      division_by_zero := io.s0.divisor === 0.U
      remainder := 0.U
      quotient := 0.U // Not required because will be shifted out anyway. May be remove it?
    }
  } .otherwise {
    dividend_r := (dividend_r << 1)
    quotient := (quotient << 1) | positive

    remainder := (Mux(positive, difference, remainder) << 1) | dividend_r(63)

    when(counter === xLen.U) {
      remainder := (Mux(positive, difference, remainder))
      ready := true.B
      busy := false.B
    } .otherwise {
      counter := counter + 1.U
    }
  }

}

/*

// TODO: Fix REM, REMU, DIV, DIVU to correctly overflow and divide by zero
import MULDIV._

class MULDIV_Impl extends Module {
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
  val MULDIV_MUL    = 16.U(5.W)
  val MULDIV_MULH   = 17.U(5.W)
  val MULDIV_MULHSU = 18.U(5.W)
  val MULDIV_MULHU  = 19.U(5.W)
  val MULDIV_DIV    = 20.U(5.W)
  val MULDIV_DIVU   = 21.U(5.W)
  val MULDIV_REM    = 22.U(5.W)
  val MULDIV_REMU   = 23.U(5.W)*/