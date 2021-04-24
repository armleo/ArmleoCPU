package armleocpu


import chisel3._
import chisel3.util._

import Control._

class Execute(log: Boolean) extends Module {
	val io = IO(new Bundle{
			val instr = Input(UInt(32.W))
			val pc = Input(UInt(32.W))
			val pipeline_wait = Output(Bool())
			val pipeline_kill = Output(Bool())
			val cache_flush = Output(Bool())
			val pc_sel = Output(UInt(2.W))
			val br_taken = Output(Bool())
      //val memhost = new MemHostIf()
	})
	
	val alu = Module(new Alu_imp)
	val control = Module(new ControlUnit)
	val regfile = Module(new Regfile(false))
	val immgen = Module(new ImmGen)
	val brcond = Module(new BrCond)
	//val dcache = Module(new Cache)

	io.pipeline_wait := 0.U
	io.pipeline_kill := control.io.inst_kill
	io.cache_flush := control.io.flush
	io.pc_sel := control.io.pc_sel
	


	// control
	control.io.inst := io.instr

	// regfile
	regfile.io.rs1.address := io.instr(24, 20)
	regfile.io.rs2.address := io.instr(19, 15)
	regfile.io.rd.address := io.instr(11, 7)
	regfile.io.rd.write := control.io.wb_en
	regfile.io.rd.data := Mux(
		control.io.wb_sel === WB_ALU, alu.io.out, 0.U(32.W)
		//Mux(control.io.wb_sel == WB_MEM // TODO: Memory writeback
	)
	// immgen
	immgen.io.inst := io.instr
	immgen.io.sel := control.io.imm_sel
	//immgen.out
	
	// alu
	alu.io.op := control.io.alu_op
	alu.io.A := Mux(control.io.A_sel === A_RS1, regfile.io.rs1.data, io.pc)
	alu.io.B := Mux(control.io.B_sel === B_RS2, regfile.io.rs2.data, immgen.io.out)
	alu.io.shamt := io.instr(24, 20)

	// brcond
	brcond.io.rs1 := regfile.io.rs1.data
	brcond.io.rs2 := regfile.io.rs2.data
	brcond.io.br_type := control.io.br_type
	io.br_taken := brcond.io.taken
	
	//val dcache = Module(new Cache)
	//dcache.io.write := st_type =/= ST_XXX && stall
	//dcache.io.read := ld_type =/= LD_XXX && stall
	//dcache.io.address := 
	//dcache.memhost <> io.memhost
}