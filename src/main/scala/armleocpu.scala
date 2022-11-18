package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

object states extends ChiselEnum {
    val FETCH, DECODE, EXECUTE1, EXECUTE2, WRITEBACK_MEMORY = Value
}

object Instructions {
  def LUI                 = BitPat("b?????????????????????????0110111")
  def AUIPC               = BitPat("b?????????????????????????0010111")
  def JAL                 = BitPat("b?????????????????????????1101111")
  def JALR                = BitPat("b?????????????????000?????1100111")

  def BRANCH              = BitPat("b?????????????????????????1100011")
  def BEQ                 = BitPat("b?????????????????000?????1100011")
  def BNE                 = BitPat("b?????????????????001?????1100011")
  def BLT                 = BitPat("b?????????????????100?????1100011")
  def BGE                 = BitPat("b?????????????????101?????1100011")
  def BLTU                = BitPat("b?????????????????110?????1100011")
  def BGEU                = BitPat("b?????????????????111?????1100011")

  // MEMORY
  def LOAD                = BitPat("b?????????????????????????0000011")
  def LB                  = BitPat("b?????????????????000?????0000011")
  def LH                  = BitPat("b?????????????????001?????0000011")
  def LW                  = BitPat("b?????????????????010?????0000011")
//  def LD                  = BitPat("b?????????????????011?????0000011")
  def LBU                 = BitPat("b?????????????????100?????0000011")
  def LHU                 = BitPat("b?????????????????101?????0000011")
//  def LWU                 = BitPat("b?????????????????110?????0000011")

  def STORE               = BitPat("b?????????????????????????0100011")
  def SB                  = BitPat("b?????????????????000?????0100011")
  def SH                  = BitPat("b?????????????????001?????0100011")
  def SW                  = BitPat("b?????????????????010?????0100011")
//  def SD                  = BitPat("b?????????????????011?????0100011")

  // Arithmetic
  def ADD                 = BitPat("b0000000??????????000?????0110011")
//  def ADDW                = BitPat("b0000000??????????000?????0111011")
  def SUB                 = BitPat("b0100000??????????000?????0110011")
//  def SUBW                = BitPat("b0100000??????????000?????0111011")

  // logical
  def AND                 = BitPat("b0000000??????????111?????0110011")
  def OR                  = BitPat("b0000000??????????110?????0110011")
  def XOR                 = BitPat("b0000000??????????100?????0110011")

  // shift
  def SLL                 = BitPat("b0000000??????????001?????0110011")
  def SLLW                = BitPat("b0000000??????????001?????0111011")
  def SRA                 = BitPat("b0100000??????????101?????0110011")
  def SRAW                = BitPat("b0100000??????????101?????0111011")
  def SRL                 = BitPat("b0000000??????????101?????0110011")
  def SRLW                = BitPat("b0000000??????????101?????0111011")

  // SLT/SLTU
  def SLT                 = BitPat("b0000000??????????010?????0110011")
  def SLTU                = BitPat("b0000000??????????011?????0110011")

  // Arithmetic imm
  def ADDI                = BitPat("b?????????????????000?????0010011")
//  def ADDIW               = BitPat("b?????????????????000?????0011011")

  // logical imm
  def ANDI                = BitPat("b?????????????????111?????0010011")
  def ORI                 = BitPat("b?????????????????110?????0010011")
  def XORI                = BitPat("b?????????????????100?????0010011")

  // Shifts imm
  def SLLI                = BitPat("b000000???????????001?????0010011")
//  def SLLIW               = BitPat("b0000000??????????001?????0011011")
  def SRAI                = BitPat("b010000???????????101?????0010011")
//  def SRAIW               = BitPat("b0100000??????????101?????0011011")
  def SRLI                = BitPat("b000000???????????101?????0010011")
//  def SRLIW               = BitPat("b0000000??????????101?????0011011")

  // SLTI/SLTIU imm
  def SLTI                = BitPat("b?????????????????010?????0010011")
  def SLTIU               = BitPat("b?????????????????011?????0010011")

