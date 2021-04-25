package armleocpu

import chisel3._
import chisel3.util._


object Control {
	val Y = true.B
	val N = false.B

	// pc_sel
	val PC_4   = 0.U(2.W)
	val PC_ALU = 1.U(2.W)
	val PC_0   = 2.U(2.W)
	val PC_EPC = 3.U(2.W)

	// A_sel
	val A_XXX  = 0.U(1.W)
	val A_PC   = 0.U(1.W)
	val A_RS1  = 1.U(1.W)

	// B_sel
	val B_XXX  = 0.U(1.W)
	val B_IMM  = 0.U(1.W)
	val B_RS2  = 1.U(1.W)

	// imm_sel
	val IMM_X  = 0.U(3.W)
	val IMM_I  = 1.U(3.W)
	val IMM_S  = 2.U(3.W)
	val IMM_U  = 3.U(3.W)
	val IMM_J  = 4.U(3.W)
	val IMM_B  = 5.U(3.W)
	val IMM_Z  = 6.U(3.W)

	// br_type
	val BR_XXX = 0.U(3.W)
	val BR_LTU = 1.U(3.W)
	val BR_LT  = 2.U(3.W)
	val BR_EQ  = 3.U(3.W)
	val BR_GEU = 4.U(3.W)
	val BR_GE  = 5.U(3.W)
	val BR_NE  = 6.U(3.W)

	// st_type
	val ST_XXX = 0.U(2.W)
	val ST_SW  = 1.U(2.W)
	val ST_SH  = 2.U(2.W)
	val ST_SB  = 3.U(2.W)

	// ld_type
	val LD_XXX = 0.U(3.W)
	val LD_LW  = 1.U(3.W)
	val LD_LH  = 2.U(3.W)
	val LD_LB  = 3.U(3.W)
	val LD_LHU = 4.U(3.W)
	val LD_LBU = 5.U(3.W)

