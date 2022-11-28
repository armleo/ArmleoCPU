package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

import io.AnsiColor._

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
  //def SC_D                = BitPat("b00011????????????011?????0101111")
  def SC_W                = BitPat("b00011????????????010?????0101111")

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
  def CSRRW               = BitPat("b?????????????????001?????1110011")
  def CSRRWI              = BitPat("b?????????????????101?????1110011")
  def CSRRS               = BitPat("b?????????????????010?????1110011")
  def CSRRSI              = BitPat("b?????????????????110?????1110011")
  def CSRRC               = BitPat("b?????????????????011?????1110011")
  def CSRRCI              = BitPat("b?????????????????111?????1110011")


}

import Instructions._
import armleocpu.utils._

class coreParams(
  // Primary core parameters
  val xLen: Int = 32,
  val iLen: Int = 32,

  // CSR and reset
  val reset_vector:BigInt = BigInt("40000000", 16),
  val mtvec_default: BigInt = BigInt("40002000", 16),
  val stvec_default: BigInt = BigInt("40004000", 16),

  val mvendorid: BigInt = BigInt("0A1AA1E0", 16),
  val marchid: BigInt = BigInt(1),
  val mimpid: BigInt = BigInt(1),
  val mhartid: BigInt = BigInt(0),
  val mconfigptr: BigInt = BigInt("100", 16),
  

  // Memory subsystem
  val pagetable_levels: Int = 2,

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
  
  // PMA/PMP config
  val pma_config_default: Seq[pma_config_default_t] = Seq(
    new pma_config_default_t(
      BigInt(0) << 33,
      BigInt(1) << 33,
      true
    ), new pma_config_default_t(
      BigInt(1) << 33,
      (BigInt(1) << 34) - 1,
      false
    )
  ),
  /*
  val pmpcfg_default: Seq[Int],
  val pmpaddr_default: Seq[Int]*/

  // Debug options
  val verboseCycleWidth:Int = 16,
  val ptw_verbose: Boolean = true,
  val core_verbose: Boolean = true,
  val fetch_verbose: Boolean = true,
  val itlb_verbose: Boolean = true,
  val dtlb_verbose: Boolean = true,
  // TODO: Add verbose options for the rest of modules

  val rvfi_enabled: Boolean = false,
  val rvfi_dont_touch: Boolean = true
) {
  println("Generating using PMA Configuration default:")
  var regionnum = 0
  for(m <- pma_config_default) {
    println(f"Region $regionnum start: 0x${m.addr_low.toString(16)}, end: 0x${m.addr_high.toString(16)}, cacheable: ${m.cacheable}")
    regionnum += 1
  }
  
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
  // TODO: RV64 In the future, replace with 64 version

  // Make sure it is power of two
  require( bus_data_bytes >= 1)
  require(isPowerOfTwo(bus_data_bytes))
  require( bus_data_bytes <= icache_entry_bytes * 2)
  require( bus_data_bytes <= dcache_entry_bytes * 2)

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

  def getCoreName(): String = {
    return f"core${mhartid}"
  }
}

class Logger(coreName: String, moduleName: String, enabled: Boolean, counter: UInt) {
  def apply(fmt: String, data: Bits*):Unit = {
    if(enabled) {
      val newdata = counter +: data
      
      printf(f"[c:%%d $coreName $moduleName] ${fmt}${RESET}\n", newdata:_*)
    }
  }
}

class rvfi_o(c: coreParams) extends Bundle {
  val valid = Bool()
  val order = UInt(64.W)
  val insn  = UInt(c.iLen.W)
  val trap  = Bool()
  val halt  = Bool()
  val intr  = Bool()
  val mode  = UInt(2.W) // Privilege mode
  val ixl   = UInt(2.W)

  // Register
  val rs1_addr  = UInt(5.W)
  val rs2_addr  = UInt(5.W)
  val rs1_rdata = UInt(c.xLen.W)
  val rs2_rdata = UInt(c.xLen.W)
  val rd_addr   = UInt(5.W)
  val rd_wdata  = UInt(c.xLen.W)