  /*
  // MULDIV
  def MUL                 = BitPat("b0000001??????????000?????0110011")
  def MULH                = BitPat("b0000001??????????001?????0110011")
  def MULHSU              = BitPat("b0000001??????????010?????0110011")
  def MULHU               = BitPat("b0000001??????????011?????0110011")

  def DIV                 = BitPat("b0000001??????????100?????0110011")
  def DIVU                = BitPat("b0000001??????????101?????0110011")
  def REM                 = BitPat("b0000001??????????110?????0110011")
  def REMU                = BitPat("b0000001??????????111?????0110011")

  def MULW                = BitPat("b0000001??????????000?????0111011")
  def REMUW               = BitPat("b0000001??????????111?????0111011")
  def REMW                = BitPat("b0000001??????????110?????0111011")
  def DIVUW               = BitPat("b0000001??????????101?????0111011")
  def DIVW                = BitPat("b0000001??????????100?????0111011")
  */

  def EBREAK              = BitPat("b00000000000100000000000001110011")
  def ECALL               = BitPat("b00000000000000000000000001110011")
  def MRET                = BitPat("b00110000001000000000000001110011")
  def SRET                = BitPat("b00010000001000000000000001110011")


  def FENCE               = BitPat("b?????????????????000?????0001111")
  def FENCE_I             = BitPat("b?????????????????001?????0001111")
  def SFENCE_VMA          = BitPat("b0001001??????????000000001110011")

  // ATOMIC

  //def LR_D                = BitPat("b00010??00000?????011?????0101111")
  def LR_W                = BitPat("b00010??00000?????010?????0101111")
  def SC_D                = BitPat("b00011????????????011?????0101111")
  //def SC_W                = BitPat("b00011????????????010?????0101111")

  // AMO*
  def AMOADD_W            = BitPat("b00000????????????010?????0101111")
  def AMOAND_W            = BitPat("b01100????????????010?????0101111")
  def AMOXOR_W            = BitPat("b00100????????????010?????0101111")
  def AMOOR_W             = BitPat("b01000????????????010?????0101111")
  def AMOMAX_W            = BitPat("b10100????????????010?????0101111")
  def AMOMAXU_W           = BitPat("b11100????????????010?????0101111")
  def AMOMIN_W            = BitPat("b10000????????????010?????0101111")
  def AMOMINU_W           = BitPat("b11000????????????010?????0101111")
  def AMOSWAP_W           = BitPat("b00001????????????010?????0101111")

  /*
  def AMOADD_D            = BitPat("b00000????????????011?????0101111")
  def AMOAND_D            = BitPat("b01100????????????011?????0101111")
  def AMOOR_D             = BitPat("b01000????????????011?????0101111")
  def AMOXOR_D            = BitPat("b00100????????????011?????0101111")
  def AMOMAX_D            = BitPat("b10100????????????011?????0101111")
  def AMOMAXU_D           = BitPat("b11100????????????011?????0101111")
  def AMOMIN_D            = BitPat("b10000????????????011?????0101111")
  def AMOMINU_D           = BitPat("b11000????????????011?????0101111")
  def AMOSWAP_D           = BitPat("b00001????????????011?????0101111")
  */

  // CSR
  def CSRRC               = BitPat("b?????????????????011?????1110011")
  def CSRRCI              = BitPat("b?????????????????111?????1110011")
  def CSRRS               = BitPat("b?????????????????010?????1110011")
  def CSRRSI              = BitPat("b?????????????????110?????1110011")
  def CSRRW               = BitPat("b?????????????????001?????1110011")
  def CSRRWI              = BitPat("b?????????????????101?????1110011")
}

import Instructions._
import armleocpu.utils._

