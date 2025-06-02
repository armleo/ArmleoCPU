package armleocpu


import chisel3._
import chisel3.util._
import armleocpu.bus_const_t._




class BRAM(
  val sizeInWords:Int, // InBytes
  val baseAddr:UInt,

  val memoryFile: MemoryFile,
  val ccx: CCXParams
) extends CCXModule(ccx = ccx) {

  /**************************************************************************/
  /*  IO and parameter checking                                             */
  /**************************************************************************/
  val size = sizeInWords * ccx.busBytes
  require(((baseAddr.litValue) % size) == 0)
  require(size % ccx.busBytes == 0)

  def isAddressInside(addr:UInt):Bool = {
    return (addr >= baseAddr) && (addr < baseAddr + size.U)
  }


  val io = IO(Flipped(new dbus_t(ccx)))


  /**************************************************************************/
  /*  Assertions                                                            */
  /**************************************************************************/
  // checkStableRecord(io.r)
  // TODO: Check that only relevant records change for R bus

  when(io.ax.valid) {
    assume((io.ax.bits.addr & (ccx.busBytes - 1).S) === 0.S)
  }


  /**************************************************************************/
  /*                                                                        */
  /*  Default logic output                                                  */
  /*                                                                        */
  /**************************************************************************/
  
  io.ax.ready := false.B
  io.r.valid := false.B

  /**************************************************************************/
  /*                                                                        */
  /*  Memory                                                                */
  /*                                                                        */
  /**************************************************************************/

  val memory = if (memoryFile.path != "") SRAM.masked(sizeInWords, Vec(ccx.busBytes, UInt(8.W)), 0, 0, 1, memoryFile) else SRAM.masked(sizeInWords, Vec(ccx.busBytes, UInt(8.W)), 0, 0, 1)
  memory.readwritePorts(0).address    := (io.ax.bits.addr.asUInt % size.asUInt) / ccx.busBytes.U
  memory.readwritePorts(0).mask.get   := io.ax.bits.strb.asBools
  memory.readwritePorts(0).enable     := io.ax.valid && io.ax.ready && ((io.ax.bits.op === OP_READ) || (io.ax.bits.op === OP_WRITE)) && isAddressInside(io.ax.bits.addr.asUInt)
  memory.readwritePorts(0).isWrite    := io.ax.bits.op === OP_WRITE
  memory.readwritePorts(0).writeData  := io.ax.bits.data.asTypeOf(memory.readwritePorts(0).writeData)
  

  val resp   = RegInit(0.U.asTypeOf(io.r.bits.resp))
  val r_valid = RegInit(false.B)

  io.r.bits.resp := resp
  io.r.bits.data := memory.readwritePorts(0).readData.asTypeOf(io.r.bits.data)
  io.r.valid := r_valid

  when(!io.r.valid) {
    when(io.ax.valid) {
      io.ax.ready := true.B
      resp := Mux(isAddressInside(io.ax.bits.addr.asUInt), OKAY, DECERR)
      when(io.ax.bits.op === OP_READ) {
        log(cf"READ ADDR: 0x${io.ax.bits.addr}%x, isAddressInside: ${isAddressInside(io.ax.bits.addr.asUInt)}, memory_offset: 0x${memory.readwritePorts(0).address}%x")
        r_valid := true.B
      } .elsewhen(io.ax.bits.op === OP_WRITE) {
        log(cf"WRITE ADDR: 0x${io.ax.bits.addr}%x, strb: 0x${io.ax.bits.strb}%x, wdata: 0x${io.ax.bits.data}")
        r_valid := true.B
      } .otherwise {
        resp := SLVERR
        r_valid := true.B
      }
    }
  } .otherwise {
    when(io.r.ready) {
      r_valid := false.B
      log(cf"BEAT: data: 0x${io.r.bits.data}%x, resp: 0x${io.r.bits.resp}%x")
    }
  }
}

      



import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


import chisel3.stage._
object BootRAMGenerator extends App {
  // Temorary disable memory configs as yosys does not know what to do with them
  // (new ChiselStage).execute(Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Core)))
  val bram = new BRAM(
    ccx = new CCXParams,
    sizeInWords = 2 * 1024, // InBytes
    baseAddr ="h40000000".asUInt,
    memoryFile = new HexMemoryFile("")
  )
  ChiselStage.emitSystemVerilogFile(
      bram,
      Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", "generated_vlog/", "--target", "verilog") ++ args,
      Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
  )
  
}



