package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

import Instructions._
import armleocpu.utils._


class execute_uop_t(c: CoreParams) extends decode_uop_t(c) {
  // Using signed, so it will be sign extended
  val alu_out         = SInt(c.xLen.W)
  //val muldiv_out      = SInt(c.xLen.W)
  val branch_taken    = Bool()
}

class Execute(val c: CoreParams = new CoreParams) extends Module {
  val kill                = IO(Input(Bool()))

  val decode_uop_accept   = IO(Output(Bool()))
  val decode_uop_valid    = IO(Input(Bool()))
  val decode_uop          = IO(Input(new decode_uop_t(c)))

  val uop_accept    = IO(Input(Bool()))
  val uop_valid_o   = IO(Output(Bool()))
  val uop_o         = IO(Output(new execute_uop_t(c)))

  
  val execute_uop        = Reg(new execute_uop_t(c))
  val execute_uop_valid  = RegInit(false.B)

  uop_valid_o := execute_uop_valid
  uop_o       := execute_uop

  val log = new Logger(c.lp.coreName, f"exec ", c.core_verbose)

  /**************************************************************************/
  /*                Decode pipeline combinational signals                   */
  /**************************************************************************/

  // Ignore the below mumbo jumbo
  // It was the easiest way to get universal instructions without checking c.xLen for each
  val decode_uop_simm12 = Wire(SInt(c.xLen.W))
  decode_uop_simm12 := decode_uop.instr(31, 20).asSInt()

  // The regfile has unknown register state for address 0
  // This is by-design
  // So instead we MUX zero at execute1 stage if its read from 0th register

  val execute1_rs1_data = Mux(decode_uop.instr(19, 15) =/= 0.U, decode_uop.rs1_data, 0.U)
  val execute1_rs2_data = Mux(decode_uop.instr(24, 20) =/= 0.U, decode_uop.rs2_data, 0.U)
  
  val decode_uop_shamt_xlen = Wire(UInt(c.xLen_log2.W))
  val decode_uop_rs2_shift_xlen = Wire(UInt(c.xLen_log2.W))
  if(c.xLen == 32) {
    decode_uop_shamt_xlen := decode_uop.instr(24, 20)
    decode_uop_rs2_shift_xlen := execute1_rs2_data(4, 0)
  } else {
    decode_uop_shamt_xlen := decode_uop.instr(25, 20)
    decode_uop_rs2_shift_xlen := execute1_rs2_data(5, 0)
  }

  decode_uop_accept := false.B
  def execute1_debug(instr: String): Unit = {
    log(f"$instr instr=0x%%x, pc=0x%%x", decode_uop.instr, decode_uop.pc)
  }

