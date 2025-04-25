package armleocpu


import chisel3._
import chisel3.util._




class BRAM(val c: CoreParams = new CoreParams,
  val random_delay:Boolean = true,
  val size:Int = 16 * 1024, // InBytes
  val baseAddr:UInt = "h40000000".asUInt
) extends Module {

  /**************************************************************************/
  /*  IO and parameter checking                                             */
  /**************************************************************************/
  require(((baseAddr.litValue) % size) == 0)
  require(size % c.bp.data_bytes == 0)

  def isAddressInside(addr:UInt):Bool = {
    return (addr >= baseAddr) && (addr < baseAddr + size.U)
  }


  val io = IO(Flipped(new dbus_t(c)))


  /**************************************************************************/
  /*  Assertions                                             */
  /**************************************************************************/

  when(io.aw.valid) {
    assert(io.aw.size === (log2Up(c.bp.data_bytes).U))
    assert(io.aw.len === 0.U)
    assert((io.aw.addr & (c.bp.data_bytes - 1).S) === 0.S)

  }
  when(io.ar.valid) {
    assert(io.ar.size === (log2Up(c.bp.data_bytes).U))
    assert(io.ar.len === 0.U)
    assert((io.ar.addr & (c.bp.data_bytes - 1).S) === 0.S)
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
  val axrequest = Reg(Output(io.aw.cloneType))

  val burst_remaining = Reg(UInt(9.W)) // One more than axlen


  // Keeps the response we intent to return
  val resp = Reg(io.r.resp.cloneType)




  /**************************************************************************/
  /*                                                                        */
  /*  Address calculation logic                                             */
  /*                                                                        */
  /**************************************************************************/
  // Signals
  //val wrap_mask = Wire(io.aw.addr.cloneType)
  val increment = (1.U(io.aw.addr.getWidth.W) << (axrequest.size));
  val incremented_addr = (axrequest.addr.asUInt + increment).asSInt
  // wrap_mask := (axrequest.len << 2.U) | "b11".U;

  
  /**************************************************************************/
  /*                                                                        */
  /*  Default logic output                                                  */
  /*                                                                        */
  /**************************************************************************/
  io.r.last := burst_remaining === 0.U;
  io.r.valid := false.B
  io.b.valid := false.B
  io.aw.ready := false.B
  io.ar.ready := false.B
  io.w.ready := false.B
  io.r.resp := resp
  io.b.resp := resp



  /**************************************************************************/
  /*                                                                        */
  /*  Memory address / offet calculation                                    */
  /*                                                                        */
  /**************************************************************************/
  

  val memory_addr = Wire(io.ar.addr.asUInt.cloneType)
  memory_addr := io.ar.addr.asUInt

  // Calculate the selection address from meory
  val memory_offset = (memory_addr % size.asUInt) >> log2Down(c.bp.data_bytes)



  /**************************************************************************/
  /*                                                                        */
  /*  Memory                                                                */
  /*                                                                        */
  /**************************************************************************/
  
  val memory    = Seq.tabulate(c.bp.data_bytes) {
    f:Int => SyncReadMem(size, UInt(8.W))
  }


  // Retains the memory read data
  val memory_rdata = Wire(Vec(c.bp.data_bytes, UInt(8.W)))

  // Write request to memory
  val memory_write = io.w.valid && io.w.ready

  // We create a separate memory for each byte to use independend masks
  for(bytenum <- 0 until c.bp.data_bytes) {
    memory_rdata(bytenum) := 0.U
    // Create read-write port. Otherwise a two port memory will be created
    val rdwrPort = memory(bytenum)(memory_offset)
    when(!memory_write) {
      // Read data only if there is no write. Otherwise we will mess up the data AND we will need to use a two port memory.
      memory_rdata(bytenum) := rdwrPort
    }.otherwise {
        rdwrPort := io.w.data.asTypeOf(memory_rdata)(bytenum)


        // For now we assume that all data is written at the same time
        assert(io.w.strb(bytenum))
    }
  }
  // We can just directly connect memory read data
  io.r.data := memory_rdata.asTypeOf(io.r.data)



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
      // Retain request data that we will need later
      axrequest := io.aw
      resp := isAddressInside(io.aw.addr.asUInt)
      burst_remaining := io.aw.len

      // Transition to write state
      state := STATE_WRITE

      io.aw.ready := true.B
      
    } .elsewhen(io.ar.valid) {
      /**************************************************************************/
      /*                                                                        */
      /*  Start of read operation                                               */
      /*                                                                        */
      /**************************************************************************/
      // We set the memory addr to initiate the request for read
      memory_addr := io.ar.addr.asUInt
      axrequest := io.ar
      resp := isAddressInside(io.ar.addr.asUInt)
      burst_remaining := io.ar.len
      
      state := STATE_READ

      io.ar.ready := true.B
    }
  } .elsewhen(state === STATE_READ) {
    /**************************************************************************/
    /*                                                                        */
    /*  One beat of read operation                                            */
    /*                                                                        */
    /**************************************************************************/
    io.r.valid := true.B

    // No combinational logic needed here. Everything is already wired correctly
    
    when(io.r.ready) {
      axrequest.addr := incremented_addr;
      memory_addr := incremented_addr.asUInt
      burst_remaining := burst_remaining - 1.U;
      resp := isAddressInside(incremented_addr.asUInt)

      when(io.r.last) {
        state := STATE_IDLE
      }
    }
  } .elsewhen(state === STATE_WRITE) {
    memory_addr := axrequest.addr.asUInt
    // Response to request
    io.w.ready := true.B

    when(io.w.valid) {
      // Calculate next address and decrement the remaining burst counter
      axrequest.addr := incremented_addr;
      burst_remaining := burst_remaining - 1.U;
      when(io.w.last) {
        // Transition to write response if done
        state := STATE_WRITE_RESPONSE
        assert(burst_remaining === 0.U, "io.w.last on not last request")
      }
    }

    
  } .elsewhen(state === STATE_WRITE_RESPONSE) {
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



