package armleocpu


import chisel3._
import chisel3.util._
import armleocpu.busConst._


class BRAM(
  val sizeInWords: Int, // InBytes
  val busBytes: Int,
  val memoryFile: MemoryFile
)(implicit ccx:CCXParams) extends CCXModule {
  

  /**************************************************************************/
  /* parameter checking                                                     */
  /**************************************************************************/
  require(isPow2(sizeInWords))
  def isAddressInside(addr:UInt):Bool = {
    return (addr < (sizeInWords * busBytes).U)
  }

  /**************************************************************************/
  /*  IO and parameter checking                                             */
  /**************************************************************************/
  val io = IO(Flipped(new ReadWriteBus()(
    new BusParams(
    addrWidth = log2Ceil(sizeInWords * busBytes),
    idWidth = 0,
    lenWidth = 8,
    busBytes = busBytes
  ))))


  /**************************************************************************/
  /*  Assertions                                                            */
  /**************************************************************************/
  // checkStableRecord(io.r)
  // TODO: Add the stability check
  when(io.ar.valid) {
    assume((io.ar.bits.addr & (busBytes - 1).U) === 0.U)
  }

  when(io.aw.valid) {
    assume((io.aw.bits.addr & (busBytes - 1).U) === 0.U)
  }

  //assume(io.ar.bits.size === (log2Ceil(ccx.busBytes).U))
  //assume(io.aw.bits.size === (log2Ceil(ccx.busBytes).U))


  /**************************************************************************/
  /*                                                                        */
  /*  Memory                                                                */
  /*                                                                        */
  /**************************************************************************/

  val addr = Wire(UInt(log2Ceil(sizeInWords * busBytes).W))
  addr := io.ar.bits.addr.asUInt
  val idx = addr / busBytes.U
  val read = WireDefault(false.B)
  val write = WireDefault(false.B)

  val memory =
    if (memoryFile.path != "")  SRAM.masked(sizeInWords, Vec(busBytes, UInt(8.W)), 0, 0, 1, memoryFile)
    else                        SRAM.masked(sizeInWords, Vec(busBytes, UInt(8.W)), 0, 0, 1)
  memory.readwritePorts(0).address    := idx
  memory.readwritePorts(0).mask.get   := io.w.bits.strb.asBools
  memory.readwritePorts(0).enable     := write || read
  memory.readwritePorts(0).isWrite    := write
  memory.readwritePorts(0).writeData  := io.w.bits.data.asTypeOf(memory.readwritePorts(0).writeData)
  

  /**************************************************************************/
  /*                                                                        */
  /*  State machine                                                         */
  /*                                                                        */
  /**************************************************************************/
  val STATE_IDLE = 0.U; // Accepts read/write address
  val STATE_WRITE = 1.U; // Accepts write
  val STATE_WRITE_RESPONSE = 2.U; // Sends write response
  val STATE_READ = 3.U; // Read cycle

  val state = RegInit(0.U(2.W))

  // Assumes io.ar is the same type as io.aw
  // Keeps the request address
  val axrequest = Reg(Output(io.aw.bits.cloneType))

  val burst_remaining = Reg(UInt(9.W)) // One more than axlen


  // Keeps the response we intent to return
  val resp = Reg(io.r.bits.resp.cloneType)


  /**************************************************************************/
  /*                                                                        */
  /*  Default logic output                                                  */
  /*                                                                        */
  /**************************************************************************/
  
  io.r.valid := false.B
  io.b.valid := false.B
  io.r.bits := DontCare
  io.b.bits := DontCare

  io.aw.ready := false.B
  io.ar.ready := false.B
  io.w.ready := false.B
  io.r.bits.resp := resp
  io.b.bits.resp := resp
  io.r.bits.last := burst_remaining === 0.U


  /**************************************************************************/
  /*                                                                        */
  /*  Address calculation logic                                             */
  /*                                                                        */
  /**************************************************************************/
  // Signals
  //val wrap_mask = Wire(io.aw.addr.cloneType)
  //val increment = (1.U << axrequest.size);
  val increment = busBytes.U
  val incremented_addr = (axrequest.addr.asUInt + increment).asUInt
  // wrap_mask := (axrequest.len << 2.U) | "b11".U;


  io.r.bits.resp := resp
  io.r.bits.data := memory.readwritePorts(0).readData.asTypeOf(io.r.bits.data)
  
  /**************************************************************************/
  /*                                                                        */
  /*  Main state machine                                                    */
  /*                                                                        */
  /**************************************************************************/
  when(state === STATE_IDLE) {
    when(io.aw.valid) {
      /**************************************************************************/
      /*                                                                        */
      /*  Start of write operation                                              */
      /*                                                                        */
      /**************************************************************************/
      log(cf"WRITE ADDR: 0x${io.aw.bits.addr}%x, len: 0x${io.aw.bits.len}%x\n")

      // Retain request data that we will need later
      axrequest := io.aw.bits
      resp := Mux(isAddressInside(io.aw.bits.addr.asUInt), OKAY, DECERR)
      burst_remaining := io.aw.bits.len

      state := STATE_WRITE

      io.aw.ready := true.B
    } .elsewhen(io.ar.valid) {
      /**************************************************************************/
      /*                                                                        */
      /*  Start of read operation                                               */
      /*                                                                        */
      /**************************************************************************/
      log(cf"READ ADDR: 0x${io.ar.bits.addr}%x, len: 0x${io.ar.bits.len}%x")

      // We set the memory addr to initiate the request for read
      addr := io.ar.bits.addr.asUInt
      axrequest := io.ar.bits
      resp := Mux(isAddressInside(io.ar.bits.addr.asUInt), OKAY, DECERR)
      burst_remaining := io.ar.bits.len
      
      state := STATE_READ
      read := true.B

      io.ar.ready := true.B
    }
  } .elsewhen(state === STATE_READ) {
    /**************************************************************************/
    /*                                                                        */
    /*  One beat of read operation                                            */
    /*                                                                        */
    /**************************************************************************/
    
    io.r.valid := true.B
  
    addr := axrequest.addr.asUInt
    read := true.B

    when(io.r.ready) {
      axrequest.addr := incremented_addr;
      addr := incremented_addr.asUInt
      burst_remaining := burst_remaining - 1.U;
      resp := Mux(isAddressInside(incremented_addr.asUInt), OKAY, DECERR)

      
      log(cf"READ BEAT: 0x${axrequest.addr}%x, idx: 0x${idx}%x, data: 0x${io.r.bits.data}%x, resp: 0x${io.r.bits.resp}%x len: 0x${burst_remaining}%x, last: 0x${io.r.bits.last}%x")
      when(io.r.bits.last) {
        state := STATE_IDLE
        read := false.B
      }
    }
  } .elsewhen(state === STATE_WRITE) {
    /**************************************************************************/
    /*                                                                        */
    /*  Write operation                                                       */
    /*                                                                        */
    /**************************************************************************/
    


    addr := axrequest.addr.asUInt
    io.w.ready := true.B

    when(io.w.valid) {
      log(cf"WRITE BEAT: 0x${axrequest.addr}%x, strb: 0x${io.w.bits.strb}%x, data: 0x${io.w.bits.data}%x, idx: 0x${idx}%x, len: 0x${burst_remaining}%x, last: 0x${io.w.bits.last}%x")
      // Calculate next address and decrement the remaining burst counter
      axrequest.addr := incremented_addr;
      burst_remaining := burst_remaining - 1.U;
      

      write := true.B
      when(io.w.bits.last) {
        // Transition to write response if done
        state := STATE_WRITE_RESPONSE
        assume(burst_remaining === 0.U, "io.w.bits.last on not last request")
      } .otherwise {
        resp := Mux(isAddressInside(incremented_addr.asUInt), OKAY, DECERR)
      }
    }

    
  } .elsewhen(state === STATE_WRITE_RESPONSE) {
    /**************************************************************************/
    /*                                                                        */
    /*  Write response                                                        */
    /*                                                                        */
    /**************************************************************************/
    io.b.valid := true.B
    when(io.b.ready) {
      state := STATE_IDLE
    }
  }
}



// TODO: Write a generalist module synthesis/generator

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


import chisel3.stage._
object BRAMGenerator extends App {
  // Temorary disable memory configs as yosys does not know what to do with them
  // (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  
  implicit val ccx:CCXParams = new CCXParams;

  
  ChiselStage.emitSystemVerilogFile(
      new BRAM(
      busBytes = 2,
      sizeInWords = 2,
      memoryFile = new HexMemoryFile("")
    ),
      Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
      Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}