  when(!execute_uop_valid || (execute_uop_valid && uop_accept)) {
    when(decode_uop_valid && !kill) {
      decode_uop_accept := true.B

      execute_uop.viewAsSupertype(chiselTypeOf(decode_uop)) := decode_uop
      execute_uop_valid        := true.B

      execute_uop.alu_out      := 0.S
      //execute_uop.muldiv_out   := 0.S(c.xLen.W)

      execute_uop.branch_taken := false.B

      /**************************************************************************/
      /*                                                                        */
      /*                Alu-like EXECUTE1                                       */
      /*                                                                        */
      /**************************************************************************/
      when(decode_uop.instr === LUI) {
        // Use SInt to sign extend it before writing
        execute_uop.alu_out    := Cat(decode_uop.instr(31, 12), 0.U(12.W)).asSInt()
        
      } .elsewhen(decode_uop.instr === AUIPC) {
        execute_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31, 12), 0.U(12.W)).asSInt()
        execute1_debug("AUIPC")
      
      /**************************************************************************/
      /*                                                                        */
      /*                Branching EXECUTE1                                      */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(decode_uop.instr === JAL) {
        execute_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(19, 12), decode_uop.instr(20), decode_uop.instr(30, 21), 0.U(1.W)).asSInt()
        execute1_debug("JAL")
      } .elsewhen(decode_uop.instr === JALR) {
        execute_uop.alu_out    := execute1_rs1_data.asSInt() + decode_uop.instr(31, 20).asSInt()
        execute1_debug("JALR")
      } .elsewhen        (decode_uop.instr === BEQ) {
        execute_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute_uop.branch_taken   := execute1_rs1_data          === execute1_rs2_data
        execute1_debug("BEQ")
      } .elsewhen (decode_uop.instr === BNE) {
        execute_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute_uop.branch_taken   := execute1_rs1_data          =/= execute1_rs2_data
        execute1_debug("BNE")
      } .elsewhen (decode_uop.instr === BLT) {
        execute_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute_uop.branch_taken   := execute1_rs1_data.asSInt()  <  execute1_rs2_data.asSInt()
        execute1_debug("BLT")
      } .elsewhen (decode_uop.instr === BLTU) {
        execute_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute_uop.branch_taken   := execute1_rs1_data.asUInt()  <  execute1_rs2_data.asUInt()
        execute1_debug("BLTU")
      } .elsewhen (decode_uop.instr === BGE) {
        execute_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute_uop.branch_taken   := execute1_rs1_data.asSInt() >=  execute1_rs2_data.asSInt()
        execute1_debug("BGE")
      } .elsewhen (decode_uop.instr === BGEU) {
        execute_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute_uop.branch_taken   := execute1_rs1_data.asUInt() >=  execute1_rs2_data.asUInt()
        execute1_debug("BGEU")
      /**************************************************************************/
      /*                                                                        */
      /*                Memory EXECUTE1                                         */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(decode_uop.instr === LOAD) {
        execute_uop.alu_out := execute1_rs1_data.asSInt() + decode_uop.instr(31, 20).asSInt()
        execute1_debug("LOAD")
      } .elsewhen(decode_uop.instr === STORE) {
        execute_uop.alu_out := execute1_rs1_data.asSInt() + Cat(decode_uop.instr(31, 25), decode_uop.instr(11, 7)).asSInt()
        execute1_debug("STORE")
      
      /**************************************************************************/
      /*                                                                        */
      /*                ALU EXECUTE1                                            */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(decode_uop.instr === ADD) { // ALU instructions
        execute_uop.alu_out := execute1_rs1_data.asSInt() + execute1_rs2_data.asSInt()
        execute1_debug("ADD")
      } .elsewhen(decode_uop.instr === SUB) {
        execute_uop.alu_out := execute1_rs1_data.asSInt() - execute1_rs2_data.asSInt()
        execute1_debug("SUB")
      } .elsewhen(decode_uop.instr === AND) {
        execute_uop.alu_out := execute1_rs1_data.asSInt() & execute1_rs2_data.asSInt()
        execute1_debug("AND")
      } .elsewhen(decode_uop.instr === OR) {
        execute_uop.alu_out := execute1_rs1_data.asSInt() | execute1_rs2_data.asSInt()
        execute1_debug("OR")
      } .elsewhen(decode_uop.instr === XOR) {
        execute_uop.alu_out := execute1_rs1_data.asSInt() ^ execute1_rs2_data.asSInt()
        execute1_debug("XOR")
      } .elsewhen(decode_uop.instr === SLL) {
        // TODO: RV64 add SLL/SRL/SRA for 64 bit
        // Explaination of below
        // SLL and SLLW are equivalent (and others). But in RV64 you need to sign extends 32 bits
        execute_uop.alu_out := (execute1_rs1_data.asUInt() << decode_uop_rs2_shift_xlen)(31, 0).asSInt()
        execute1_debug("SLL")
      } .elsewhen(decode_uop.instr === SRL) {
        execute_uop.alu_out := (execute1_rs1_data.asUInt() >> decode_uop_rs2_shift_xlen)(31, 0).asSInt()
        execute1_debug("SRL")
      } .elsewhen(decode_uop.instr === SRA) {
        execute_uop.alu_out := (execute1_rs1_data.asSInt() >> decode_uop_rs2_shift_xlen)(31, 0).asSInt()
        execute1_debug("SRA")
      } .elsewhen(decode_uop.instr === SLT) {
        // TODO: RV64 Fix below
        execute_uop.alu_out := (execute1_rs1_data.asSInt() < execute1_rs2_data.asSInt()).asSInt()
        execute1_debug("SLT")
      } .elsewhen(decode_uop.instr === SLTU) {
        execute_uop.alu_out := (execute1_rs1_data.asUInt() < execute1_rs2_data.asUInt()).asSInt()
        execute1_debug("SLTU")
      } .elsewhen(decode_uop.instr === ADDI) {
        execute_uop.alu_out := execute1_rs1_data.asSInt() + decode_uop_simm12
        execute1_debug("ADDI")
      } .elsewhen(decode_uop.instr === SLTI) {
        execute_uop.alu_out := (execute1_rs1_data.asSInt() < decode_uop_simm12).asSInt()
        execute1_debug("SLTI")
      } .elsewhen(decode_uop.instr === SLTIU) {
        execute_uop.alu_out := (execute1_rs1_data.asUInt() < decode_uop_simm12.asUInt()).asSInt()
        execute1_debug("SLTIU")
      } .elsewhen(decode_uop.instr === ANDI) {
        execute_uop.alu_out := (execute1_rs1_data.asUInt() & decode_uop_simm12.asUInt()).asSInt()
        execute1_debug("ANDI")
      } .elsewhen(decode_uop.instr === ORI) {
        execute_uop.alu_out := (execute1_rs1_data.asUInt() | decode_uop_simm12.asUInt()).asSInt()
        execute1_debug("ORI")
      } .elsewhen(decode_uop.instr === XORI) {
        execute_uop.alu_out := (execute1_rs1_data.asUInt() ^ decode_uop_simm12.asUInt()).asSInt()
        execute1_debug("XORI")
      } .elsewhen(decode_uop.instr === SLLI) {
        execute_uop.alu_out := (execute1_rs1_data.asUInt() << decode_uop_shamt_xlen).asSInt()
        execute1_debug("SLLI")
      } .elsewhen(decode_uop.instr === SRLI) {
        execute_uop.alu_out := (execute1_rs1_data.asUInt() >> decode_uop_shamt_xlen).asSInt()
        execute1_debug("SRLI")
      } .elsewhen(decode_uop.instr === SRAI) {
        execute_uop.alu_out := (execute1_rs1_data.asSInt() >> decode_uop_shamt_xlen).asSInt()
        execute1_debug("SRAI")
      /**************************************************************************/
      /*                                                                        */
      /*                Alu-like EXECUTE1                                       */
      /*                                                                        */
      /**************************************************************************/
      } .otherwise {
        execute1_debug("No-action for execute1")
      }
      
      // TODO: RV64 Add the 64 bit shortened 32 bit versions
      // TODO: RV64 add the 64 bit instruction tests
      // TODO: MULDIV here


    } .otherwise { // Decode has no instruction.
      log("No instruction found or instruction killed")
      execute_uop_valid := false.B
    }
  } .elsewhen(kill) {
    execute_uop_valid := false.B
    log("Instr killed")
  }
}