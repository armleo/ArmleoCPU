package armleocpu


import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._


object controlunit_state extends ChiselEnum {
  val reset = 0.U(3.W)
  val debug = 1.U(3.W)
  val idle = 2.U(3.W)
  val new_pc = 3.U(3.W)
  val flush = 3.U(3.W)
}

object controlunit_cmd extends ChiselEnum {
  val none = 0.U(2.W)
  val retire = 1.U(2.W)
  val branch = 2.U(2.W)
}
/*
class controlunit(val c: coreParams = new coreParams) extends Module {
  val fetch_cmd   = IO(Input())
  val cu_cmd      = IO(Input(chiselTypeOf(controlunit_cmd.none)))
  val cu_pc     = RegInit(c.reset_vector.U(c.avLen.W))
  val cu_state  = RegInit(controlunit_state.reset)


}
*/