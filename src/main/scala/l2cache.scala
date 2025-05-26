package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._

/*
class L2Cache(n: Int, cp: CoreParams) extends Module {
  val io = IO(new Bundle {
    val up = Vec(n, new corebus_t(cp = cp))
    val down = Vec(n, new dbus_t(cp = cp))
  })

  class directoryEntry extends Bundle {
    val valid = Bool() // Is this entry valid
    val tag = UInt(64.W) // TODO: Address tag, we the use it to compare with the requested address

    val shared = UInt((n).W) // Which cores have this line
    // If it is owned then only one bit is set
  }

  // On request arrival we accept the request
  // Then check the directory
  //      If requested unique and somebody has it, then send snoop request to the owned core


  // Implement the actual storage
  // Implement the directory
  // Implement the snooping
}
*/
