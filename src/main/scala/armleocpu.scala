package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

import io.AnsiColor._

import Instructions._
import armleocpu.utils._

class rvfi_o(c: CoreParams) extends Bundle {
  val valid = Bool()
  val order = UInt(64.W)
  val insn  = UInt(c.archParams.iLen.W)
  val trap  = Bool()
  val halt  = Bool()
  val intr  = Bool()
  val mode  = UInt(2.W) // Privilege mode
  val ixl   = UInt(2.W)

  // Register
  val rs1_addr  = UInt(5.W)
  val rs2_addr  = UInt(5.W)
  val rs1_rdata = UInt(c.archParams.xLen.W)
  val rs2_rdata = UInt(c.archParams.xLen.W)
  val rd_addr   = UInt(5.W)
  val rd_wdata  = UInt(c.archParams.xLen.W)

  // PC
  val pc_rdata  = UInt(c.archParams.xLen.W)
  val pc_wdata  = UInt(c.archParams.xLen.W)

  // MEM
  val mem_addr  = UInt(c.archParams.xLen.W)
  val mem_rmask = UInt((c.archParams.xLen / 8).W)
  val mem_wmask = UInt((c.archParams.xLen / 8).W)
  val mem_rdata = UInt(c.archParams.xLen.W)
  val mem_wdata = UInt(c.archParams.xLen.W)

}

class ArmleoCPU(val c: CoreParams = new CoreParams) extends Module {
  var xLen_log2 = c.archParams.xLen_log2

  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val ibus        = IO(new ibus_t(c))
  val dbus        = IO(new dbus_t(c))
  val int         = IO(Input(new InterruptsInputs))
  val debug_int_i = IO(Input(Bool()))
  val rvfi        = if(c.rvfi_enabled) IO(Output(new rvfi_o(c))) else Wire(new rvfi_o(c))

  if(!c.rvfi_enabled && c.rvfi_dont_touch) {
    dontTouch(rvfi) // It should be optimized away
  }


  

  val dlog = new Logger(c.lp.coreName, f"${YELLOW}decod", c.core_verbose)
  val e1log = new Logger(c.lp.coreName, f"${GREEN}exec1", c.core_verbose)
  val e2log = new Logger(c.lp.coreName, f"${BLUE}exec2", c.core_verbose)
  val memwblog = new Logger(c.lp.coreName, f"${RED}memwb", c.core_verbose)

  /**************************************************************************/
  /*                                                                        */
  /*                Submodules                                              */
  /*                                                                        */
  /**************************************************************************/

  val fetch   = Module(new Fetch(c))
  val csr = Module(new CSR(c))
  val cu  = Module(new ControlUnit(c))


  /**************************************************************************/
  /*                                                                        */
  /*                Submodules permanent connections                        */
  /*                                                                        */
  /**************************************************************************/

  fetch.ibus <> ibus
  // TODO: Add Instruction PTE storage for RVFI
  
  

  
  val dcache  = Module(new Cache(verbose = c.dcache_verbose, c = c, instName = "data$", cp = c.dcache))
  val dtlb    = Module(new TLB(verbose = c.dtlb_verbose, instName = "dtlb ", c = c, tp = c.dtlb))
  val dptw    = Module(new PTW(instName = "dptw ", c = c, tp = c.dtlb))
  val drefill = Module(new Refill(c = c, cp = c.dcache, dcache))
  // TODO: Add PTE storage for RVFI
  
  

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/


  val atomic_lock             = RegInit(false.B)
  val atomic_lock_addr        = Reg(UInt(c.archParams.apLen.W))
  val atomic_lock_doubleword  = Reg(Bool()) // Either word 010 and 011

  val dbus_ax_done            = RegInit(false.B)
  val dbus_w_done             = RegInit(false.B)
  val dbus_wait_for_response  = RegInit(false.B)
  

  // Registers

  val regs              = Mem(32, UInt(c.archParams.xLen.W))
  val regs_reservation  = RegInit(VecInit.tabulate(32) {f:Int => false.B})

  

  // DECODE
  class decode_uop_t extends fetch_uop_t(c) {
    val rs1_data        = UInt(c.archParams.xLen.W)
    val rs2_data        = UInt(c.archParams.xLen.W)
  }

  val decode_uop        = Reg(new decode_uop_t)
  val decode_uop_valid  = RegInit(false.B)
  
