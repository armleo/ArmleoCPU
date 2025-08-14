package armleocpu


import chisel3._
import chisel3.util._

import chisel3.util._
import chisel3.experimental.dataview._

import Instructions._

class execute_uop_t(implicit ccx: CCXParams) extends decode_uop_t {
  // Using signed, so it will be sign extended
  val alu_out         = SInt(ccx.xLen.W)
  //val muldiv_out      = SInt(ccx.xLen.W)
  val branch_taken    = Bool()
}

class Execute(implicit ccx: CCXParams) extends CCXModule {
  val ctrl              = IO(new PipelineControlIO) // Pipeline command interface form control unit

  val uop_i         = IO(Flipped(DecoupledIO(new decode_uop_t)))
  val uop_o         = IO(DecoupledIO(new execute_uop_t))
  

  val uop_o_bits        = Reg(new execute_uop_t)
  val uop_o_valid       = RegInit(false.B)

  uop_o.valid       := uop_o_valid
  uop_o.bits        := uop_o_bits
  
  /**************************************************************************/
  /*                Decode pipeline combinational signals                   */
  /**************************************************************************/
  val kill                = ctrl.kill || ctrl.flush || ctrl.jump
  ctrl.busy               := uop_o.valid

  // Ignore the below mumbo jumbo
  // It was the easiest way to get universal instructions without checking ccx.xLen for each
  val uop_i_simm12 = Wire(SInt(ccx.xLen.W))
  uop_i_simm12 := uop_i.bits.instr(31, 20).asSInt

  // The regfile has unknown register state for address 0
  // This is by-design
  // So instead we MUX zero at execute stage if its read from 0th register

  val execute_rs1_data = Mux(uop_i.bits.instr(19, 15) =/= 0.U, uop_i.bits.rs1_data, 0.U)
  val execute_rs2_data = Mux(uop_i.bits.instr(24, 20) =/= 0.U, uop_i.bits.rs2_data, 0.U)
  
  val uop_i_shamt_xlen = Wire(UInt(ccx.xLenLog2.W))
  val uop_i_rs2_shift_xlen = Wire(UInt(ccx.xLenLog2.W))
  
  uop_i_shamt_xlen := uop_i.bits.instr(25, 20)
  uop_i_rs2_shift_xlen := execute_rs2_data(5, 0)
  