  // PC
  val pc_rdata  = UInt(c.xLen.W)
  val pc_wdata  = UInt(c.xLen.W)

  // MEM
  val mem_addr  = UInt(c.xLen.W)
  val mem_rmask = UInt((c.xLen / 8).W)
  val mem_wmask = UInt((c.xLen / 8).W)
  val mem_rdata = UInt(c.xLen.W)
  val mem_wdata = UInt(c.xLen.W)

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
  val int         = IO(Input(new InterruptsInputs))
  val rvfi        = if(c.rvfi_enabled) IO(Output(new rvfi_o(c))) else Wire(new rvfi_o(c))

  if(!c.rvfi_enabled && c.rvfi_dont_touch) {
    dontTouch(rvfi) // It should be optimized away
  }


  val cycle = RegInit(0.U(c.verboseCycleWidth.W))
  cycle := cycle + 1.U

  val dlog = new Logger(c.getCoreName(), f"${YELLOW}decod", c.core_verbose, cycle)
  val e1log = new Logger(c.getCoreName(), f"${GREEN}exec1", c.core_verbose, cycle)
  val e2log = new Logger(c.getCoreName(), f"${BLUE}exec2", c.core_verbose, cycle)
  val memwblog = new Logger(c.getCoreName(), f"${RED}memwb", c.core_verbose, cycle)

  /**************************************************************************/
  /*                                                                        */
  /*                Submodules                                              */
  /*                                                                        */
  /**************************************************************************/

  val fetch   = Module(new Fetch(c))

  fetch.ibus <> ibus
  fetch.cycle := cycle

  // TODO: Add Instruction PTE storage for RVFI
  
  val csr = Module(new CSR(c))
  val cu  = Module(new ControlUnit(c))

  /*
  val dcache  = Module(new Cache(is_icache = false, c))
  val dtlb    = Module(new TLB(is_itlb = false, c))
  val dptw    = Module(new PTW(is_iptw = false, c))
  
  // TODO: Add PTE storage for RVFI
  
  */

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/


  val atomic_lock       = RegInit(false.B)

  val dbus_ax_done            = RegInit(false.B)
  val dbus_w_done             = RegInit(false.B)
  val dbus_wait_for_response  = RegInit(false.B)
  

  // Registers

  val regs              = Mem(32, UInt(c.xLen.W))
  val regs_reservation  = RegInit(VecInit.tabulate(32) {f:Int => false.B})

  

  // DECODE
  class decode_uop_t extends fetch_uop_t(c) {
    val rs1_data        = UInt(c.xLen.W)
    val rs2_data        = UInt(c.xLen.W)
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
  val execute1_uop_accept = Wire(Bool())
  val execute2_uop_accept = Wire(Bool())

  dbus.aw.valid := false.B
  dbus.aw.addr  := execute2_uop.alu_out
  // FIXME: Needs to depend on dbus_len
  dbus.aw.size  := "b010".U
  dbus.aw.len   := 0.U
  dbus.aw.lock  := false.B

  dbus.w.valid  := false.B
  dbus.w.data   := execute2_uop.rs2_data
  dbus.w.strb   := (-1.S(dbus.w.strb.getWidth.W)).asUInt() // Just pick any number, that is bigger than write strobe
  // FIXME: Strobe needs proper value
  dbus.w.last   := true.B

  dbus.b.ready  := false.B

  dbus.ar.valid := false.B
  dbus.ar.addr  := execute2_uop.alu_out
  // FIXME: Needs to depend on dbus_len
  dbus.ar.size  := "b010".U // FIXME: This should be depending on value of c.xLen
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

  // The regfile has unknown register state for address 0
  // This is by-design
  // So instead we MUX zero at execute1 stage if its read from 0th register