  // EXECUTE1
  class execute_uop_t extends decode_uop_t {
    // Using signed, so it will be sign extended
    val alu_out         = SInt(c.archParams.xLen.W)
    //val muldiv_out      = SInt(c.archParams.xLen.W)
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
  val decode_uop_accept   = Wire(Bool())
  val execute1_uop_accept = Wire(Bool())
  val execute2_uop_accept = Wire(Bool())


  /**************************************************************************/
  /*                Dbus combinational signals                              */
  /**************************************************************************/
  dbus.aw.valid := false.B
  dbus.aw.addr  := execute2_uop.alu_out.asSInt.pad(c.archParams.apLen) // FIXME: Mux depending on vm enabled
  // FIXME: Needs to depend on dbus_len
  dbus.aw.size  := "b010".U // FIXME: Needs to be set properly
  dbus.aw.len   := 0.U
  dbus.aw.lock  := false.B // FIXME: Needs to be set properly

  dbus.w.valid  := false.B
  dbus.w.data   := execute2_uop.rs2_data // FIXME: Duplicate
  dbus.w.strb   := (-1.S(dbus.w.strb.getWidth.W)).asUInt() // Just pick any number, that is bigger than write strobe
  // FIXME: Strobe needs proper values
  // FIXME: Strobe needs proper value
  dbus.w.last   := true.B // Constant

  dbus.b.ready  := false.B

  dbus.ar.valid := false.B
  dbus.ar.addr  := execute2_uop.alu_out.asSInt.pad(c.archParams.apLen) // FIXME: Needs a proper MUX
  // FIXME: Needs to depend on dbus_len
  dbus.ar.size  := "b010".U // FIXME: This should be depending on value of c.archParams.xLen
  dbus.ar.len   := 0.U
  dbus.ar.lock  := false.B

  dbus.r.ready  := false.B
  
  
  /**************************************************************************/
  /*                Pipeline combinational signals                          */
  /**************************************************************************/
  val should_rd_reserve       = Wire(Bool())
  should_rd_reserve           := false.B

  val rd_write = Wire(Bool())
  val rd_wdata = Wire(UInt(c.archParams.xLen.W))

  rd_write := false.B
  rd_wdata := execute2_uop.alu_out.asUInt()



  /**************************************************************************/
  /*                Decode pipeline combinational signals                   */
  /**************************************************************************/

  // Ignore the below mumbo jumbo
  // It was the easiest way to get universal instructions without checking c.archParams.xLen for each
  val decode_uop_simm12 = Wire(SInt(c.archParams.xLen.W))
  decode_uop_simm12 := decode_uop.instr(31, 20).asSInt()

  // The regfile has unknown register state for address 0
  // This is by-design
  // So instead we MUX zero at execute1 stage if its read from 0th register

  val execute1_rs1_data = Mux(decode_uop.instr(19, 15) =/= 0.U, decode_uop.rs1_data, 0.U)
  val execute1_rs2_data = Mux(decode_uop.instr(24, 20) =/= 0.U, decode_uop.rs2_data, 0.U)
  
  val decode_uop_shamt_xlen = Wire(UInt(xLen_log2.W))
  val decode_uop_rs2_shift_xlen = Wire(UInt(xLen_log2.W))
  if(c.archParams.xLen == 32) {
    decode_uop_shamt_xlen := decode_uop.instr(24, 20)
    decode_uop_rs2_shift_xlen := execute1_rs2_data(4, 0)
  } else {
    decode_uop_shamt_xlen := decode_uop.instr(25, 20)
    decode_uop_rs2_shift_xlen := execute1_rs2_data(5, 0)
  }
  

  /**************************************************************************/
  /*                CSR Signals                                             */
  /**************************************************************************/
  csr.int <> int
  csr.instret_incr := false.B //
  csr.addr := execute2_uop.instr(31, 20) // Constant
  csr.cause := 0.U // FIXME: Need to be properly set
  csr.cmd := csr_cmd.none
  csr.epc := execute2_uop.pc
  csr.in := 0.U // FIXME: Needs to be properly connected


  /**************************************************************************/
  /*                ControlUnit Signals                                     */
  /**************************************************************************/

  cu.cmd := controlunit_cmd.none
  cu.pc_in := execute2_uop.pc_plus_4
  cu.decode_to_cu_ready := !decode_uop_valid
  cu.execute1_to_cu_ready := !execute1_uop_valid
  cu.execute2_to_cu_ready := !execute2_uop_valid
  cu.fetch_ready := !fetch.busy
  cu.wb_ready := true.B
  