  uop_i.ready := false.B
  def execute_debug(instr: String): Unit = {
    log(cf"$instr instr=0x${uop_i.bits.instr}%x, pc=0x${uop_i.bits.pc}%x")
  }

  
  when(!uop_o_valid || (uop_o_valid && uop_o.ready) || kill) {
    when(uop_i.valid && !kill) {
      uop_i.ready := true.B

      uop_o_bits.viewAsSupertype(chiselTypeOf(uop_i.bits)) := uop_i.bits
      uop_o_valid        := true.B

      uop_o_bits.alu_out      := 0.S
      //uop_o.muldiv_out   := 0.S(ccx.xLen.W)

      uop_o_bits.branch_taken := false.B

      /**************************************************************************/
      /*                                                                        */
      /*                Alu-like EXECUTE                                       */
      /*                                                                        */
      /**************************************************************************/
      when(uop_i.bits.instr === LUI) {
        // Use SInt to sign extend it before writing
        uop_o_bits.alu_out    := Cat(uop_i.bits.instr(31, 12), 0.U(12.W)).asSInt
        
      } .elsewhen(uop_i.bits.instr === AUIPC) {
        uop_o_bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31, 12), 0.U(12.W)).asSInt
        execute_debug("AUIPC")
      
      /**************************************************************************/
      /*                                                                        */
      /*                Branching EXECUTE                                      */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(uop_i.bits.instr === JAL) {
        uop_o_bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(19, 12), uop_i.bits.instr(20), uop_i.bits.instr(30, 21), 0.U(1.W)).asSInt
        execute_debug("JAL")
      } .elsewhen(uop_i.bits.instr === JALR) {
        uop_o_bits.alu_out    := execute_rs1_data.asSInt + uop_i.bits.instr(31, 20).asSInt
        execute_debug("JALR")
      } .elsewhen        (uop_i.bits.instr === BEQ) {
        uop_o_bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o_bits.branch_taken   := execute_rs1_data          === execute_rs2_data
        execute_debug("BEQ")
      } .elsewhen (uop_i.bits.instr === BNE) {
        uop_o_bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o_bits.branch_taken   := execute_rs1_data          =/= execute_rs2_data
        execute_debug("BNE")
      } .elsewhen (uop_i.bits.instr === BLT) {
        uop_o_bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o_bits.branch_taken   := execute_rs1_data.asSInt  <  execute_rs2_data.asSInt
        execute_debug("BLT")
      } .elsewhen (uop_i.bits.instr === BLTU) {
        uop_o_bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o_bits.branch_taken   := execute_rs1_data.asUInt  <  execute_rs2_data.asUInt
        execute_debug("BLTU")
      } .elsewhen (uop_i.bits.instr === BGE) {
        uop_o_bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o_bits.branch_taken   := execute_rs1_data.asSInt >=  execute_rs2_data.asSInt
        execute_debug("BGE")
      } .elsewhen (uop_i.bits.instr === BGEU) {
        uop_o_bits.alu_out    := uop_i.bits.pc.asSInt + Cat(uop_i.bits.instr(31), uop_i.bits.instr(7), uop_i.bits.instr(30, 25), uop_i.bits.instr(11, 8), 0.U(1.W)).asSInt
        uop_o_bits.branch_taken   := execute_rs1_data.asUInt >=  execute_rs2_data.asUInt
        execute_debug("BGEU")
      /**************************************************************************/
      /*                                                                        */
      /*                Memory EXECUTE                                         */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(uop_i.bits.instr === LOAD) {
        uop_o_bits.alu_out := execute_rs1_data.asSInt + uop_i.bits.instr(31, 20).asSInt
        execute_debug("LOAD")
      } .elsewhen(uop_i.bits.instr === STORE) {
        uop_o_bits.alu_out := execute_rs1_data.asSInt + Cat(uop_i.bits.instr(31, 25), uop_i.bits.instr(11, 7)).asSInt
        execute_debug("STORE")
      
      /**************************************************************************/
      /*                                                                        */
      /*                ALU EXECUTE                                            */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(uop_i.bits.instr === ADD) { // ALU instructions
        uop_o_bits.alu_out := execute_rs1_data.asSInt + execute_rs2_data.asSInt
        execute_debug("ADD")
      } .elsewhen(uop_i.bits.instr === ADDW) {
        uop_o_bits.alu_out := (execute_rs1_data(31,0).asSInt + execute_rs2_data(31,0).asSInt)
        execute_debug("ADDW")
      } .elsewhen(uop_i.bits.instr === SUB) {
        uop_o_bits.alu_out := execute_rs1_data.asSInt - execute_rs2_data.asSInt
        execute_debug("SUB")
      } .elsewhen(uop_i.bits.instr === SUBW) {
        uop_o_bits.alu_out := (execute_rs1_data(31,0).asSInt - execute_rs2_data(31,0).asSInt)
        execute_debug("SUBW")
      } .elsewhen(uop_i.bits.instr === AND) {
        uop_o_bits.alu_out := execute_rs1_data.asSInt & execute_rs2_data.asSInt
        execute_debug("AND")
      } .elsewhen(uop_i.bits.instr === OR) {
        uop_o_bits.alu_out := execute_rs1_data.asSInt | execute_rs2_data.asSInt
        execute_debug("OR")
      } .elsewhen(uop_i.bits.instr === XOR) {
        uop_o_bits.alu_out := execute_rs1_data.asSInt ^ execute_rs2_data.asSInt
        execute_debug("XOR")
      } .elsewhen(uop_i.bits.instr === SLL) {
        uop_o_bits.alu_out := (execute_rs1_data.asUInt << uop_i_rs2_shift_xlen).asSInt
        execute_debug("SLL")
      } .elsewhen(uop_i.bits.instr === SRL) {
        uop_o_bits.alu_out := (execute_rs1_data.asUInt >> uop_i_rs2_shift_xlen).asSInt
        execute_debug("SRL")
      } .elsewhen(uop_i.bits.instr === SRA) {
        uop_o_bits.alu_out := (execute_rs1_data.asSInt >> uop_i_rs2_shift_xlen).asSInt
        execute_debug("SRA")
      } .elsewhen(uop_i.bits.instr === SLLW) {
        uop_o_bits.alu_out := (execute_rs1_data(31,0).asUInt << uop_i_rs2_shift_xlen(4,0)).asSInt
        execute_debug("SLLW")
      } .elsewhen(uop_i.bits.instr === SRLW) {
        uop_o_bits.alu_out := (execute_rs1_data(31,0).asUInt >> uop_i_rs2_shift_xlen(4,0)).asSInt
        execute_debug("SRLW")
      } .elsewhen(uop_i.bits.instr === SRAW) {
        uop_o_bits.alu_out := (execute_rs1_data(31,0).asSInt >> uop_i_rs2_shift_xlen(4,0)).asSInt
        execute_debug("SRAW")
      } .elsewhen(uop_i.bits.instr === SLT) {
        uop_o_bits.alu_out := (execute_rs1_data.asSInt < execute_rs2_data.asSInt).asSInt
        execute_debug("SLT")
      } .elsewhen(uop_i.bits.instr === SLTU) {
        uop_o_bits.alu_out := (execute_rs1_data.asUInt < execute_rs2_data.asUInt).asSInt
        execute_debug("SLTU")
      } .elsewhen(uop_i.bits.instr === ADDI) {
        uop_o_bits.alu_out := execute_rs1_data.asSInt + uop_i_simm12
        execute_debug("ADDI")
      } .elsewhen(uop_i.bits.instr === ADDIW) {
        uop_o_bits.alu_out := (execute_rs1_data(31,0).asSInt + uop_i_simm12(31,0).asSInt)
        execute_debug("ADDIW")
      } .elsewhen(uop_i.bits.instr === SLTI) {
        uop_o_bits.alu_out := (execute_rs1_data.asSInt < uop_i_simm12).asSInt
        execute_debug("SLTI")
      } .elsewhen(uop_i.bits.instr === SLTIU) {
        uop_o_bits.alu_out := (execute_rs1_data.asUInt < uop_i_simm12.asUInt).asSInt
        execute_debug("SLTIU")
      } .elsewhen(uop_i.bits.instr === ANDI) {
        uop_o_bits.alu_out := (execute_rs1_data.asUInt & uop_i_simm12.asUInt).asSInt
        execute_debug("ANDI")
      } .elsewhen(uop_i.bits.instr === ORI) {
        uop_o_bits.alu_out := (execute_rs1_data.asUInt | uop_i_simm12.asUInt).asSInt
        execute_debug("ORI")
      } .elsewhen(uop_i.bits.instr === XORI) {
        uop_o_bits.alu_out := (execute_rs1_data.asUInt ^ uop_i_simm12.asUInt).asSInt
        execute_debug("XORI")
      } .elsewhen(uop_i.bits.instr === SLLI) {
        // SLLI/SRLI/SRAI is analogous in RV64/32 due to the fact the shamt[5] is set to zero
        uop_o_bits.alu_out := (execute_rs1_data.asUInt << uop_i_shamt_xlen).asSInt
        execute_debug("SLLI")
      } .elsewhen(uop_i.bits.instr === SRLI) {
        uop_o_bits.alu_out := (execute_rs1_data.asUInt >> uop_i_shamt_xlen).asSInt
        execute_debug("SRLI")
      } .elsewhen(uop_i.bits.instr === SRAI) {
        uop_o_bits.alu_out := (execute_rs1_data.asSInt >> uop_i_shamt_xlen).asSInt
        execute_debug("SRAI")
      } .elsewhen(uop_i.bits.instr === SLLIW) {
        // SLLIW: RV64 only, shift left logical, lower 5 bits of shamt, result sign-extended from 32 bits
        uop_o_bits.alu_out := (execute_rs1_data(31,0).asUInt << uop_i_shamt_xlen(4,0)).asSInt
        execute_debug("SLLIW")
      } .elsewhen(uop_i.bits.instr === SRLIW) {
        // SRLIW: RV64 only, shift right logical, lower 5 bits of shamt, result sign-extended from 32 bits
        uop_o_bits.alu_out := (execute_rs1_data(31,0).asUInt >> uop_i_shamt_xlen(4,0)).asSInt
        execute_debug("SRLIW")
      } .elsewhen(uop_i.bits.instr === SRAIW) {
        // SRAIW: RV64 only, shift right arithmetic, lower 5 bits of shamt, result sign-extended from 32 bits
        uop_o_bits.alu_out := (execute_rs1_data(31,0).asSInt >> uop_i_shamt_xlen(4,0)).asSInt
        execute_debug("SRAIW")
      /**************************************************************************/
      /*                                                                        */
      /*                Alu-like EXECUTE                                       */
      /*                                                                        */
      /**************************************************************************/
      } .otherwise {
        execute_debug("No-action for execute")
      }
      // TODO: MULDIV here


    } .otherwise { // Decode has no instruction. Or killed
      //log(cf"No instruction found or instruction killed")
      uop_o_valid := false.B
    }
  } .elsewhen(kill) {
    uop_o_valid := false.B
    log(cf"Instr killed")
  }
}