package armleocpu


import chisel3._
import chisel3.util._




class BootRAM(val c: CoreParams = new CoreParams,
  val random_delay:Boolean = true,
  val size:Int = 16 * 1024,
  val baseAddr:Int = 0x4000_0000
) extends Module {
  require((baseAddr % size) == 0)
  require(size % c.bp.data_bytes == 0)

  def isAddressInside(addr:UInt):Bool = {
    return (addr >= baseAddr.U) && (addr < baseAddr.U + size.U)
  }

  

  val io = IO(Flipped(new dbus_t(c)))

  val backstorage    = Seq.tabulate(c.bp.data_bytes) {
    f:Int => SyncReadMem(size, UInt(8.W))
  }

  val STATE_IDLE = 0.U; // Accepts read/write address
  val STATE_WRITE = 1.U; // Accepts write
  val STATE_WRITE_RESPONSE = 2.U; // Sends write response
  val STATE_READ = 3.U; // Read cycle

  val state = RegInit(0.U(2.W))

  // Assumes io.ar is the same type as io.aw
  val axrequest = Reg(Output(io.aw.cloneType))

  def backstorage_offset(addr:UInt): UInt = {
    (addr % size.asUInt) >> log2Down(c.bp.data_bytes)
  }

  val resp = Reg(io.r.resp.cloneType)

  require((io.aw.len.getWidth + 1) == 9)
  val burst_remaining = Reg(UInt(9.W)) // One more than axlen

  // Signals
  //val wrap_mask = Wire(io.aw.addr.cloneType)
  val increment = (1.U(io.aw.addr.getWidth.W) << (axrequest.size));
  val incremented_addr = (axrequest.addr.asUInt + increment).asSInt
  // wrap_mask := (axrequest.len << 2.U) | "b11".U;

  
  
  io.r.last := burst_remaining === 0.U;
  io.r.valid := false.B
  io.b.valid := false.B
  io.aw.ready := false.B
  io.ar.ready := false.B
  io.w.ready := false.B
  io.r.resp := resp
  io.b.resp := resp


  val read_addr = Wire(backstorage_offset(io.ar.addr.asUInt).cloneType)
  read_addr := backstorage_offset(io.ar.addr.asUInt)

  val readdata = Wire(Vec(c.bp.data_bytes, UInt(8.W)))

  for(bytenum <- 0 until c.bp.data_bytes) {
    readdata(bytenum) := (backstorage(bytenum)(backstorage_offset(read_addr.asUInt)))
  }

  io.r.data := readdata.asTypeOf(io.r.data)

  when(state === STATE_IDLE) {
    when(io.aw.valid) {
      state := STATE_WRITE
      io.aw.ready := true.B

      axrequest := io.aw
      resp := isAddressInside(io.aw.addr.asUInt)
      
    } .elsewhen(io.ar.valid) {
      state := STATE_READ
      io.ar.ready := true.B
      read_addr := backstorage_offset(io.ar.addr.asUInt)
      axrequest := io.ar
      resp := isAddressInside(io.ar.addr.asUInt)
    }
  } .elsewhen(state === STATE_READ) {
    
    when(io.r.ready) {
      
      axrequest.addr := incremented_addr;
      read_addr := backstorage_offset(incremented_addr.asUInt)
      burst_remaining := burst_remaining - 1.U;
      when(io.r.last) {
        state := STATE_IDLE
      }

      

      resp := isAddressInside(axrequest.addr.asUInt)
    }
  } .elsewhen(state === STATE_WRITE) {
    io.w.ready := true.B
    when(io.w.valid) {
      for(bytenum <- 0 until c.bp.data_bytes) {
        backstorage(bytenum)(backstorage_offset(axrequest.addr.asUInt)) := io.w.data.asTypeOf(readdata)(bytenum)
      }
      axrequest.addr := incremented_addr;
      burst_remaining := burst_remaining - 1.U;
      when(io.w.last) {
        state := STATE_WRITE_RESPONSE
        assert(burst_remaining === 0.U, "io.w.last on not last request")
      }

      // FIXME: Write the data actually

      assert(burst_remaining === 0.U, "We currently dont support writes for more than one request")
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
    new BootRAM(),
      Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
      Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}