  val execute1_rs1_data = Mux(decode_uop.instr(19, 15) =/= 0.U, decode_uop.rs1_data, 0.U)
  val execute1_rs2_data = Mux(decode_uop.instr(24, 20) =/= 0.U, decode_uop.rs2_data, 0.U)
  
  val decode_uop_shamt_xlen = Wire(UInt(xLen_log2.W))
  val decode_uop_rs2_shift_xlen = Wire(UInt(xLen_log2.W))
  if(c.xLen == 32) {
    decode_uop_shamt_xlen := decode_uop.instr(24, 20)
    decode_uop_rs2_shift_xlen := execute1_rs2_data(4, 0)
  } else {
    decode_uop_shamt_xlen := decode_uop.instr(25, 20)
    decode_uop_rs2_shift_xlen := execute1_rs2_data(5, 0)
  }
  


  csr.int <> int
  csr.instret_incr := false.B
  csr.addr := execute2_uop.instr(31, 20)
  csr.cause := 0.U
  csr.cmd := csr_cmd.none
  csr.epc := execute2_uop.pc
  csr.in := 0.U // FIXME: Needs to be properly connected

  cu.cmd := controlunit_cmd.none
  cu.pc_in := execute2_uop.pc_plus_4
  cu.decode_to_cu_ready := !decode_uop_valid
  cu.execute1_to_cu_ready := !execute1_uop_valid
  cu.execute2_to_cu_ready := !execute2_uop_valid
  cu.fetch_ready := !fetch.busy


  
  /**************************************************************************/
  /*                                                                        */
  /*                DECODE                                                  */
  /*                                                                        */
  /**************************************************************************/
  fetch.uop_accept    := false.B
  fetch.cmd           := cu.cu_to_fetch_cmd
  fetch.mem_priv      := csr.mem_priv_o
  fetch.new_pc        := cu.pc_out

  /**************************************************************************/
  /*                                                                        */
  /*                RVFI                                                    */
  /*                                                                        */
  /**************************************************************************/

  rvfi.valid := false.B
  rvfi.halt := false.B
  rvfi.ixl  := Mux(c.xLen.U === 32.U, 1.U, 2.U)
  rvfi.mode := csr.mem_priv_o.privilege

  rvfi.trap := false.B // FIXME: rvfi.trap
  rvfi.halt := false.B // FIXME: rvfi.halt
  rvfi.intr := false.B // FIXME: rvfi.intr
  
  val order = RegInit(0.U(64.W))
  rvfi.order := order
  when(rvfi.valid) {
    order := order + 1.U
  }
  
  rvfi.insn := execute2_uop.instr

  rvfi.rs1_addr := execute2_uop.instr(19, 15)
  rvfi.rs1_rdata := execute2_uop.rs1_data
  rvfi.rs2_rdata := execute2_uop.rs2_data
  rvfi.rs2_addr := execute2_uop.instr(24, 20)
  rvfi.rd_addr  := execute2_uop.instr(11, 7)
  rvfi.rd_wdata := Mux(execute2_uop.instr(11, 7) === 0.U, 0.U, rd_wdata)


  rvfi.pc_rdata := execute2_uop.pc
  rvfi.pc_wdata := cu.pc_in

