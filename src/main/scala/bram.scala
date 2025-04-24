package armleocpu


import chisel3._
import chisel3.util._




class BRAM(val c: CoreParams = new CoreParams,
  val random_delay:Boolean = true,
  val size:Int = 16 * 1024, // InBytes
  val baseAddr:UInt = "h40000000".asUInt
) extends Module {
  require(((baseAddr.litValue) % size) == 0)
  require(size % c.bp.data_bytes == 0)

  def isAddressInside(addr:UInt):Bool = {
    return (addr >= baseAddr) && (addr < baseAddr + size.U)
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

  val resp = Reg(io.r.resp.cloneType)

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


  val back_storage_addr = Wire(io.ar.addr.asUInt.cloneType)
  back_storage_addr := io.ar.addr.asUInt
  val back_storage_offset = (back_storage_addr % size.asUInt) >> log2Down(c.bp.data_bytes)

  val readdata = Wire(Vec(c.bp.data_bytes, UInt(8.W)))
  val backstorage_write = Wire(Bool())
  
  backstorage_write := false.B
  for(bytenum <- 0 until c.bp.data_bytes) {
    readdata(bytenum) := 0.U
    val rdwrPort = backstorage(bytenum)(back_storage_offset)
    when(!backstorage_write) {
      readdata(bytenum) := rdwrPort
    }.otherwise {
        rdwrPort := io.w.data.asTypeOf(readdata)(bytenum)
        assert(io.w.strb(bytenum))
    }
  }

  io.r.data := readdata.asTypeOf(io.r.data)

  when(state === STATE_IDLE) {
    when(io.aw.valid) {
      state := STATE_WRITE
      io.aw.ready := true.B

      axrequest := io.aw
      resp := isAddressInside(io.aw.addr.asUInt)
      burst_remaining := io.aw.len
      assert(io.aw.size === (log2Up(c.bp.data_bytes).U))
      assert(io.aw.len === 0.U)
    } .elsewhen(io.ar.valid) {
      state := STATE_READ
      io.ar.ready := true.B
      back_storage_addr := io.ar.addr.asUInt
      axrequest := io.ar
      resp := isAddressInside(io.ar.addr.asUInt)
      burst_remaining := io.ar.len
      assert(io.ar.size === (log2Up(c.bp.data_bytes).U))
      assert(io.ar.len === 0.U)
    }
  } .elsewhen(state === STATE_READ) {
    io.r.valid := true.B
    when(io.r.ready) {
      
      axrequest.addr := incremented_addr;
      back_storage_addr := incremented_addr.asUInt
      burst_remaining := burst_remaining - 1.U;
      when(io.r.last) {
        state := STATE_IDLE
      }
      resp := isAddressInside(incremented_addr.asUInt)
    }
  } .elsewhen(state === STATE_WRITE) {
    back_storage_addr := axrequest.addr.asUInt
    io.w.ready := true.B
    when(io.w.valid) {
      backstorage_write := true.B
      
      
      axrequest.addr := incremented_addr;
      burst_remaining := burst_remaining - 1.U;
      when(io.w.last) {
        state := STATE_WRITE_RESPONSE
        assert(burst_remaining === 0.U, "io.w.last on not last request")
      }

      // FIXME: Write the data actually

      //assert(burst_remaining === 0.U, "We currently dont support writes for more than one request")
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



