package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._

class L3CacheParams {
  val directoryEntriesLog2: Int = log2Ceil(8 * 1024)
  val dataEntriesLog2: Int = log2Ceil(2 * 1024)
  val directoryWaysLog2: Int = 2
  val dataWaysLog2: Int = 2
  
}


class L3Cache(ccx: CCXParams) extends Module {
  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/
  val io = IO(new Bundle {
    val up = Vec(ccx.coreCount, new corebus_t(ccx))
    val down = Vec(ccx.coreCount, new dbus_t(ccx))
  })

  
  /**************************************************************************/
  /* Structures                                                             */
  /**************************************************************************/
  class directoryEntry(tagWidth: Int) extends Bundle {
    val valid = Bool() // Is this entry valid
    val dirty = Bool() // Is this entry dirty
    val tag = UInt(tagWidth.W) // TODO: Address tag, we the use it to compare with the requested address
    val shared = UInt((ccx.coreCount).W) // Which cores have this line
    // If it is owned then only one bit is set
  }

  class cacheEntry(tagWidth: Int) extends directoryEntry(tagWidth = tagWidth) {
    val data = UInt((ccx.busBytes * 8).W)
  }





  val IDLE        = 0.U(4.W)
  // No active requests
  val DECISION    = 1.U(4.W)
  // Use the results from local buffer to decide what to do next
  // If read shared and somebody has it, then request it from there
  // If read unique and somebody owns it, then we ask to migrate
  // If read unique and somebody shares it, then we ask to invalidate
  // If invalidate then we mark the invalidation
  // If writeback then we mark it clean
  // If nobody has it, forward to memory
  val SNOOP       = 2.U(4.W)
  val RETURN      = 3.U(4.W)
  val BACKMEMORY  = 4.U(4.W)
  
  

  val state = RegInit(IDLE)


  val chosen = Reg(arb.io.chosen.cloneType)
  val ax_bits = Reg(arb.io.out.bits.cloneType)


  val arb = Module(new Arbiter(io.up(0).ax.bits.cloneType, ccx.coreCount))


  // FIXME: Connect the arbiter: arb.io.in() <> 

  when(state === IDLE) {
    when(arb.io.out.valid) {
      chosen := arb.io.chosen // FIXME: Save the chosen so that we dont DECISION the requesting host
      arb.io.out.ready := true.B

      ax_bits := arb.io.out.bits

      state := DECISION


    }
  } .elsewhen(state === DECISION) {
    
  }
  



  // On request arrival we accept the request
  // Then check the directory
  //      If requested unique and somebody has it, then send DECISION request to the owned core


  // Implement the actual storage
  // Implement the directory
  // Implement the DECISIONing
}