	// wb_sel
	val WB_ALU = 0.U(2.W)
	val WB_MEM = 1.U(2.W)
	val WB_PC4 = 2.U(2.W)
	val WB_CSR = 3.U(2.W)
	// TODO, add FLUSH
	import Instructions._
	import ALU._
	/*
	// add FENCE and FENCEI for flushing
	val default =
		//                                                            kill                        wb_en  illegal?
		//            pc_sel  A_sel   B_sel  imm_sel   alu_op   br_type |  st_type ld_type wb_sel  | csr_cmd | flush
		//              |       |       |     |          |          |   |     |       |       |    |  |      |  |
				 List(PC_4,   A_XXX,  B_XXX, IMM_X, ALU_XXX   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, Y, N)
	val map = Array(
		// LUI, AUIPC
		LUI   -> List(PC_4  , A_PC,   B_IMM, IMM_U, ALU_COPY_B, BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		AUIPC -> List(PC_4  , A_PC,   B_IMM, IMM_U, ALU_ADD   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),

		// Jump and Link
		JAL   -> List(PC_ALU, A_PC,   B_IMM, IMM_J, ALU_ADD   , BR_XXX, Y, ST_XXX, LD_XXX, WB_PC4, Y, CSR.N, N, N),
		JALR  -> List(PC_ALU, A_RS1,  B_IMM, IMM_I, ALU_ADD   , BR_XXX, Y, ST_XXX, LD_XXX, WB_PC4, Y, CSR.N, N, N),
		// Conditional branches
		BEQ   -> List(PC_4  , A_PC,   B_IMM, IMM_B, ALU_ADD   , BR_EQ , N, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, N, N),
		BNE   -> List(PC_4  , A_PC,   B_IMM, IMM_B, ALU_ADD   , BR_NE , N, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, N, N),
		BLT   -> List(PC_4  , A_PC,   B_IMM, IMM_B, ALU_ADD   , BR_LT , N, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, N, N),
		BGE   -> List(PC_4  , A_PC,   B_IMM, IMM_B, ALU_ADD   , BR_GE , N, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, N, N),
		BLTU  -> List(PC_4  , A_PC,   B_IMM, IMM_B, ALU_ADD   , BR_LTU, N, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, N, N),
		BGEU  -> List(PC_4  , A_PC,   B_IMM, IMM_B, ALU_ADD   , BR_GEU, N, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, N, N),
		// Load
		LB    -> List(PC_0  , A_RS1,  B_IMM, IMM_I, ALU_ADD   , BR_XXX, Y, ST_XXX, LD_LB , WB_MEM, Y, CSR.N, N, N),
		LH    -> List(PC_0  , A_RS1,  B_IMM, IMM_I, ALU_ADD   , BR_XXX, Y, ST_XXX, LD_LH , WB_MEM, Y, CSR.N, N, N),
		LW    -> List(PC_0  , A_RS1,  B_IMM, IMM_I, ALU_ADD   , BR_XXX, Y, ST_XXX, LD_LW , WB_MEM, Y, CSR.N, N, N),
		LBU   -> List(PC_0  , A_RS1,  B_IMM, IMM_I, ALU_ADD   , BR_XXX, Y, ST_XXX, LD_LBU, WB_MEM, Y, CSR.N, N, N),
		LHU   -> List(PC_0  , A_RS1,  B_IMM, IMM_I, ALU_ADD   , BR_XXX, Y, ST_XXX, LD_LHU, WB_MEM, Y, CSR.N, N, N),
		// Store
		SB    -> List(PC_4  , A_RS1,  B_IMM, IMM_S, ALU_ADD   , BR_XXX, N, ST_SB , LD_XXX, WB_ALU, N, CSR.N, N, N),
		SH    -> List(PC_4  , A_RS1,  B_IMM, IMM_S, ALU_ADD   , BR_XXX, N, ST_SH , LD_XXX, WB_ALU, N, CSR.N, N, N),
		SW    -> List(PC_4  , A_RS1,  B_IMM, IMM_S, ALU_ADD   , BR_XXX, N, ST_SW , LD_XXX, WB_ALU, N, CSR.N, N, N),
		// ALUI
		ADDI  -> List(PC_4  , A_RS1,  B_IMM, IMM_I, ALU_ADD   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SLTI  -> List(PC_4  , A_RS1,  B_IMM, IMM_I, ALU_SLT   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SLTIU -> List(PC_4  , A_RS1,  B_IMM, IMM_I, ALU_SLTU  , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		XORI  -> List(PC_4  , A_RS1,  B_IMM, IMM_I, ALU_XOR   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		ORI   -> List(PC_4  , A_RS1,  B_IMM, IMM_I, ALU_OR    , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		ANDI  -> List(PC_4  , A_RS1,  B_IMM, IMM_I, ALU_AND   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SLLI  -> List(PC_4  , A_RS1,  B_IMM, IMM_I, ALU_SLL   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SRLI  -> List(PC_4  , A_RS1,  B_IMM, IMM_I, ALU_SRL   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SRAI  -> List(PC_4  , A_RS1,  B_IMM, IMM_I, ALU_SRA   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		// ALU
		ADD   -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_ADD   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SUB   -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_SUB   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SLL   -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_SLL   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SLT   -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_SLT   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SLTU  -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_SLTU  , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		XOR   -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_XOR   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SRL   -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_SRL   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		SRA   -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_SRA   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		OR    -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_OR    , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		AND   -> List(PC_4  , A_RS1,  B_RS2, IMM_X, ALU_AND   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, Y, CSR.N, N, N),
		// Add mul, div, rem operations

		// Flushes
		FENCE -> List(PC_4  , A_XXX,  B_XXX, IMM_X, ALU_XXX   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, N, Y),
		FENCEI-> List(PC_0  , A_XXX,  B_XXX, IMM_X, ALU_XXX   , BR_XXX, Y, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, N, Y),

		// CSRs
		CSRRW -> List(PC_0  , A_RS1,  B_XXX, IMM_X, ALU_COPY_A, BR_XXX, Y, ST_XXX, LD_XXX, WB_CSR, Y, CSR.W, N, N),
		CSRRS -> List(PC_0  , A_RS1,  B_XXX, IMM_X, ALU_COPY_A, BR_XXX, Y, ST_XXX, LD_XXX, WB_CSR, Y, CSR.S, N, N),
		CSRRC -> List(PC_0  , A_RS1,  B_XXX, IMM_X, ALU_COPY_A, BR_XXX, Y, ST_XXX, LD_XXX, WB_CSR, Y, CSR.C, N, N),
		CSRRWI-> List(PC_0  , A_XXX,  B_XXX, IMM_Z, ALU_XXX   , BR_XXX, Y, ST_XXX, LD_XXX, WB_CSR, Y, CSR.W, N, N),
		CSRRSI-> List(PC_0  , A_XXX,  B_XXX, IMM_Z, ALU_XXX   , BR_XXX, Y, ST_XXX, LD_XXX, WB_CSR, Y, CSR.S, N, N),
		CSRRCI-> List(PC_0  , A_XXX,  B_XXX, IMM_Z, ALU_XXX   , BR_XXX, Y, ST_XXX, LD_XXX, WB_CSR, Y, CSR.C, N, N),
		// ecall, ebreak, eret, wfi
		ECALL -> List(PC_4  , A_XXX,  B_XXX, IMM_X, ALU_XXX   , BR_XXX, N, ST_XXX, LD_XXX, WB_CSR, N, CSR.P, N, N),
		EBREAK-> List(PC_4  , A_XXX,  B_XXX, IMM_X, ALU_XXX   , BR_XXX, N, ST_XXX, LD_XXX, WB_CSR, N, CSR.P, N, N),
		ERET  -> List(PC_EPC, A_XXX,  B_XXX, IMM_X, ALU_XXX   , BR_XXX, Y, ST_XXX, LD_XXX, WB_CSR, N, CSR.P, N, N),
		WFI   -> List(PC_4  , A_XXX,  B_XXX, IMM_X, ALU_XXX   , BR_XXX, N, ST_XXX, LD_XXX, WB_ALU, N, CSR.N, N, N)
	)*/
}

