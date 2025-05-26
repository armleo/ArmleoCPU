package armleocpu


import chisel3._
import chisel3.util._

import chisel3.util._
import chisel3.experimental.dataview._

import Instructions._



class execute_uop_t(c: CoreParams) extends decode_uop_t(c) {
  // Using signed, so it will be sign extended
  val alu_out         = SInt(c.xLen.W)
  //val muldiv_out      = SInt(c.xLen.W)
  val branch_taken    = Bool()
}

class Execute(val c: CoreParams = new CoreParams) extends Module {
  val kill                = IO(Input(Bool()))

  val uop_i         = IO(Flipped(DecoupledIO(new decode_uop_t(c))))

  val uop_o         = IO(DecoupledIO(new execute_uop_t(c)))
  
  val uop_o_bits        = Reg(new execute_uop_t(c))
  val uop_o_valid       = RegInit(false.B)

  uop_o_valid       := uop_o.valid
  uop_o_bits        := uop_o

  val log = new Logger(c.lp.coreName, f"exec ", c.core_verbose)

  /**************************************************************************/
  /*                Decode pipeline combinational signals                   */
  /**************************************************************************/

  // Ignore the below mumbo jumbo
  // It was the easiest way to get universal instructions without checking c.xLen for each
  val uop_i_simm12 = Wire(SInt(c.xLen.W))
  uop_i_simm12 := uop_i.bits.instr(31, 20).asSInt

  // The regfile has unknown register state for address 0
  // This is by-design
  // So instead we MUX zero at execute1 stage if its read from 0th register

  val execute1_rs1_data = Mux(uop_i.bits.instr(19, 15) =/= 0.U, uop_i.bits.rs1_data, 0.U)
  val execute1_rs2_data = Mux(uop_i.bits.instr(24, 20) =/= 0.U, uop_i.bits.rs2_data, 0.U)
  
  val uop_i_shamt_xlen = Wire(UInt(c.xLen_log2.W))
  val uop_i_rs2_shift_xlen = Wire(UInt(c.xLen_log2.W))
  
  uop_i_shamt_xlen := uop_i.bits.instr(25, 20)
  uop_i_rs2_shift_xlen := execute1_rs2_data(5, 0)
  

  uop_i.ready := false.B
  def execute1_debug(instr: String): Unit = {
    log(f"$instr instr=0x%%x, pc=0x%%x", uop_i.bits.instr, uop_i.bits.pc)
  }

