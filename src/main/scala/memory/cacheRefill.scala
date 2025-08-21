package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._
import chisel3.experimental.dataview._
import armleocpu.busConst._


class CacheArrayArbIO(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  val req  = Flipped(Decoupled(new CacheArrayReq))
  val resp = Valid(new CacheArrayResp)
}

class CacheRefill(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Module {
  import CacheUtils._

  // TODO: Writeback: Add support for the refill with unique


  /**************************************************************************/
  /*  Interface                                                             */
  /**************************************************************************/
  val io = IO(new Bundle {
    val req   = Input(Bool())
    val physicalAddr = Input(UInt(ccx.apLen.W))
    
    val cplt  = Output(Bool())
    val readData = Vec(ccx.xLenBytes, UInt(8.W)) // Preemptive response to be returned to requesting cache
    val err   = Output(Bool())
    val bus   = new Bus

    val victimWayIdx = Input(UInt(cp.waysLog2.W))
    val cacheReq = Decoupled(new CacheArrayReq)
  })
  
  
  val metaWdata = Wire(new CacheMeta)
  metaWdata.ptag := getPtag(io.physicalAddr)
  metaWdata.valid := true.B

  
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



  io.bus.ax.bits.addr := Cat(getPtag(io.physicalAddr), getIdx(io.physicalAddr), 0.U(ccx.cacheLineLog2.W))
  io.bus.ax.bits.op   := OP_READ
  io.bus.ax.bits.strb := DontCare

  io.cacheReq.valid := false.B
  io.cacheReq.bits.addr := io.physicalAddr
  io.cacheReq.bits.metaWrite := true.B
  io.cacheReq.bits.metaWdata := VecInit(Seq.fill(cp.ways)(metaWdata))
  io.cacheReq.bits.metaMask  := UIntToOH(io.victimWayIdx)

  io.cacheReq.bits.dataWrite := true.B
  io.cacheReq.bits.dataWayIdx := io.victimWayIdx
  io.cacheReq.bits.dataWdata := io.bus.r.bits.data
  io.cacheReq.bits.dataMask := Fill(ccx.busBytes, 1.U).asBools
  
  val subBus = io.bus.r.bits.data.asTypeOf(Vec(ccx.busBytes / ccx.xLenBytes, UInt(ccx.xLen.W)))
  val subBusSelect = io.physicalAddr(log2Ceil(ccx.busBytes) - 1, ccx.xLenBytesLog2)
  io.readData := subBus(subBusSelect).asTypeOf(io.readData.cloneType)


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
    
      assume(requestSent, "Read result returned before AX completed")

      io.bus.r.ready := true.B
      requestSent := false.B
      
      io.cacheReq.valid := true.B
      
      when(io.cacheReq.ready) {
        io.bus.r.ready := true.B
        io.cplt := true.B
      }
      /*
      victimWayIdxIncrement := true.B
      */
    }
  }
}
