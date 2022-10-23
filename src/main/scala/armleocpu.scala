package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum

object states extends ChiselEnum {
    val FETCH, DECODE, EXECUTE1, EXECUTE2, MEMORY, WRITEBACK = Value
}

import Consts._

class ArmleoCPU extends Module {
  val ireq_addr   = IO(Output(UInt(xLen.W)))
  val ireq_data   = IO(Input (UInt(xLen.W)))
  val ireq_valid  = IO(Output(Bool()))
  val ireq_ready  = IO(Input (Bool()))

  val pc          = Reg(UInt(xLen.W))
  val state       = Reg(states())
  val instr       = Reg(UInt(32.W))
  val rs1_data    = Reg(UInt(xLen.W))
  val rs2_data    = Reg(UInt(xLen.W))


  // Using signed, so it will be sign extended
  val rd_wdata     = Reg(SInt(xLen.W))

  val regs        = SyncReadMem(32, UInt(xLen.W))
  

  val opcode      = instr(6, 0)



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
        instr := ireq_data
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
      rs1_data := regs.read(instr(19, 15))
      rs2_data := regs.read(instr(24, 20))

      state := states.EXECUTE1
    }
    
    /**************************************************************************/
    /*                                                                        */
    /*                EXECUTE1                                                */
    /*                                                                        */
    /**************************************************************************/
    is(states.EXECUTE1) {
      switch(opcode) {
        /**************************************************************************/
        /*                LUI                                                     */
        /**************************************************************************/
        is("b0110111".U) { 
          // Use SInt to sign extend it before writing
          rd_wdata := Cat(instr(31, 12), 0.U(12.W)).asSInt()
        }
        /**************************************************************************/
        /*                AUIPC                                                   */
        /**************************************************************************/
      }
    }
  }
  dontTouch(rd_wdata)
  dontTouch(rs1_data)
  dontTouch(rs2_data)
}



import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object ArmleoCPUGenerator extends App {
  (new ChiselStage).execute(Array("-frsq", "-o:memory_configs", "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new ArmleoCPU)))
}