  /**************************************************************************/
  /*                                                                        */
  /*                Fetch combinational signals                             */
  /*                                                                        */
  /**************************************************************************/
  fetch.uop_accept    := false.B
  fetch.cmd           := cu.cu_to_fetch_cmd
  fetch.mem_priv      := csr.mem_priv_o
  fetch.new_pc        := cu.pc_out

  /**************************************************************************/
  /*                                                                        */
  /*                Non permanent memory related combinationals             */
  /*                                                                        */
  /**************************************************************************/
  
  dcache.s0 <> drefill.s0

  /**************************************************************************/
  /*                RVFI                                                    */
  /**************************************************************************/

  rvfi.valid := false.B
  rvfi.halt := false.B
  rvfi.ixl  := Mux(c.archParams.xLen.U === 32.U, 1.U, 2.U)
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
  rvfi.rd_addr  := 0.U // No write === 0 addr
  rvfi.rd_wdata := 0.U // Do not write unless valid


  rvfi.pc_rdata := execute2_uop.pc
  rvfi.pc_wdata := cu.pc_in

  rvfi.mem_addr := execute2_uop.alu_out.asSInt.pad(c.archParams.apLen).asUInt // FIXME: Need to be muxed depending on vm_enabled
  rvfi.mem_rmask := 0.U // FIXME: rvfi.mem_rmask
  rvfi.mem_wmask := 0.U // FIXME: rvfi.mem_wmask
  rvfi.mem_rdata := rd_wdata // FIXME: rvfi.mem_rdata
  // FIXME: rd_wdata depended
  rvfi.mem_wdata := 0.U  // FIXME: rvfi.mem_wdata

  /**************************************************************************/
  /*                                                                        */
  /*                DECODE Stage                                            */
  /*                                                                        */
  /**************************************************************************/
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

  // TODO: FMAX: Add registerl slice

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

      execute1_uop.alu_out      := 0.S
      //execute1_uop.muldiv_out   := 0.S(c.archParams.xLen.W)

      execute1_uop.branch_taken := false.B