class coreParams(
  val xLen: Int = 32,
  val iLen: Int = 32,

  val reset_vector:BigInt = BigInt("40000000", 16),

  val icache_ways: Int  = 2, // How many ways there are
  val icache_entries: Int = 32, // How many entries each way contains
  val icache_entry_bytes: Int = 64, // in bytes

  val dcache_ways: Int  = 2, // How many ways there are
  val dcache_entries: Int = 32, // How many entries each way contains
  val dcache_entry_bytes: Int = 64, // in bytes

  val itlb_entries: Int = 64,
  val itlb_ways: Int = 2,

  val dtlb_entries: Int = 64,
  val dtlb_ways: Int = 2,
  
  val bus_data_bytes: Int = 4,

  val ptw_verbose: Boolean = true,
  val pagetable_levels: Int = 2,
) {

  val apLen: Int = 34
  val avLen: Int = 32

  val pgoff_len: Int = 12

  val vtag_len = avLen - pgoff_len
  val ptag_len = apLen - pgoff_len

  val dcache_ptag_width = apLen - log2Up(dcache_entries * dcache_entry_bytes)
  val icache_ptag_width = apLen - log2Up(icache_entries * icache_entry_bytes)

  val xLen_log2 = log2Ceil(xLen)
  require(xLen == 32)
  require(iLen == 32)
  // TODO: In the future, replace with 64 version

  // Make sure it is power of two
  require( bus_data_bytes >= 1)
  require(isPowerOfTwo(bus_data_bytes))
  require( bus_data_bytes <= icache_entry_bytes * 2)

  require( bus_data_bytes >= xLen / 8)

  // bus_data_bytes used to be separate between Ibus and Dbus.
  // However, it would complicate PTW's bus connection and parametrization, so the idea was scrapped

  require((reset_vector & BigInt("11", 2)) == 0)

  def checkCacheTlbParam(p: Int) = {
      require(p >= 1)
      require(isPowerOfTwo(p))
  }
  checkCacheTlbParam(icache_ways)
  checkCacheTlbParam(icache_entries)
  checkCacheTlbParam(icache_entry_bytes)

  checkCacheTlbParam(itlb_entries)
  checkCacheTlbParam(itlb_ways)

  checkCacheTlbParam(dcache_ways)
  checkCacheTlbParam(dcache_entries)
  checkCacheTlbParam(dcache_entry_bytes)
  
  checkCacheTlbParam(dtlb_entries)
  checkCacheTlbParam(dtlb_ways)

  
  // If it gets bigger than 4096 bytes, then it goes out of page boundry
  // This means that TLB has to be resolved before cache request is sent
  require(icache_entries * icache_entry_bytes <= 4096)
  require(dcache_entries * dcache_entry_bytes <= 4096)

  require(pagetable_levels == 2)
  // TODO: RV64 extend to have 3/4 layers
}

class ArmleoCPU(val c: coreParams = new coreParams) extends Module {
  var xLen_log2 = c.xLen_log2

  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val ibus        = IO(new ibus_t(c))
  val dbus        = IO(new dbus_t(c))

  /**************************************************************************/
  /*                                                                        */
  /*                Submodules                                              */
  /*                                                                        */
  /**************************************************************************/

  val fetch   = Module(new Fetch(c))

  /*
  val dcache  = Module(new Cache(is_icache = false, c))
  val dtlb    = Module(new TLB(is_itlb = false, c))
  val dptw    = Module(new PTW(c))*/

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/

  val cu_pc             = RegInit(c.reset_vector.U(c.avLen.W))
  val RESET = 0.U(3.W)
  // val DEBUG_REQ = 1.U(3.W)
  val IDLE = 2.U(3.W)
  val NEW_PC = 3.U(3.W)
  //val INTERRUPT = 4.U(3.W)
  val EXCEPTION = 5.U(3.W)
  
  val cu_state          = RegInit(RESET)


  val atomic_lock       = RegInit(false.B)

  val dbus_ax_done            = RegInit(false.B)
  val dbus_w_done             = RegInit(false.B)
  val dbus_wait_for_response  = RegInit(false.B)
  

  // Registers

  val regs              = Mem(32, UInt(c.xLen.W))
  val regs_reservation  = Mem(32, Bool())


  // DECODE
  class decode_uop_t extends fetch_uop_t(c) {
    val rs1_data        = UInt(c.xLen.W)
    val rs2_data        = UInt(c.xLen.W)
    val branch_target   = UInt(c.xLen.W)
  }

  val decode_uop        = Reg(new decode_uop_t)
  val decode_uop_valid    = RegInit(false.B)
  
