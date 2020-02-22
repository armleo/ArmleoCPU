package armleocpu

import chisel3._
import chisel3.util._

import Control._


class BrCond extends Module {
	val io = IO(new Bundle{
		val rs1 = Input(UInt(32.W))
		val rs2 = Input(UInt(32.W))
		val br_type = Input(UInt(3.W))
		val taken = Output(Bool())
	})

	val eq   = io.rs1 === io.rs2
	val neq  = !eq
	val lt   = io.rs1.asSInt < io.rs2.asSInt
	val ge   = !lt
	val ltu  = io.rs1 < io.rs2
	val geu  = !ltu
	io.taken :=     
		((io.br_type === BR_EQ) && eq) ||
		((io.br_type === BR_NE) && neq) ||
		((io.br_type === BR_LT) && lt) ||
		((io.br_type === BR_GE) && ge) ||
		((io.br_type === BR_LTU) && ltu) ||
		((io.br_type === BR_GEU) && geu)
}