class ControlSignals extends Bundle {
	val inst      = Input(UInt(32.W))
	val pc_sel    = Output(UInt(2.W))
	val inst_kill = Output(Bool())
	val A_sel     = Output(UInt(1.W))
	val B_sel     = Output(UInt(1.W))
	val imm_sel   = Output(UInt(3.W))
	val alu_op    = Output(UInt(4.W))
	val br_type   = Output(UInt(3.W))
	val st_type   = Output(UInt(2.W))
	val ld_type   = Output(UInt(3.W))
	val wb_sel    = Output(UInt(2.W))
	val wb_en     = Output(Bool())
	val csr_cmd   = Output(UInt(3.W))
	val illegal   = Output(Bool())
	val flush     = Output(Bool())
}


class ControlUnit extends Module {
	val io = IO(new ControlSignals)
	val ctrlSignals = 0.U(32.W) //ListLookup(io.inst, Control.default, Control.map)

	// Control signals for Fetch
	io.pc_sel    := ctrlSignals(0)
	io.inst_kill := ctrlSignals(6)

	// Control signals for Execute
	io.A_sel   := ctrlSignals(1)
	io.B_sel   := ctrlSignals(2)
	io.imm_sel := ctrlSignals(3)
	io.alu_op  := ctrlSignals(4)
	io.br_type := ctrlSignals(5)
	io.st_type := ctrlSignals(7)
	io.ld_type := ctrlSignals(8)
	io.wb_sel  := ctrlSignals(9)
	io.wb_en   := ctrlSignals(10)
	io.csr_cmd := ctrlSignals(11)
	io.illegal := ctrlSignals(12)
	io.flush   := ctrlSignals(13)
}