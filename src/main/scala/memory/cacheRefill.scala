package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._
import chisel3.experimental.dataview._
import armleocpu.bus_const_t.OKAY


class CacheRefill(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Module {
  /**************************************************************************/
  /*  Interface                                                             */
  /**************************************************************************/
  val io = IO(new Bundle {
    val req   = IO(Input(Bool()))
    val physicalAddr = IO(Input(UInt(ccx.apLen.W)))
    // TODO: Writeback: Add support for the refill with unique
    val cplt  = IO(Output(Bool()))
    val err   = IO(Output(Bool()))

    val bus   = IO(new dbus_t)


    // TODO: Get the requested paddr
    // TODO: Use the requested paddr
    // TODO: Write the cache interface
  })
  
  
  
  /**************************************************************************/
  /*  Pipeline's output                                                     */
  /**************************************************************************/
  io.cplt := false.B
  io.err := false.B

  /**************************************************************************/
  /*  Primary logic                                                         */
  /**************************************************************************/
  val requestSent = RegInit(false.B)
  val err = RegInit(false.B)


  when(io.req) {
    /**************************************************************************/
    /*  AR section                                                            */
    /**************************************************************************/
    io.bus.ax.valid := !requestSent
    
    when(io.bus.ax.ready) {
      requestSent := true.B
      err := false.B
    }
    
    /**************************************************************************/
    /*  R section                                                             */
    /**************************************************************************/

    when(io.bus.r.valid) {
      err := io.bus.r.bits.resp =/= OKAY
      

      
      // TODO: handle the cache writing
    }
  }
}