  when(!uop_o.valid || (uop_o.valid && uop_o.ready)) {
    when(uop_i.valid && !kill) {
      uop_i.ready := true.B

      uop_o.bits.viewAsSupertype(chiselTypeOf(uop_i.bits)) := uop_i.bits
      uop_o.valid        := true.B

      uop_o.bits.alu_out      := 0.S
      //uop_o.muldiv_out   := 0.S(c.xLen.W)

      uop_o.bits.branch_taken := false.B

      /**************************************************************************/
      /*                                                                        */
      /*                Alu-like EXECUTE1                                       */
      /*                                                                        */
      /**************************************************************************/
      when(uop_i.bits.instr === LUI) {
        // Use SInt to sign extend it before writing
        uop_o.bits.alu_out    := Cat(uop_i.bits.instr(31, 12), 0.U(12.W)).asSInt
        
      } .elsewhen(uop_i.bits.instr === AUIPC) {
        uop_o.bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31, 12), 0.U(12.W)).asSInt
        execute1_debug("AUIPC")
      
      /**************************************************************************/
      /*                                                                        */
      /*                Branching EXECUTE1                                      */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(uop_i.bits.instr === JAL) {
        uop_o.bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(19, 12), uop_i.bits.instr(20), uop_i.bits.instr(30, 21), 0.U(1.W)).asSInt
        execute1_debug("JAL")
      } .elsewhen(uop_i.bits.instr === JALR) {
        uop_o.bits.alu_out    := execute1_rs1_data.asSInt + uop_i.bits.instr(31, 20).asSInt
        execute1_debug("JALR")
      } .elsewhen        (uop_i.bits.instr === BEQ) {
        uop_o.bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o.bits.branch_taken   := execute1_rs1_data          === execute1_rs2_data
        execute1_debug("BEQ")
      } .elsewhen (uop_i.bits.instr === BNE) {
        uop_o.bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o.bits.branch_taken   := execute1_rs1_data          =/= execute1_rs2_data
        execute1_debug("BNE")
      } .elsewhen (uop_i.bits.instr === BLT) {
        uop_o.bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o.bits.branch_taken   := execute1_rs1_data.asSInt  <  execute1_rs2_data.asSInt
        execute1_debug("BLT")
      } .elsewhen (uop_i.bits.instr === BLTU) {
        uop_o.bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o.bits.branch_taken   := execute1_rs1_data.asUInt  <  execute1_rs2_data.asUInt
        execute1_debug("BLTU")
      } .elsewhen (uop_i.bits.instr === BGE) {
        uop_o.bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o.bits.branch_taken   := execute1_rs1_data.asSInt >=  execute1_rs2_data.asSInt
        execute1_debug("BGE")
      } .elsewhen (uop_i.bits.instr === BGEU) {
        uop_o.bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o.bits.branch_taken   := execute1_rs1_data.asUInt >=  execute1_rs2_data.asUInt
        execute1_debug("BGEU")
      /**************************************************************************/
      /*                                                                        */
      /*                Memory EXECUTE1                                         */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(uop_i.bits.instr === LOAD) {
        uop_o.bits.alu_out := execute1_rs1_data.asSInt + uop_i.bits.instr(31, 20).asSInt
        execute1_debug("LOAD")
      } .elsewhen(uop_i.bits.instr === STORE) {
        uop_o.bits.alu_out := execute1_rs1_data.asSInt + Cat(uop_i.bits.instr(31, 25), uop_i.bits.instr(11, 7)).asSInt
        execute1_debug("STORE")
      
      /**************************************************************************/
      /*                                                                        */
      /*                ALU EXECUTE1                                            */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(uop_i.bits.instr === ADD) { // ALU instructions
        uop_o.bits.alu_out := execute1_rs1_data.asSInt + execute1_rs2_data.asSInt
        execute1_debug("ADD")
      } .elsewhen(uop_i.bits.instr === SUB) {
        uop_o.bits.alu_out := execute1_rs1_data.asSInt - execute1_rs2_data.asSInt
        execute1_debug("SUB")
      } .elsewhen(uop_i.bits.instr === AND) {
        uop_o.bits.alu_out := execute1_rs1_data.asSInt & execute1_rs2_data.asSInt
        execute1_debug("AND")
      } .elsewhen(uop_i.bits.instr === OR) {
        uop_o.bits.alu_out := execute1_rs1_data.asSInt | execute1_rs2_data.asSInt
        execute1_debug("OR")
      } .elsewhen(uop_i.bits.instr === XOR) {
        uop_o.bits.alu_out := execute1_rs1_data.asSInt ^ execute1_rs2_data.asSInt
        execute1_debug("XOR")
      } .elsewhen(uop_i.bits.instr === SLL) {
        // TODO: RV64 add SLL/SRL/SRA for 64 bit
        // Explaination of below
        // SLL and SLLW are equivalent (and others). But in RV64 you need to sign extends 32 bits
        uop_o.bits.alu_out := (execute1_rs1_data.asUInt << uop_i_rs2_shift_xlen)(31, 0).asSInt
        execute1_debug("SLL")
      } .elsewhen(uop_i.bits.instr === SRL) {
        uop_o.bits.alu_out := (execute1_rs1_data.asUInt >> uop_i_rs2_shift_xlen)(31, 0).asSInt
        execute1_debug("SRL")
      } .elsewhen(uop_i.bits.instr === SRA) {
        uop_o.bits.alu_out := (execute1_rs1_data.asSInt >> uop_i_rs2_shift_xlen)(31, 0).asSInt
        execute1_debug("SRA")
      } .elsewhen(uop_i.bits.instr === SLT) {
        // TODO: RV64 Fix below
        uop_o.bits.alu_out := (execute1_rs1_data.asSInt < execute1_rs2_data.asSInt).asSInt
        execute1_debug("SLT")
      } .elsewhen(uop_i.bits.instr === SLTU) {
        uop_o.bits.alu_out := (execute1_rs1_data.asUInt < execute1_rs2_data.asUInt).asSInt
        execute1_debug("SLTU")
      } .elsewhen(uop_i.bits.instr === ADDI) {
        uop_o.bits.alu_out := execute1_rs1_data.asSInt + uop_i_simm12
        execute1_debug("ADDI")
      } .elsewhen(uop_i.bits.instr === SLTI) {
        uop_o.bits.alu_out := (execute1_rs1_data.asSInt < uop_i_simm12).asSInt
        execute1_debug("SLTI")
      } .elsewhen(uop_i.bits.instr === SLTIU) {
        uop_o.bits.alu_out := (execute1_rs1_data.asUInt < uop_i_simm12.asUInt).asSInt
        execute1_debug("SLTIU")
      } .elsewhen(uop_i.bits.instr === ANDI) {
        uop_o.bits.alu_out := (execute1_rs1_data.asUInt & uop_i_simm12.asUInt).asSInt
        execute1_debug("ANDI")
      } .elsewhen(uop_i.bits.instr === ORI) {
        uop_o.bits.alu_out := (execute1_rs1_data.asUInt | uop_i_simm12.asUInt).asSInt
        execute1_debug("ORI")
      } .elsewhen(uop_i.bits.instr === XORI) {
        uop_o.bits.alu_out := (execute1_rs1_data.asUInt ^ uop_i_simm12.asUInt).asSInt
        execute1_debug("XORI")
      } .elsewhen(uop_i.bits.instr === SLLI) {
        uop_o.bits.alu_out := (execute1_rs1_data.asUInt << uop_i_shamt_xlen).asSInt
        execute1_debug("SLLI")
      } .elsewhen(uop_i.bits.instr === SRLI) {
        uop_o.bits.alu_out := (execute1_rs1_data.asUInt >> uop_i_shamt_xlen).asSInt
        execute1_debug("SRLI")
      } .elsewhen(uop_i.bits.instr === SRAI) {
        uop_o.bits.alu_out := (execute1_rs1_data.asSInt >> uop_i_shamt_xlen).asSInt
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
      uop_o.valid := false.B
    }
  } .elsewhen(kill) {
    uop_o.valid := false.B
    log("Instr killed")
  }
}