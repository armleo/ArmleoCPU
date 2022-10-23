package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum

object states extends ChiselEnum {
    val FETCH, DECODE, EXECUTE1, EXECUTE2, MEMORY, WRITEBACK = Value
}

import Consts._

class ArmleoCPU extends Module {
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

  val pc                = Reg(UInt(xLen.W))
  val state             = Reg(states())


  val fetch_uop         = Reg(new Bundle{
    val instr           = UInt(32.W)
    val pc              = UInt(xLen.W)
  })
  
  val decode_uop        = Reg(new Bundle{
    val instr           = UInt(32.W)
    val pc              = UInt(xLen.W)

    val rs1_data        = UInt(xLen.W)
    val rs2_data        = UInt(xLen.W)
  })
  
  val execute1_uop      = Reg(new Bundle{
    val instr           = UInt(32.W)
    val pc              = UInt(xLen.W)
    
    val rs1_data        = UInt(xLen.W)
    val rs2_data        = UInt(xLen.W)

    // Using signed, so it will be sign extended
    val rd_wdata        = SInt(xLen.W)
    val rd_write        = Bool()
  })

  val regs              = SyncReadMem(32, UInt(xLen.W))
  val regs_reservation  = SyncReadMem(32, Bool())

  /**************************************************************************/
  /*                                                                        */
  /*                COMBINATIONAL                                           */
  /*                                                                        */
  /**************************************************************************/
  val rd_reserve        = Wire(Bool())
  rd_reserve := false.B


  ireq_valid := false.B
  ireq_addr := pc

  switch(state) {
    /**************************************************************************/
    /*                                                                        */
    /*                FETCH                                                   */
    /*                                                                        */
    /**************************************************************************/
    is(states.FETCH) {
      ireq_valid := true.B
      when(ireq_ready) {
        state := states.DECODE
        fetch_uop.instr := ireq_data
        fetch_uop.pc    := pc
      }
    }


    /**************************************************************************/
    /*                                                                        */
    /*                DECODE                                                  */
    /*                                                                        */
    /**************************************************************************/
    is(states.DECODE) {
      // TODO: PIPELINE Make sure that rs1/rs2 is not conflicting with the later stages.
      // TODO: PIPELINE Make sure the proper instruction is used from pipeline, not from global
      // TODO: PIPELINE Transfer from this stage to execute 1
      decode_uop.instr    := fetch_uop.instr
      decode_uop.pc       := fetch_uop.pc

      // STALL until reservation is reset
      decode_uop.rs1_data := regs.read(fetch_uop.instr(19, 15))
      decode_uop.rs2_data := regs.read(fetch_uop.instr(24, 20))

      // TODO: Dont unconditonally reserve the register
      // IF REGISTER IS NOT RESERVED, ELSE STALL
      // RESERVE RD
      rd_reserve := true.B




      regs_reservation.write(fetch_uop.instr(11, 7), rd_reserve)
      state := states.EXECUTE1
    }
    
    /**************************************************************************/
    /*                                                                        */
    /*                EXECUTE1                                                */
    /*                                                                        */
    /**************************************************************************/
    is(states.EXECUTE1) {
      execute1_uop.instr    := decode_uop.instr
      execute1_uop.pc       := decode_uop.pc
      execute1_uop.rs1_data := decode_uop.rs1_data
      execute1_uop.rs2_data := decode_uop.rs2_data
      execute1_uop.rd_wdata := 0.S(xLen.W)

      switch(decode_uop.instr(6, 0)) {
        /**************************************************************************/
        /*                LUI                                                     */
        /**************************************************************************/
        is("b0110111".U) {
          // Use SInt to sign extend it before writing
          execute1_uop.rd_wdata := Cat(decode_uop.instr(31, 12), 0.U(12.W)).asSInt()
          execute1_uop.rd_write := true.B
        }
        /**************************************************************************/
        /*                AUIPC                                                   */
        /**************************************************************************/
        is("b0010111".U) {
          execute1_uop.rd_wdata := execute1_uop.pc.asSInt() + Cat(execute1_uop.instr(31, 12), 0.U(12.W)).asSInt()
          execute1_uop.rd_write := true.B
        }
      }

      state := states.EXECUTE2
    }
    /**************************************************************************/
    /*                                                                        */
    /*                EXECUTE2                                                */
    /*                                                                        */
    /**************************************************************************/
    is(states.EXECUTE2) {
      state := states.MEMORY
    }

    /**************************************************************************/
    /*                                                                        */
    /*                MEMORY                                                  */
    /*                                                                        */
    /**************************************************************************/
    is(states.MEMORY) {
      state := states.WRITEBACK
    }

    /**************************************************************************/
    /*                                                                        */
    /*                WRITEBACK                                               */
    /*                                                                        */
    /**************************************************************************/
    is(states.WRITEBACK) {
      state := states.FETCH
    }

  }
  dontTouch(decode_uop)
  dontTouch(fetch_uop)
  dontTouch(execute1_uop)
  //dontTouch(regs)
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object ArmleoCPUGenerator extends App {
  (new ChiselStage).execute(Array("-frsq", "-o:memory_configs", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new ArmleoCPU)))
}