      /**************************************************************************/
      /*                                                                        */
      /*                Alu-like EXECUTE1                                       */
      /*                                                                        */
      /**************************************************************************/
      when(decode_uop.instr === LUI) {
        // Use SInt to sign extend it before writing
        execute1_uop.alu_out    := Cat(decode_uop.instr(31, 12), 0.U(12.W)).asSInt()
        
      } .elsewhen(decode_uop.instr === AUIPC) {
        execute1_uop.alu_out    := decode_uop.pc.asSInt() + Cat(decode_uop.instr(31, 12), 0.U(12.W)).asSInt()
        execute1_debug("AUIPC")
      
      /**************************************************************************/
      /*                                                                        */
      /*                Branching EXECUTE1                                      */
      /*                                                                        */
      /**************************************************************************/
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
      /**************************************************************************/
      /*                                                                        */
      /*                Memory EXECUTE1                                         */
      /*                                                                        */
      /**************************************************************************/
      } .elsewhen(decode_uop.instr === LOAD) {
        execute1_uop.alu_out := execute1_rs1_data.asSInt() + decode_uop.instr(31, 20).asSInt()
        execute1_debug("LOAD")
      } .elsewhen(decode_uop.instr === STORE) {
        execute1_uop.alu_out := execute1_rs1_data.asSInt() + Cat(decode_uop.instr(31, 25), decode_uop.instr(11, 7)).asSInt()
        execute1_debug("STORE")
      
      /**************************************************************************/
      /*                                                                        */
      /*                ALU EXECUTE1                                            */
      /*                                                                        */
      /**************************************************************************/
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
      e1log("No instruction found or instruction killed")
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

  // TODO: Decouple the ready logic to improve Fmax by around ~33%
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

  
  /**************************************************************************/
  /*                Instruction completion shorthand                        */
  /**************************************************************************/
  // Used to complete the instruction
  // If br_pc_valid is set then it means that fetch needs to start from br_pc
  // Therefore command control unit to start killing the pipeline
  // and restarting from br_pc
  // We also retire instructions here, so set the rvfi_valid
  // and instret_incr
  def instr_cplt(br_pc_valid: Bool = false.B, br_pc: UInt = execute2_uop.pc_plus_4): Unit = {
    execute2_uop_accept := true.B
    rvfi.valid := true.B
    csr.instret_incr := true.B
    
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

  when(cu.wb_flush) {
    cu.wb_ready := true.B // FIXME: Should be false unless all dcache is invalidated
    memwblog("Flushing")
  } .elsewhen(execute2_uop_valid && !cu.wb_kill) {
    execute2_uop_accept := false.B
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Debug enter logic                                 */
    /*                                                                        */
    /**************************************************************************/
    when(debug_int_i) {
      memwblog("Debug interrupt")
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Interrupt logic                                   */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(csr.int_pending_o) {
      memwblog("External Interrupt")
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: FETCH ERROR LOGIC                                 */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(execute2_uop.ifetch_access_fault) {
      memwblog("Instruction fetch access fault")
    } .elsewhen (execute2_uop.ifetch_page_fault) {
      memwblog("Instruction fetch page fault")
    /**************************************************************************/
    /*                                                                        */
    /*                Alu/Alu-like writeback                                  */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(
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


    
    /**************************************************************************/
    /*                                                                        */
    /*                JAL/JALR                                                */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(
        (execute2_uop.instr === JAL) ||
        (execute2_uop.instr === JALR)
    ) {
      rd_wdata := execute2_uop.pc_plus_4
      rd_write := true.B

      when(execute2_uop.instr === JALR) {
        val next_cu_pc = execute2_uop.alu_out.asUInt() & (~(1.U(c.archParams.avLen.W)))
        instr_cplt(true.B, next_cu_pc)
        memwblog("JALR instr=0x%x, pc=0x%x, rd_wdata=0x%x, target=0x%x", execute2_uop.instr, execute2_uop.pc, rd_wdata, next_cu_pc)
      } .otherwise {
        instr_cplt(true.B, execute2_uop.alu_out.asUInt())
        memwblog("JAL instr=0x%x, pc=0x%x, rd_wdata=0x%x, target=0x%x", execute2_uop.instr, execute2_uop.pc, rd_wdata, execute2_uop.alu_out.asUInt())
      }
      
      // Reset PC to zero
      // TODO: C-ext change to (0) := 0.U
      // FIXME: Add a check for PC to be aligned to 4 bytes or error out
      execute2_uop_accept := true.B

    /**************************************************************************/
    /*                                                                        */
    /*               Branching logic                                          */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen (
      (execute2_uop.instr === BEQ) || 
      (execute2_uop.instr === BNE) || 
      (execute2_uop.instr === BLT) || 
      (execute2_uop.instr === BLTU) || 
      (execute2_uop.instr === BGE) || 
      (execute2_uop.instr === BGEU)
    ) {
      when(execute2_uop.branch_taken) {
        // TODO: New variant of branching. Always take the branch backwards in decode stage. And if mispredicted in writeback stage branch towards corrected path
        execute2_uop_accept := true.B
        instr_cplt(true.B, execute2_uop.alu_out.asUInt)
        memwblog("BranchTaken instr=0x%x, pc=0x%x, target=0x%x", execute2_uop.instr, execute2_uop.pc, execute2_uop.alu_out.asUInt())
      } .otherwise {
        instr_cplt()
        memwblog("BranchNotTaken instr=0x%x, pc=0x%x", execute2_uop.instr, execute2_uop.pc)
        execute2_uop_accept := true.B
      }
      // TODO: IMPORTANT! Branch needs to check for misaligment in this stage
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Load logic                                        */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen (execute2_uop.instr === LW) {
      // TODO: Load
      // TODO: Add

      dbus.ar.valid := !dbus_wait_for_response
      when(dbus.ar.ready) {
        dbus_wait_for_response := true.B
      }
      when (dbus.r.valid && dbus_wait_for_response) {
          rd_write := true.B
          // FIXME: This should not be dbus.r.data
          // FIXME: Proper sign extension needed
          val read_data = dbus.r.data.asTypeOf(Vec(c.bp.data_bytes / 4, SInt(32.W)))((dbus.ar.addr.asUInt & (c.bp.data_bytes - 1).U) >> 2)
          rd_wdata := read_data.pad(c.archParams.xLen).asUInt
          
          dbus_wait_for_response := false.B
      }
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Store logic                                       */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen (execute2_uop.instr === SW) {
      // TODO: Store

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
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: CSRRW/CSRRWI                                      */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen((execute2_uop.instr === CSRRW) || (execute2_uop.instr === CSRRWI)) {
      rd_wdata := csr.out

      when(execute2_uop.instr(11,  7) === 0.U) { // RD == 0; => No read
        csr.cmd := csr_cmd.write
      } .otherwise {  // RD != 0; => Read side effects
        csr.cmd := csr_cmd.read_write
        rd_write := true.B
      }
      when(execute2_uop.instr === CSRRW) {
        csr.in := execute2_uop.rs1_data
        memwblog("CSRRW instr=0x%x, pc=0x%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x", execute2_uop.instr, execute2_uop.pc, csr.cmd.asUInt, csr.addr, csr.in)
      } .otherwise { // CSRRWI
        csr.in := execute2_uop.instr(19, 15)
        memwblog("CSRRWI instr=0x%x, pc=0x%x, csr.cmd=0x%x, csr.addr=0x%x, csr.in=0x%x", execute2_uop.instr, execute2_uop.pc, csr.cmd.asUInt, csr.addr, csr.in)
      }

      // Need to restart the instruction fetch process
      instr_cplt(true.B)
      when(csr.err) {
        // FIXME: Add error handling
      }
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: CSRRS/CSRRSI                                      */
    /*                                                                        */
    /**************************************************************************/
    //} .elsewhen((execute2_uop.instr === CSRRS) || (execute2_uop.instr === CSRRSI)) {
    //  printf("[core%x c:%d WritebackMemory] CSRRW instr=0x%x, pc=0x%x, execute2_uop.instr, execute2_uop.pc)
    /**************************************************************************/
    /*                                                                        */
    /*              FIXME: CSRRC/CSRRCI                                       */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: CSRRC/CSRRCI                                      */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: EBREAK                                            */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: ECALL                                             */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: Flushing instructions                             */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen((execute2_uop.instr === FENCE) || (execute2_uop.instr === FENCE_I) || (execute2_uop.instr === SFENCE_VMA)) {
      memwblog("Flushing everything instr=0x%x, pc=0x%x", execute2_uop.instr, execute2_uop.pc)
      instr_cplt(true.B)
      cu.cmd := controlunit_cmd.flush
    /**************************************************************************/
    /*                                                                        */
    /*               MRET                                                     */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen((csr.mem_priv_o.privilege === privilege_t.M) && (execute2_uop.instr === MRET)) {
      csr.cmd := csr_cmd.mret
      instr_cplt(true.B, csr.next_pc)
      assert(csr.err === false.B)
    /**************************************************************************/
    /*                                                                        */
    /*               SRET                                                     */
    /*                                                                        */
    /**************************************************************************/
    } .elsewhen(((csr.mem_priv_o.privilege === privilege_t.M) || (csr.mem_priv_o.privilege === privilege_t.S)) && !csr.hyptrap_o.tsr && (execute2_uop.instr === SRET)) {
      csr.cmd := csr_cmd.sret
      instr_cplt(true.B, csr.next_pc)
      assert(csr.err === false.B)
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: ATOMICS LR                                        */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: ATOMICS SC                                        */
    /*               NOTE: Dont issue atomics store if no active lock         */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: ATOMICS AMOOP                                     */
    /*                                                                        */
    /**************************************************************************/
    /**************************************************************************/
    /*                                                                        */
    /*               FIXME: UNKNOWN INSTURCTION ERROR                         */
    /*                                                                        */
    /**************************************************************************/
    } .otherwise {
      memwblog("UNKNOWN instr=0x%x, pc=0x%x", execute2_uop.instr, execute2_uop.pc)
    }


    when(rd_write) {
      regs(execute2_uop.instr(11,  7)) := rd_wdata
      memwblog("Write rd=0x%x, value=0x%x", execute2_uop.instr(11,  7), rd_wdata)
      rvfi.rd_addr := execute2_uop.instr(11,  7)
      rvfi.rd_wdata := Mux(execute2_uop.instr(11, 7) === 0.U, 0.U, rd_wdata)
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
  (new ChiselStage).execute(
    Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/recommended_conf/"),
    Seq(
      ChiselGeneratorAnnotation(
        () => new ArmleoCPU(
          new CoreParams(
            icache = new CacheParams(ways = 8, entries = 64),
            dcache = new CacheParams(ways = 8, entries = 64),
            itlb = new TlbParams(ways = 8),
            dtlb = new TlbParams(ways = 8),
            bp = new BusParams(data_bytes = 8),
          )
        )
      )
    )
  )
  
}


