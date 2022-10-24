package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

object states extends ChiselEnum {
    val /*PREFETCH,*/ FETCH, DECODE, EXECUTE1, EXECUTE2, MEMORY, WRITEBACK = Value
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


  def LOAD                = BitPat("b?????????????????????????0000011")
  def LB                  = BitPat("b?????????????????000?????0000011")
  def LH                  = BitPat("b?????????????????001?????0000011")
  def LD                  = BitPat("b?????????????????011?????0000011")
  def LBU                 = BitPat("b?????????????????100?????0000011")
  def LHU                 = BitPat("b?????????????????101?????0000011")
  def LWU                 = BitPat("b?????????????????110?????0000011")

  def STORE               = BitPat("b?????????????????????????0100011")
  def SW                  = BitPat("b?????????????????010?????0100011")
  def SH                  = BitPat("b?????????????????001?????0100011")
  def SB                  = BitPat("b?????????????????000?????0100011")

  def ADD                 = BitPat("b0000000??????????000?????0110011")
  def ADDW                = BitPat("b0000000??????????000?????0111011")
  def SUB                 = BitPat("b0100000??????????000?????0110011")
  def XOR                 = BitPat("b0000000??????????100?????0110011")
  def XORI                = BitPat("b?????????????????100?????0010011")
}

import Instructions._
import Consts._

class ArmleoCPU extends Module {
  val reset_vector = 0x4000_0000
  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/
  val ireq_addr   = IO(Output(UInt(xLen.W)))
  val ireq_data   = IO(Input (UInt(xLen.W)))
  val ireq_valid  = IO(Output(Bool()))
  val ireq_ready  = IO(Input (Bool()))

  /**************************************************************************/
  /*                                                                        */
  /*                STATE                                                   */
  /*                                                                        */
  /**************************************************************************/

  val pc                = RegInit(reset_vector.U(xLen.W))
  val state             = RegInit(states.FETCH)
  val atomic_lock       = RegInit(false.B)


  val fetch_uop         = Reg(new Bundle{
    val instr           = UInt(iLen.W)
    val pc              = UInt(xLen.W)
  })
  
  val decode_uop        = Reg(new Bundle{
    val instr           = UInt(iLen.W)
    val pc              = UInt(xLen.W)

    val rs1_data        = UInt(xLen.W)
    val rs2_data        = UInt(xLen.W)
  })
  
  val execute1_uop      = Reg(new Bundle{
    val instr           = UInt(iLen.W)
    val pc              = UInt(xLen.W)
    
    val rs1_data        = UInt(xLen.W)
    val rs2_data        = UInt(xLen.W)

    // Using signed, so it will be sign extended
    val alu_out         = SInt(xLen.W)
    val muldiv_out      = SInt(xLen.W)
    val branch_taken    = Bool()
  })

  val execute2_uop      = Reg(new Bundle{
    val instr           = UInt(iLen.W)
    val pc              = UInt(xLen.W)
    
    val rs1_data        = UInt(xLen.W)
    val rs2_data        = UInt(xLen.W)

    // Using signed, so it will be sign extended
    val alu_out         = SInt(xLen.W)
    val muldiv_out      = SInt(xLen.W)
    val branch_taken    = Bool()
  })

  val memory_uop      = Reg(new Bundle{
    val instr           = UInt(iLen.W)
    val pc              = UInt(xLen.W)
    
    val rs1_data        = UInt(xLen.W)
    val rs2_data        = UInt(xLen.W)

    // Using signed, so it will be sign extended
    val alu_out         = SInt(xLen.W)
    val muldiv_out      = SInt(xLen.W)
    val branch_taken    = Bool()
    /*
    TODO: Add in the future
    val tlb_lookup_result = 
    val mem_lookup  
    */
  })


  val regs              = SyncReadMem(32, UInt(xLen.W))
  val regs_reservation  = SyncReadMem(32, Bool())

  /**************************************************************************/
  /*                                                                        */
  /*                COMBINATIONAL                                           */
  /*                                                                        */
  /**************************************************************************/
  val should_rd_reserve       = Wire(Bool())
  should_rd_reserve           := false.B


  ireq_valid := false.B
  ireq_addr := pc