  rvfi.mem_addr := execute2_uop.alu_out.asUInt
  rvfi.mem_rmask := 0.U // FIXME: rvfi.mem_rmask
  rvfi.mem_wmask := 0.U // FIXME: rvfi.mem_wmask
  rvfi.mem_rdata := rd_wdata // FIXME: rvfi.mem_rdata
  rvfi.mem_wdata := 0.U  // FIXME: rvfi.mem_wdata

  
  when((!decode_uop_valid) || (decode_uop_valid && decode_uop_accept)) {
    when(fetch.uop_valid && !cu.kill) {
      
      
      // IF REGISTER not reserved, then move the Uop downs stage
      // ELSE stall

      // Only send the uop down the stage if no conflict with any of rs1/rs2/rd
      // otherwise the pipeline will issue instructions with old register values
      
      // Also RD is checked so that the register is not overwritten

      val rs1_reserved  = (fetch.uop.instr(19, 15) =/= 0.U) && regs_reservation(fetch.uop.instr(19, 15))
      val rs2_reserved  = (fetch.uop.instr(24, 20) =/= 0.U) && regs_reservation(fetch.uop.instr(24, 20))
      val rd_reserved   = (fetch.uop.instr(11,  7) =/= 0.U) && regs_reservation(fetch.uop.instr(11,  7))

      val stall         = rs1_reserved || rs2_reserved || rd_reserved
      
      when (!stall) {
        // TODO: Dont unconditonally reserve the register
        when(fetch.uop.instr(11, 7) =/= 0.U) {
          regs_reservation(fetch.uop.instr(11, 7)) := true.B
        }
        decode_uop.viewAsSupertype(new fetch_uop_t(c))  := fetch.uop

        // STALL until reservation is reset
        decode_uop.rs1_data                             := regs(fetch.uop.instr(19, 15))
        decode_uop.rs2_data                             := regs(fetch.uop.instr(24, 20))
        
        fetch.uop_accept                                := true.B
        decode_uop_valid                                := true.B
        dlog("Instruction passed to next stage instr=0x%x, pc=0x%x", fetch.uop.instr, fetch.uop.pc)
      } .otherwise {
        dlog("Instruction stalled because of reservation instr=0x%x, pc=0x%x", fetch.uop.instr, fetch.uop.pc)
        decode_uop_valid := false.B
      }
    } .otherwise {
      dlog("Idle")
      decode_uop_valid := false.B
      when(cu.kill) {
        fetch.uop_accept := true.B
      }
    }
  } .elsewhen(cu.kill) {
    fetch.uop_accept := true.B
    decode_uop_valid := false.B
    dlog("Instr killed")
  } .otherwise {
    decode_uop_valid := false.B
  }
  /**************************************************************************/
  /*                                                                        */
  /*                EXECUTE1                                                */
  /*                                                                        */
  /**************************************************************************/
  
  
  decode_uop_accept := false.B
  def execute1_debug(instr: String): Unit = {
    e1log(f"$instr instr=0x%%x, pc=0x%%x", decode_uop.instr, decode_uop.pc)
  }
  when(!execute1_uop_valid || (execute1_uop_valid && execute1_uop_accept)) {
    when(decode_uop_valid && !cu.kill) {
      decode_uop_accept := true.B

      execute1_uop.viewAsSupertype(chiselTypeOf(decode_uop)) := decode_uop
      execute1_uop_valid        := true.B

      execute1_uop.alu_out      := 0.S(c.xLen.W)
      //execute1_uop.muldiv_out   := 0.S(c.xLen.W)

      execute1_uop.branch_taken := false.B

      when(decode_uop.instr === LUI) {
        // Use SInt to sign extend it before writing
        execute1_uop.alu_out    := Cat(decode_uop.instr(31, 12), 0.U(12.W)).asSInt()
        
      } .elsewhen(decode_uop.instr === AUIPC) {
        execute1_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31, 12), 0.U(12.W)).asSInt()
        execute1_debug("AUIPC")
      } .elsewhen(decode_uop.instr === JAL) {
        execute1_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(19, 12), decode_uop.instr(20), decode_uop.instr(30, 21), 0.U(1.W)).asSInt()
        execute1_debug("JAL")
      } .elsewhen(decode_uop.instr === JALR) {
        execute1_uop.alu_out    := execute1_rs1_data.asSInt() + decode_uop.instr(31, 20).asSInt()
        execute1_debug("JALR")
      } .elsewhen        (decode_uop.instr === BEQ) {
        execute1_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute1_uop.branch_taken   := execute1_rs1_data          === execute1_rs2_data
        execute1_debug("BEQ")
      } .elsewhen (decode_uop.instr === BNE) {
        execute1_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute1_uop.branch_taken   := execute1_rs1_data          =/= execute1_rs2_data
        execute1_debug("BNE")
      } .elsewhen (decode_uop.instr === BLT) {
        execute1_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute1_uop.branch_taken   := execute1_rs1_data.asSInt()  <  execute1_rs2_data.asSInt()
        execute1_debug("BLT")
      } .elsewhen (decode_uop.instr === BLTU) {
        execute1_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute1_uop.branch_taken   := execute1_rs1_data.asUInt()  <  execute1_rs2_data.asUInt()
        execute1_debug("BLTU")
      } .elsewhen (decode_uop.instr === BGE) {
        execute1_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute1_uop.branch_taken   := execute1_rs1_data.asSInt() >=  execute1_rs2_data.asSInt()
        execute1_debug("BGE")
      } .elsewhen (decode_uop.instr === BGEU) {
        execute1_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()
        execute1_uop.branch_taken   := execute1_rs1_data.asUInt() >=  execute1_rs2_data.asUInt()
        execute1_debug("BGEU")
      } .elsewhen(decode_uop.instr === LOAD) {
        execute1_uop.alu_out := execute1_rs1_data.asSInt() + decode_uop.instr(31, 20).asSInt()
        execute1_debug("LOAD")
      } .elsewhen(decode_uop.instr === STORE) {
        execute1_uop.alu_out := execute1_rs1_data.asSInt() + Cat(decode_uop.instr(31, 25), decode_uop.instr(11, 7)).asSInt()
        execute1_debug("STORE")
      } .elsewhen(decode_uop.instr === ADD) { // ALU instructions
        execute1_uop.alu_out := execute1_rs1_data.asSInt() + execute1_rs2_data.asSInt()
        execute1_debug("ADD")
      } .elsewhen(decode_uop.instr === SUB) {
        execute1_uop.alu_out := execute1_rs1_data.asSInt() - execute1_rs2_data.asSInt()
        execute1_debug("SUB")
      } .elsewhen(decode_uop.instr === AND) {
        execute1_uop.alu_out := execute1_rs1_data.asSInt() & execute1_rs2_data.asSInt()
        execute1_debug("AND")
      } .elsewhen(decode_uop.instr === OR) {
        execute1_uop.alu_out := execute1_rs1_data.asSInt() | execute1_rs2_data.asSInt()
        execute1_debug("OR")
      } .elsewhen(decode_uop.instr === XOR) {
        execute1_uop.alu_out := execute1_rs1_data.asSInt() ^ execute1_rs2_data.asSInt()
        execute1_debug("XOR")
      } .elsewhen(decode_uop.instr === SLL) {
        // TODO: RV64 add SLL/SRL/SRA for 64 bit
        // Explaination of below
        // SLL and SLLW are equivalent (and others). But in RV64 you need to sign extends 32 bits
        execute1_uop.alu_out := (execute1_rs1_data.asUInt() << decode_uop_rs2_shift_xlen)(31, 0).asSInt()
        execute1_debug("SLL")
      } .elsewhen(decode_uop.instr === SRL) {
        execute1_uop.alu_out := (execute1_rs1_data.asUInt() >> decode_uop_rs2_shift_xlen)(31, 0).asSInt()
        execute1_debug("SRL")
      } .elsewhen(decode_uop.instr === SRA) {
        execute1_uop.alu_out := (execute1_rs1_data.asSInt() >> decode_uop_rs2_shift_xlen)(31, 0).asSInt()
        execute1_debug("SRA")
      } .elsewhen(decode_uop.instr === SLT) {
        // TODO: RV64 Fix below
        execute1_uop.alu_out := (execute1_rs1_data.asSInt() < execute1_rs2_data.asSInt()).asSInt()
        execute1_debug("SLT")
      } .elsewhen(decode_uop.instr === SLTU) {
        execute1_uop.alu_out := (execute1_rs1_data.asUInt() < execute1_rs2_data.asUInt()).asSInt()
        execute1_debug("SLTU")
      } .elsewhen(decode_uop.instr === ADDI) {
        execute1_uop.alu_out := execute1_rs1_data.asSInt() + decode_uop_simm12
        execute1_debug("ADDI")
      } .elsewhen(decode_uop.instr === SLTI) {
        execute1_uop.alu_out := (execute1_rs1_data.asSInt() < decode_uop_simm12).asSInt()
        execute1_debug("SLTI")
      } .elsewhen(decode_uop.instr === SLTIU) {
        execute1_uop.alu_out := (execute1_rs1_data.asUInt() < decode_uop_simm12.asUInt()).asSInt()
        execute1_debug("SLTIU")
      } .elsewhen(decode_uop.instr === ANDI) {
        execute1_uop.alu_out := (execute1_rs1_data.asUInt() & decode_uop_simm12.asUInt()).asSInt()
        execute1_debug("ANDI")
      } .elsewhen(decode_uop.instr === ORI) {
        execute1_uop.alu_out := (execute1_rs1_data.asUInt() | decode_uop_simm12.asUInt()).asSInt()
        execute1_debug("ORI")
      } .elsewhen(decode_uop.instr === XORI) {
        execute1_uop.alu_out := (execute1_rs1_data.asUInt() ^ decode_uop_simm12.asUInt()).asSInt()
        execute1_debug("XORI")
      } .elsewhen(decode_uop.instr === SLLI) {
        execute1_uop.alu_out := (execute1_rs1_data.asUInt() << decode_uop_rs2_shift_xlen).asSInt()
        execute1_debug("SLLI")
      } .elsewhen(decode_uop.instr === SRLI) {
        execute1_uop.alu_out := (execute1_rs1_data.asUInt() >> decode_uop_rs2_shift_xlen).asSInt()
        execute1_debug("SRLI")
      } .elsewhen(decode_uop.instr === SRAI) {
        execute1_uop.alu_out := (execute1_rs1_data.asSInt() >> decode_uop_rs2_shift_xlen).asSInt()
        execute1_debug("SRAI")
      } .elsewhen(decode_uop.instr === CSRRW) {
        execute1_debug("CSRRW")
      } .elsewhen(decode_uop.instr === CSRRWI) {
        execute1_debug("CSRRWI")
      } .elsewhen(decode_uop.instr === CSRRS) {
        execute1_debug("CSRRS")
      } .elsewhen(decode_uop.instr === CSRRSI) {
        execute1_debug("CSRRSI")
      } .elsewhen(decode_uop.instr === CSRRC) {
        execute1_debug("CSRRC")
      } .elsewhen(decode_uop.instr === CSRRCI) {
        execute1_debug("CSRRCI")
      } .otherwise {
        // TODO: Add instructions that are known but do not have data to be calculated in ALU stage
        execute1_debug("UNKOWN")
      }
      execute1_uop.pc_plus_4 := decode_uop.pc + 4.U
      // TODO: RV64 Add the 64 bit shortened 32 bit versions
      // TODO: RV64 add the 64 bit instruction tests
      // TODO: Rest of instructions here
      // TODO: CSR
      // TODO: Flush
      // TODO: MULDIV here
    } .otherwise { // Decode has no instruction.
      e1log("No instruction found")
      execute1_uop_valid := false.B
    }
  } .elsewhen(cu.kill) {
    execute1_uop_valid := false.B
    e1log("Instr killed")
  }
  /**************************************************************************/
  /*                                                                        */
  /*                EXECUTE2                                                */
  /*                                                                        */
  /**************************************************************************/
  execute1_uop_accept := false.B

  
  when(!execute2_uop_valid || (execute2_uop_valid && execute2_uop_accept)) {
    when(execute1_uop_valid && !cu.kill) {
      execute2_uop := execute1_uop
      execute2_uop_valid := true.B
      execute1_uop_accept := true.B
      e2log("instr=0x%x, pc=0x%x", execute1_uop.instr, execute1_uop.pc)
    } .otherwise {
      execute2_uop_valid := false.B
      e2log("No instruction found")
    }
  } .elsewhen(cu.kill) {
    execute2_uop_valid := false.B
    e2log("Instr killed")
  }
  /**************************************************************************/
  /*                                                                        */
  /*                WRITEBACK/MEMORY                                        */
  /*                                                                        */
  /**************************************************************************/
  // Accept the execute2 uop by default
  // However if execute2_uop_valid is set then the below lines will work
  execute2_uop_accept := false.B

  

  def instr_cplt(br_pc_valid: Bool = false.B, br_pc: UInt = execute2_uop.pc_plus_4): Unit = {
    instruction_valid := true.B
    execute2_uop_accept := true.B
    rvfi.valid := true.B
    
    when(br_pc_valid) {
      cu.pc_in := br_pc
      cu.cmd := controlunit_cmd.branch
      regs_reservation := 0.U.asTypeOf(chiselTypeOf(regs_reservation))
    } .otherwise {
      cu.cmd := controlunit_cmd.retire
      cu.pc_in := execute2_uop.pc_plus_4
    }

    regs_reservation(execute2_uop.instr(11, 7)) := false.B
  }


  // FIXME: Add the writeback reset to flush dcache and dtlb

  when(execute2_uop_valid && !cu.wb_kill) {
    execute2_uop_accept := false.B

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
      memwblog("ALU-like instruction found instr=0x%x, pc=0x%x", execute2_uop.instr, execute2_uop.pc)
      
      

      rd_wdata := execute2_uop.alu_out.asUInt()
      rd_write := true.B
      instr_cplt()
    } .elsewhen(
        (execute2_uop.instr === JAL) ||
        (execute2_uop.instr === JALR)
    ) {
      rd_wdata := execute2_uop.pc_plus_4
      rd_write := true.B

      when(execute2_uop.instr === JALR) {
        val next_cu_pc = execute2_uop.alu_out.asUInt() & (~(1.U(c.avLen.W)))
        instr_cplt(true.B, next_cu_pc)
        memwblog("JALR instr=0x%x, pc=0x%x, rd_wdata=0x%x, target=0x%x", execute2_uop.instr, execute2_uop.pc, rd_wdata, next_cu_pc)
      } .otherwise {
        instr_cplt(true.B, execute2_uop.alu_out.asUInt())
        memwblog("JAL instr=0x%x, pc=0x%x, rd_wdata=0x%x, target=0x%x", execute2_uop.instr, execute2_uop.pc, rd_wdata, execute2_uop.alu_out.asUInt())
      }
      
      // Reset PC to zero
      // TODO: C-ext change to (0) := 0.U
      // TODO: Add a check for PC to be aligned to 4 bytes or error out
      instruction_valid := true.B
      execute2_uop_accept := true.B

      
    } .elsewhen (
      (execute2_uop.instr === BEQ) || 
      (execute2_uop.instr === BNE) || 
      (execute2_uop.instr === BLT) || 
      (execute2_uop.instr === BLTU) || 
      (execute2_uop.instr === BGE) || 
      (execute2_uop.instr === BGEU)
    ) {
      instruction_valid := true.B
      when(execute2_uop.branch_taken) {
        // TODO: New variant of branching. Always take the branch backwards in decode stage. And if mispredicted in writeback stage branch towards corrected path
        execute2_uop_accept := true.B
        instruction_valid := true.B
        instr_cplt(true.B, execute2_uop.alu_out.asUInt)
        memwblog("BranchTaken instr=0x%x, pc=0x%x, target=0x%x", execute2_uop.instr, execute2_uop.pc, execute2_uop.alu_out.asUInt())
      } .otherwise {
        instr_cplt()
        memwblog("BranchNotTaken instr=0x%x, pc=0x%x", execute2_uop.instr, execute2_uop.pc)
        execute2_uop_accept := true.B
      }
      // TODO: IMPORTANT! Branch needs to check for misaligment in this stage
    } .elsewhen (execute2_uop.instr === LW) {
      // TODO: Load
      instruction_valid := true.B

      dbus.ar.valid := !dbus_wait_for_response
      when(dbus.ar.ready) {
        dbus_wait_for_response := true.B
      }
      when (dbus.r.valid && dbus_wait_for_response) {
        rd_write := true.B
        rd_wdata := dbus.r.data // FIXME: This should not be dbus.r.data
        dbus_wait_for_response := false.B
      }
    } .elsewhen (execute2_uop.instr === SW) {
      // TODO: Store
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
        csr.instret_incr := true.B
        // Write complete
        // TODO: Release the writeback stage and complete the instruction
        // TODO: Error handling
      }
    } .elsewhen((execute2_uop.instr === CSRRW) || (execute2_uop.instr === CSRRWI)) {
      // FIXME: Need to restart the instruction fetch process
      instruction_valid := true.B
      when(execute2_uop.instr(11,  7) === 0.U) { // RD == 0; => No read
        csr.cmd := csr_cmd.write
      } .otherwise {  // RD != 0; => Read side effects
        csr.cmd := csr_cmd.read_write
      }
      when(execute2_uop.instr === CSRRW) {
        csr.in := execute2_uop.rs1_data
        memwblog("CSRRW instr=0x%x, pc=0x%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x", execute2_uop.instr, execute2_uop.pc, csr.cmd.asUInt, csr.addr, csr.in)
      } .otherwise { // CSRRWI
        csr.in := execute2_uop.instr(19, 15)
        memwblog("CSRRWI instr=0x%x, pc=0x%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x", execute2_uop.instr, execute2_uop.pc, csr.cmd.asUInt, csr.addr, csr.in)
      }
      rd_wdata := csr.out
      rd_write := true.B
      instr_cplt(true.B)
    //} .elsewhen((execute2_uop.instr === CSRRS) || (execute2_uop.instr === CSRRSI)) {
    //  printf("[core%x c:%d WritebackMemory] CSRRW instr=0x%x, pc=0x%x, execute2_uop.instr, execute2_uop.pc)
      // FIXME: Add CSRRS
      // FIXME: Add EBREAK
      // FIXME: Add ECALL
      // FIXME: Add MRET
      // FIXME: Add SRET
    } .elsewhen((execute2_uop.instr === FENCE) || (execute2_uop.instr === FENCE_I) || (execute2_uop.instr === SFENCE_VMA)) {
      memwblog("Flushing everything instr=0x%x, pc=0x%x", execute2_uop.instr, execute2_uop.pc)
      instr_cplt(true.B)
      cu.cmd := controlunit_cmd.flush
      instruction_valid := true.B
    } .otherwise {
      memwblog("UNKNOWN instr=0x%x, pc=0x%x", execute2_uop.instr, execute2_uop.pc)
      // TODO: Handle unknown instructions with a trap
    }
    // FIXME: Add the Load/Store
    // FIXME: CACHE Add the cache refill
    // FIXME: If active interrupt then control unit will start killing instructions,
    //    so we dont need to do anything else
    // FIXME: Add atomic operations

    
    // TODO: Dont issue atomics store if there is no active lock


    when(rd_write) {
      regs(execute2_uop.instr(11,  7)) := rd_wdata
      memwblog("Write rd=0x%x, value=0x%x", execute2_uop.instr(11,  7), rd_wdata)
    }
    // TODO: Dont unconditionally reset the regs reservation
    
  } .otherwise {
    memwblog("No active instruction")
  }
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object ArmleoCPUGenerator extends App {
  // Temorary disable memory configs as yosys does not know what to do with them
  (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new ArmleoCPU)))
}