  // EXECUTE1
  class execute_uop_t extends decode_uop_t {
    // Using signed, so it will be sign extended
    val alu_out         = SInt(c.xLen.W)
    val pc_plus_4       = UInt(c.avLen.W)
    //val muldiv_out      = SInt(c.xLen.W)
    val branch_taken    = Bool()
  }
  
  val execute1_uop        = Reg(new execute_uop_t)
  val execute1_uop_valid  = RegInit(false.B)
  val execute2_uop        = Reg(new execute_uop_t)
  val execute2_uop_valid  = RegInit(false.B)

  
  
  
  /**************************************************************************/
  /*                                                                        */
  /*                COMBINATIONAL                                           */
  /*                                                                        */
  /**************************************************************************/
  val decode_uop_accept = Wire(Bool())

  dbus.aw.valid := false.B
  dbus.aw.addr  := execute2_uop.alu_out
  // TODO: Needs to depend on dbus_len
  dbus.aw.size  := "b010".U
  dbus.aw.len   := 0.U
  dbus.aw.lock  := false.B

  dbus.w.valid  := false.B
  dbus.w.data   := execute2_uop.rs2_data
  dbus.w.strb   := (-1.S(dbus.w.strb.getWidth.W)).asUInt() // Just pick any number, that is bigger than write strobe
  dbus.w.last   := true.B

  dbus.b.ready  := false.B

  dbus.ar.valid := false.B
  dbus.ar.addr  := execute2_uop.alu_out
  // TODO: Needs to depend on dbus_len
  dbus.ar.size  := "b010".U // TODO: This should be depending on value of c.xLen
  dbus.ar.len   := 0.U
  dbus.ar.lock  := false.B

  dbus.r.ready  := false.B
  
  
  val should_rd_reserve       = Wire(Bool())
  should_rd_reserve           := false.B

  val rd_write = Wire(Bool())
  val rd_wdata = Wire(UInt(c.xLen.W))
  val instruction_valid = Wire(Bool())
  instruction_valid := true.B

  rd_write := false.B
  rd_wdata := execute2_uop.alu_out.asUInt()


  // Ignore the below mumbo jumbo
  // It was the easiest way to get universal instructions without checking c.xLen for each
  val decode_uop_simm12 = Wire(SInt(c.xLen.W))
  decode_uop_simm12 := decode_uop.instr(31, 20).asSInt()

  
  val decode_uop_shamt_xlen = Wire(UInt(xLen_log2.W))
  val decode_uop_rs2_shift_xlen = Wire(UInt(xLen_log2.W))
  if(c.xLen == 32) {
    decode_uop_shamt_xlen := decode_uop.instr(24, 20)
    decode_uop_rs2_shift_xlen := decode_uop.rs2_data(4, 0)
  } else {
    decode_uop_shamt_xlen := decode_uop.instr(25, 20)
    decode_uop_rs2_shift_xlen := decode_uop.rs2_data(5, 0)
  }
  
  /**************************************************************************/
  /*                                                                        */
  /*                FETCH                                                   */
  /*                                                                        */
  /**************************************************************************/

  /**************************************************************************/
  /*                                                                        */
  /*                DECODE                                                  */
  /*                                                                        */
  /**************************************************************************/
  fetch.uop_accept := true.B

  when(fetch.uop_valid) {
    fetch.uop_accept := false.B
    // IF REGISTER not reserved, then move the Uop downs stage
    // ELSE stall

    // Only send the uop down the stage if no conflict with any of rs1/rs2/rd
    // Because if RD conflicts then when rd_reserve is reset,
    // in the future just increment it instead?
    // the pipeline will issue instructions with old register values
    val rs1_reserved  = (fetch.uop.instr(19, 15) =/= 0.U) && regs_reservation(fetch.uop.instr(19, 15))
    val rs2_reserved  = (fetch.uop.instr(24, 20) =/= 0.U) && regs_reservation(fetch.uop.instr(24, 20))
    val rd_reserved   = (fetch.uop.instr(11,  7) =/= 0.U) && regs_reservation(fetch.uop.instr(11,  7))

    val stall         = rs1_reserved || rs2_reserved || rd_reserved
    
    when (!stall && (decode_uop_valid && decode_uop_accept)) {
      // TODO: Dont unconditonally reserve the register
      when(fetch.uop.instr(11, 7) =/= 0.U) {
        regs_reservation(fetch.uop.instr(11, 7))          := true.B
      }
      decode_uop                                          := fetch.uop

      // STALL until reservation is reset
      decode_uop.rs1_data                                 := regs(fetch.uop.instr(19, 15))
      decode_uop.rs2_data                                 := regs(fetch.uop.instr(24, 20))
      
      fetch.uop_accept := true.B
    }
  }
  /**************************************************************************/
  /*                                                                        */
  /*                EXECUTE1                                                */
  /*                                                                        */
  /**************************************************************************/
  
