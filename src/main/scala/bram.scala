package armleocpu


import chisel3._
import chisel3.util._
import armleocpu.bus_resp_t._




class BRAM(val c: CoreParams = new CoreParams,
  val random_delay:Boolean = true,
  val sizeInWords:Int = 2 * 1024, // InBytes
  val baseAddr:UInt = "h40000000".asUInt
) extends Module {

  /**************************************************************************/
  /*  IO and parameter checking                                             */
  /**************************************************************************/
  val size = sizeInWords * c.bp.data_bytes
  require(((baseAddr.litValue) % size) == 0)
  require(size % c.bp.data_bytes == 0)

  def isAddressInside(addr:UInt):Bool = {
    return (addr >= baseAddr) && (addr < baseAddr + size.U)
  }


  val io = IO(Flipped(new dbus_t(c)))


  /**************************************************************************/
  /*  Assertions                                                            */
  /**************************************************************************/

  checkStableRecord(io.aw)
  checkStableRecord(io.ar)
  checkStableRecord(io.r)
  checkStableRecord(io.w)
  checkStableRecord(io.b)


  when(io.aw.valid) {
    assert(io.aw.bits.size === (log2Up(c.bp.data_bytes).U))
    //assert(io.aw.bits.len === 0.U)
    assert((io.aw.bits.addr & (c.bp.data_bytes - 1).S) === 0.S)
  }

  when(io.ar.valid) {
    assert(io.ar.bits.size === (log2Up(c.bp.data_bytes).U))
    //assert(io.ar.bits.len === 0.U)
    assert((io.ar.bits.addr & (c.bp.data_bytes - 1).S) === 0.S)
  }

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
  /*  Address calculation logic                                             */
  /*                                                                        */
  /**************************************************************************/
  // Signals
  //val wrap_mask = Wire(io.aw.addr.cloneType)
  val increment = (1.U << axrequest.size);
  val incremented_addr = (axrequest.addr.asUInt + increment).asSInt
  // wrap_mask := (axrequest.len << 2.U) | "b11".U;

  
  /**************************************************************************/
  /*                                                                        */
  /*  Default logic output                                                  */
  /*                                                                        */
  /**************************************************************************/
  
  io.r.valid := false.B
  io.b.valid := false.B
  io.aw.ready := false.B
  io.ar.ready := false.B
  io.w.ready := false.B
  io.r.bits.resp := resp
  io.b.bits.resp := resp
  io.r.bits.last := burst_remaining === 0.U


  /**************************************************************************/
  /*                                                                        */
  /*  Memory address / offet calculation                                    */
  /*                                                                        */
  /**************************************************************************/
  
  val memory_addr = Wire(io.ar.bits.addr.asUInt.cloneType)
  memory_addr := io.ar.bits.addr.asUInt

  // Calculate the selection address from meory
  val memory_offset = (memory_addr % size.asUInt) / c.bp.data_bytes.U



  /**************************************************************************/
  /*                                                                        */
  /*  Memory                                                                */
  /*                                                                        */
  /**************************************************************************/
  
  // Use per byte memory instance, as we want to have per-byte write enable
  val memory = SyncReadMem(size, Vec(c.bp.data_bytes, UInt(8.W)))

  val memory_write = io.w.valid && io.w.ready
  val memory_read = WireDefault(false.B)
  val memory_rdata = memory.readWrite(
    /*idx = */memory_offset,
    /*writeData = */io.w.bits.data.asTypeOf(Vec(c.bp.data_bytes, UInt(8.W))),
    /*mask = */io.w.bits.strb.asBools,
    /*en = */memory_write || memory_read,
    /*isWrite = */memory_write)
  
  // We can just directly connect memory read data
  io.r.bits.data := memory_rdata.asTypeOf(io.r.bits.data)



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
      printf(cf"BRAM: Start write addr: 0x${io.aw.bits.addr}%x, len: 0x${io.aw.bits.len}%x\n")

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
      printf(cf"BRAM: Start read addr: 0x${io.ar.bits.addr}%x, len: 0x${io.ar.bits.len}%x\n")

      // We set the memory addr to initiate the request for read
      memory_addr := io.ar.bits.addr.asUInt
      axrequest := io.ar.bits
      resp := Mux(isAddressInside(io.ar.bits.addr.asUInt), OKAY, DECERR)
      burst_remaining := io.ar.bits.len
      
      state := STATE_READ
      memory_read := true.B

      io.ar.ready := true.B
    }
  } .elsewhen(state === STATE_READ) {
    /**************************************************************************/
    /*                                                                        */
    /*  One beat of read operation                                            */
    /*                                                                        */
    /**************************************************************************/
    
    io.r.valid := true.B
    memory_read := true.B
    //%m %T
    // No combinational logic needed here. Everything is already wired correctly

    memory_addr := axrequest.addr.asUInt

    when(io.r.ready) {
      axrequest.addr := incremented_addr;
      memory_addr := incremented_addr.asUInt
      burst_remaining := burst_remaining - 1.U;
      resp := Mux(isAddressInside(incremented_addr.asUInt), OKAY, DECERR)

      printf(cf"BRAM: Read beat: 0x${axrequest.addr}%x, memory_offset: 0x${memory_offset}%x, data: 0x${io.r.bits.data}%x, resp: 0x${io.r.bits.resp}%x len: 0x${burst_remaining}%x, last: 0x${io.r.bits.last}%x\n")
      when(io.r.bits.last) {
        state := STATE_IDLE
      }
    }
  } .elsewhen(state === STATE_WRITE) {
    /**************************************************************************/
    /*                                                                        */
    /*  Write operation                                                       */
    /*                                                                        */
    /**************************************************************************/
    


    memory_addr := axrequest.addr.asUInt
    // Response to request
    io.w.ready := true.B

    when(io.w.valid) {
      printf(cf"BRAM: Write beat: 0x${axrequest.addr}%x, strb: 0x${io.w.bits.strb}%x, data: 0x${io.w.bits.data}%x, memory_offset: 0x${memory_offset}%x, len: 0x${burst_remaining}%x, last: 0x${io.w.bits.last}\n")
      // Calculate next address and decrement the remaining burst counter
      axrequest.addr := incremented_addr;
      burst_remaining := burst_remaining - 1.U;
      
      when(io.w.bits.last) {
        // Transition to write response if done
        state := STATE_WRITE_RESPONSE
        assert(burst_remaining === 0.U, "io.w.bits.last on not last request")
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



import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


import chisel3.stage._
object BootRAMGenerator extends App {
  // Temorary disable memory configs as yosys does not know what to do with them
  // (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  ChiselStage.emitSystemVerilogFile(
    new BRAM(),
      Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
      Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}