  switch(state) {
    /**************************************************************************/
    /*                                                                        */
    /*                PREFETCH                                                */
    /*                                                                        */
    /**************************************************************************/
    /*is(states.PREFETCH) {
      // Starting point of fetch
      // TODO: PIPELINE issue the TLB request and ICACHE request in the future
      // TODO: PIPELINE accept the control unit requests to jump to a location
      state := states.FETCH
    }*/
    /**************************************************************************/
    /*                                                                        */
    /*                FETCH                                                   */
    /*                                                                        */
    /**************************************************************************/
    is(states.FETCH) {
      // TODO: PIPELINE Accept the uop and examine the output of TLB/ICACHE
      // TODO: PIPELINE If miss then send the fetch request to IBUS
      // TODO: PIPELINE else if not a miss then send the result to next stage

      // Right now, just use simple interface as shown below
      ireq_valid := true.B
      when(ireq_ready) {
        fetch_uop.instr := ireq_data
        fetch_uop.pc    := pc

        state := states.DECODE
      }
      // Save the high bit of the instruction, for RVC instructions
    }


    /**************************************************************************/
    /*                                                                        */
    /*                DECODE                                                  */
    /*                                                                        */
    /**************************************************************************/
    is(states.DECODE) {
      // IF REGISTER not reserved, then move the Uop downs stage
      // ELSE stall

      // Only send the uop down the stage if no conflict with any of rs1/rs2/rd
      // Because if RD conflicts then when rd_reserve is reset,
      // in the future just increment it instead?
      // the pipeline will issue instructions with old register values
      val rs1_reserved  = regs_reservation.read(fetch_uop.instr(19, 15))
      val rs2_reserved  = regs_reservation.read(fetch_uop.instr(24, 20))
      val rd_reserved   = regs_reservation.read(fetch_uop.instr(11,  7))

      val stall         = rs1_reserved || rs2_reserved || rd_reserved
      
      when (!stall) {
        // TODO: Dont unconditonally reserve the register
        regs_reservation.write    (fetch_uop.instr(11, 7),    fetch_uop.instr(11, 7) =/= 0.U)
        decode_uop.viewAsSupertype(fetch_uop.cloneType)   :=  fetch_uop

        // STALL until reservation is reset
        decode_uop.rs1_data                               :=  regs.read(fetch_uop.instr(19, 15))
        decode_uop.rs2_data                               :=  regs.read(fetch_uop.instr(24, 20))
        
        state                                             :=  states.EXECUTE1
      }
    }
    
    /**************************************************************************/
    /*                                                                        */
    /*                EXECUTE1                                                */
    /*                                                                        */
    /**************************************************************************/
    is(states.EXECUTE1) {
      execute1_uop.viewAsSupertype(decode_uop.cloneType) := decode_uop

      execute1_uop.alu_out      := 0.S(xLen.W)
      execute1_uop.muldiv_out := 0.S(xLen.W)

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
        execute1_uop.alu_out := decode_uop.rs1_data.asSInt() + Cat(decode_uop.instr(31), decode_uop.instr(7), decode_uop.instr(30, 25), decode_uop.instr(11, 8), 0.U(1.W)).asSInt()

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
      when(decode_uop.instr === ADD) {
        execute1_uop.alu_out := decode_uop.rs1_data.asSInt() + decode_uop.rs2_data.asSInt()
      }
      when(decode_uop.instr === SUB) {
        execute1_uop.alu_out := decode_uop.rs1_data.asSInt() - decode_uop.rs2_data.asSInt()
      }
      // TODO: Rest of instructions here
      // TODO: MULDIV here
      state := states.EXECUTE2
    }
    /**************************************************************************/
    /*                                                                        */
    /*                EXECUTE2                                                */
    /*                                                                        */
    /**************************************************************************/
    is(states.EXECUTE2) {
      state := states.MEMORY
      execute2_uop.viewAsSupertype(execute1_uop.cloneType) := execute1_uop
    }

    /**************************************************************************/
    /*                                                                        */
    /*                MEMORY                                                  */
    /*                                                                        */
    /**************************************************************************/
    is(states.MEMORY) {
      when(execute2_uop.instr === LOAD) {
        // TODO: TLB/Cache request
        // TODO: Output/save the TLB output if stalled
      }
      when(execute2_uop.instr === STORE) {
        // TODO: Do nothing, but TLB/Cache request
      }
      memory_uop := execute2_uop
      state := states.WRITEBACK
      // TODO: Send request to the cache and forward it to 
    }

    /**************************************************************************/
    /*                                                                        */
    /*                WRITEBACK                                               */
    /*                                                                        */
    /**************************************************************************/
    is(states.WRITEBACK) {
      when(
        (memory_uop.instr === LUI) ||
        (memory_uop.instr === AUIPC) ||
        (memory_uop.instr === ADD) ||
        (memory_uop.instr === SUB)
        // TODO: Add the rest of ALU out write back 
      ) {
        regs.write(memory_uop.instr(11,  7), memory_uop.alu_out.asUInt())
      }
      // TODO: Add the rest of ALU_OUT
      // TODO: Add the Load/Store
      // TODO: CACHE Add the cache refill
      // TODO: The request to control to change the PC for branching instructions
      // TODO: Tell the control unit what the current PC is
      // TODO: If active interrupt then control unit will start killing instructions,
      //    so we dont need to do anything else
      state := states.FETCH
    }
  }
  dontTouch(decode_uop)
  dontTouch(fetch_uop)
  dontTouch(execute1_uop)
  dontTouch(execute2_uop)
  //dontTouch(regs)
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object ArmleoCPUGenerator extends App {
  (new ChiselStage).execute(Array("-frsq", "-o:memory_configs", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new ArmleoCPU)))
}