  decode_uop_accept := true.B

  when(decode_uop_valid && execute1_uop_accept) {

    decode_uop_accept := false.B


    execute1_uop := decode_uop

    execute1_uop.alu_out      := 0.S(c.xLen.W)
    //execute1_uop.muldiv_out   := 0.S(c.xLen.W)

    execute1_uop.branch_taken := false.B

    when(decode_uop.instr === LUI) {
      // Use SInt to sign extend it before writing
      execute1_uop.alu_out := Cat(decode_uop.instr(31, 12), 0.U(12.W)).asSInt()
    }
    when(decode_uop.instr === AUIPC) {
      execute1_uop.alu_out := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31, 12), 0.U(12.W)).asSInt()
    }
    when(decode_uop.instr === JAL) {
      execute1_uop.alu_out := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(19, 12), decode_uop.instr(20), decode_uop.instr(30, 21), 0.U(1.W)).asSInt()
    }
    when(decode_uop.instr === JALR) {
      execute1_uop.alu_out := decode_uop.rs1_data.asSInt() + decode_uop.instr(31, 20).asSInt()
    }
    when(decode_uop.instr === BRANCH) {
      
      execute1_uop.alu_out := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()

      when        (decode_uop.instr === BEQ) {
        execute1_uop.branch_taken   := decode_uop.rs1_data          === decode_uop.rs2_data
      } .elsewhen (decode_uop.instr === BNE) {
        execute1_uop.branch_taken   := decode_uop.rs1_data          =/= decode_uop.rs2_data
      } .elsewhen (decode_uop.instr === BLT) {
        execute1_uop.branch_taken   := decode_uop.rs1_data.asSInt() <   decode_uop.rs2_data.asSInt()
      } .elsewhen (decode_uop.instr === BLTU) {
        execute1_uop.branch_taken   := decode_uop.rs1_data.asUInt() <   decode_uop.rs2_data.asUInt()
      } .elsewhen (decode_uop.instr === BGE) {
        execute1_uop.branch_taken   := decode_uop.rs1_data.asSInt() >=  decode_uop.rs2_data.asSInt()
      } .elsewhen (decode_uop.instr === BGEU) {
        execute1_uop.branch_taken   := decode_uop.rs1_data.asUInt() >=  decode_uop.rs2_data.asUInt()
      }
    }
    when(decode_uop.instr === LOAD) {
      execute1_uop.alu_out := decode_uop.rs1_data.asSInt() + decode_uop.instr(31, 20).asSInt()
    }
    when(decode_uop.instr === STORE) {
      execute1_uop.alu_out := decode_uop.rs1_data.asSInt() + Cat(decode_uop.instr(31, 25), decode_uop.instr(11, 7)).asSInt()
    }

    // ALU instructions
    when(decode_uop.instr === ADD) {
      execute1_uop.alu_out := decode_uop.rs1_data.asSInt() + decode_uop.rs2_data.asSInt()
    }
    when(decode_uop.instr === SUB) {
      execute1_uop.alu_out := decode_uop.rs1_data.asSInt() - decode_uop.rs2_data.asSInt()
    }
    when(decode_uop.instr === AND) {
      execute1_uop.alu_out := decode_uop.rs1_data.asSInt() & decode_uop.rs2_data.asSInt()
    }
    when(decode_uop.instr === OR) {
      execute1_uop.alu_out := decode_uop.rs1_data.asSInt() | decode_uop.rs2_data.asSInt()
    }
    when(decode_uop.instr === XOR) {
      execute1_uop.alu_out := decode_uop.rs1_data.asSInt() ^ decode_uop.rs2_data.asSInt()
    }
    // TODO: RV64 add SLL/SRL/SRA for 64 bit
    // Explaination of below
    // SLL and SLLW are equivalent (and others). But in RV64 you need to sign extends 32 bits
    when(decode_uop.instr === SLL) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asUInt() << decode_uop_rs2_shift_xlen)(31, 0).asSInt()
    }
    when(decode_uop.instr === SRL) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asUInt() >> decode_uop_rs2_shift_xlen)(31, 0).asSInt()
    }
    when(decode_uop.instr === SRA) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asSInt() >> decode_uop_rs2_shift_xlen)(31, 0).asSInt()
    }
    // TODO: RV64 Fix below
    when(decode_uop.instr === SLT) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asSInt() < decode_uop.rs2_data.asSInt()).asSInt()
    }
    when(decode_uop.instr === SLTU) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asUInt() < decode_uop.rs2_data.asUInt()).asSInt()
    }

    when(decode_uop.instr === ADDI) {
      execute1_uop.alu_out := decode_uop.rs1_data.asSInt() + decode_uop_simm12
    }
    when(decode_uop.instr === SLTI) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asSInt() < decode_uop_simm12).asSInt()
    }

    when(decode_uop.instr === SLTIU) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asUInt() < decode_uop_simm12.asUInt()).asSInt()
    }

    when(decode_uop.instr === ANDI) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asUInt() & decode_uop_simm12.asUInt()).asSInt()
    }
    when(decode_uop.instr === ORI) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asUInt() | decode_uop_simm12.asUInt()).asSInt()
    }
    when(decode_uop.instr === XORI) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asUInt() ^ decode_uop_simm12.asUInt()).asSInt()
    }
    when(decode_uop.instr === SLLI) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asUInt() << decode_uop_rs2_shift_xlen).asSInt()
    }
    when(decode_uop.instr === SRLI) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asUInt() >> decode_uop_rs2_shift_xlen).asSInt()
    }
    when(decode_uop.instr === SRAI) {
      execute1_uop.alu_out := (decode_uop.rs1_data.asSInt() >> decode_uop_rs2_shift_xlen).asSInt()
    }
    execute1_uop.pc_plus_4 := decode_uop.pc + 4.U
    // TODO: RV64 Add the 64 bit shortened 32 bit versions
    // TODO: RV64 add the 64 bit instruction tests
    // TODO: Rest of instructions here
    // TODO: MULDIV here

    /**************************************************************************/
    /*                                                                        */
    /*                EXECUTE2                                                */
    /*                                                                        */
    /**************************************************************************/
    when(execute2_uop_accept) {
      // Default invalidate execute2_uop if accepted
      execute2_uop_valid := false.B
    }

    when(execute1_uop_valid && execute2_uop_accept) {
      // But if accepted and valid instruction in execute1_uop
      //  Then send it to execute2 stage
      execute2_uop := execute1_uop
      execute2_uop_valid := true.B
    }
    

    /**************************************************************************/
    /*                                                                        */
    /*                WRITEBACK/MEMORY                                        */
    /*                                                                        */
    /**************************************************************************/
    // Accept the execute2 uop by default
    // However if execute2_uop_valid is set then the below lines will work
    execute2_uop_accept := true.B

    when(execute2_uop_valid) {
      execute2_uop_accept := false.B
      // TODO: PIPELINE when(writeback_active || !kill) {

      when(
        (execute2_uop.instr === LUI) ||
        (execute2_uop.instr === AUIPC) ||

        (execute2_uop.instr === ADD) ||
        (execute2_uop.instr === SUB) ||
        (execute2_uop.instr === AND) ||
        (execute2_uop.instr === OR)  ||
        (execute2_uop.instr === XOR) ||
        (execute2_uop.instr === SLL) ||
        (execute2_uop.instr === SRL) ||
        (execute2_uop.instr === SRA) ||
        (execute2_uop.instr === SLT) ||
        (execute2_uop.instr === SLTU) ||

        (execute2_uop.instr === ADDI) ||
        (execute2_uop.instr === SLTI) ||
        (execute2_uop.instr === SLTIU) ||
        (execute2_uop.instr === ANDI) ||
        (execute2_uop.instr === ORI) ||
        (execute2_uop.instr === XORI) ||
        (execute2_uop.instr === SLLI) ||
        (execute2_uop.instr === SRLI) ||
        (execute2_uop.instr === SRAI)
        // TODO: Add the rest of ALU out write back 
      ) {
        rd_wdata := execute2_uop.alu_out.asUInt()
        rd_write := true.B
        regs_reservation.write(fetch.uop.instr(11, 7), false.B)
        instruction_valid := true.B
        execute2_uop_accept := true.B
      }

      when(
          (execute2_uop.instr === JAL) ||
          (execute2_uop.instr === JALR)
      ) {
        rd_wdata := execute2_uop.pc_plus_4
        rd_write := true.B

        when(execute2_uop.instr === JALR) {
          pc := execute2_uop.alu_out.asUInt() & (~(1.U(c.avLen.W)))
        }
        // Reset PC to zero
        // TODO: C-ext change to (0) := 0.U
        // TODO: Add a check for PC to be aligned to 4 bytes or error out
        instruction_valid := true.B
        execute2_uop_accept := true.B
      }

      when (
        (execute2_uop.instr === BEQ) || 
        (execute2_uop.instr === BNE) || 
        (execute2_uop.instr === BLT) || 
        (execute2_uop.instr === BLTU) || 
        (execute2_uop.instr === BGE) || 
        (execute2_uop.instr === BGEU)
      ) {
        instruction_valid := true.B
        when(execute2_uop.branch_taken) {
          // TODO: Send request to Control unit to restart execution from branch target
          pc := execute2_uop.alu_out.asUInt()
        }
      }

      when (execute2_uop.instr === LW) {
        instruction_valid := true.B

        dbus.ar.valid := !dbus_wait_for_response
        when(dbus.ar.ready) {
          dbus_wait_for_response := true.B
        }
        when (dbus.r.valid && dbus_wait_for_response) {
          rd_write := true.B
          rd_wdata := dbus.r.data
          dbus_wait_for_response := false.B
        }
      }

      when (execute2_uop.instr === SW) {
        instruction_valid := true.B

        dbus.aw.valid := !dbus_ax_done
        when(dbus.aw.ready) {
          dbus_ax_done := true.B
        }
        
        dbus.w.valid := !dbus_w_done
        when(dbus.w.ready) {
          dbus_w_done := true.B
        }

        when((dbus.aw.ready || dbus_ax_done) && (dbus.w.ready || dbus_w_done)) {
          dbus_ax_done := false.B
          dbus_w_done := false.B
          dbus_wait_for_response := true.B
        }

        when(dbus_wait_for_response && dbus.b.valid) {
          dbus_wait_for_response := false.B
          // Write complete
          // TODO: Release the writeback stage and complete the instruction
          // TODO: Error handling
        }
      }
      // TODO: Add the Load/Store
      // TODO: CACHE Add the cache refill
      // TODO: The request to control to change the PC for branching instructions
      // TODO: Tell the control unit what the current PC is
      // TODO: If active interrupt then control unit will start killing instructions,
      //    so we dont need to do anything else

      // TODO: If reset then write zero to zeroth register
      // TODO: Then you can remove the weird mux
      // TODO: Retired instruction should send data to CU (control unit)
      // TOOD: Send the latest retired PC to Control Unit
      
      // TODO: Dont issue atomics store if there is no active lock

      // TODO: Ignore JAL/JALR LSB bit

      when(rd_write) {
        regs.write(execute2_uop.instr(11,  7), rd_wdata)
        regs_reservation.write    (fetch_uop.instr(11, 7),  false.B)
      }

      // TODO: The PC + 4 or the branch target needs to be commited to Control unit
    }
  }
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object ArmleoCPUGenerator extends App {
  (new ChiselStage).execute(Array("-frsq", "-o:memory_configs", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new ArmleoCPU)))
}


