package armleocpu



import chisel3._
import chisel3.util._
import chisel3.util.random._


import armleocpu.busConst._
import chisel3.reflect.DataMirror
/*

class BRAMExerciserIO extends Bundle {
  val success = Output(Bool())
  val done = Output(Bool())
  val coverage = UInt(32.W)
}

class BRAMExerciser(
  val seed:BigInt = 1,
  val baseAddr:UInt = "h40000000".asUInt,
  val bramWords: Int = 2048,
  val allowedBramWords: Int = 2048,
  val numRepeats: Int = 2000,
  val maxLen: Int = 4,
  dut: BRAM) (implicit val ccx: CCXParams) extends Module {
  /**************************************************************************/
  /*                                                                        */
  /*  Shared stuff                                                          */
  /*                                                                        */
  /**************************************************************************/
  println(s"BRAMExerciser: baseAddr: 0x${baseAddr.litValue.toString(16)}, bramWords = ${bramWords}, numRepeats = ${numRepeats}")
  val io = IO(new BRAMExerciserIO)
  val dbus = IO(dut.io.cloneType)

  val coverage = RegInit(0.U(32.W))
  io.coverage := coverage

  val failed = RegInit(false.B)
  val repeat = RegInit(0.U(log2Ceil(numRepeats + 1).W))


  // Defaults
  io.success := false.B
  
  val size = bramWords * ccx.busBytes

  /**************************************************************************/
  /*                                                                        */
  /*  Write stress tester state                                             */
  /*                                                                        */
  /**************************************************************************/
  
  val ax_random_stall_module = Module(new DecoupledIORandomStall(dbus.req.bits, Some(seed + 1)))
  val req = Wire(dbus.req.cloneType)
  ax_random_stall_module.in <> req
  ax_random_stall_module.out <> dbus.req
  req.valid := false.B

  val r_random_stall_module = Module(new DecoupledIORandomStall(dbus.resp.bits, Some(seed + 3)))
  val r = Wire(dbus.resp.cloneType)
  r_random_stall_module.in <> dbus.resp
  r_random_stall_module.out <> r
  r.ready := false.B



  // 64 bits is enough for most cases. Dont want to make it depended on bram words value
  val idx = (FibonacciLFSR.maxPeriod(64, reduction = XNOR, seed = Some(seed + 4), increment = ax_random_stall_module.increment) % (allowedBramWords).U)
  val addr = baseAddr + (idx * ccx.busBytes.U)


  def isAddressInside(addr:UInt):Bool = {
    return (addr >= baseAddr) && (addr < baseAddr + size.U)
  }


  val mirror = SRAM.masked(bramWords, Vec(ccx.busBytes, UInt(8.W)), 0, 0, 1)
  mirror.readwritePorts(0).address    := (req.bits.addr.asUInt % size.asUInt) / ccx.busBytes.U
  mirror.readwritePorts(0).mask.get   := req.bits.strb.asBools
  mirror.readwritePorts(0).enable     := req.valid && req.ready && ((req.bits.op === 2.U) || (req.bits.op === 1.U)) && isAddressInside(req.bits.addr.asUInt)
  mirror.readwritePorts(0).isWrite    := req.bits.op === 2.U
  mirror.readwritePorts(0).writeData  := req.bits.data.asTypeOf(mirror.readwritePorts(0).writeData)
  
  val valid = SRAM.masked(bramWords, Vec(ccx.busBytes, Bool()), 0, 0, 1)
  valid.readwritePorts(0).address    := (req.bits.addr.asUInt % size.asUInt) / ccx.busBytes.U
  valid.readwritePorts(0).mask.get   := req.bits.strb.asBools
  valid.readwritePorts(0).enable     := req.valid && req.ready && ((req.bits.op === 2.U) || (req.bits.op === 1.U)) && isAddressInside(req.bits.addr.asUInt)
  valid.readwritePorts(0).isWrite    := req.bits.op === 2.U
  valid.readwritePorts(0).writeData  := VecInit(Fill(ccx.busBytes, 1.U).asBools)

  /**************************************************************************/
  /*                                                                        */
  /*  Write bus IO assignments                                              */
  /*                                                                        */
  /**************************************************************************/

  
  req.bits.addr := Cat(0.U(1.W), addr).asSInt
  req.bits.data  := FibonacciLFSR.maxPeriod(ccx.busBytes * 8, reduction = XNOR, seed = Some(seed + 6), increment = ax_random_stall_module.increment)
  req.bits.strb  := FibonacciLFSR.maxPeriod(req.bits.strb.getWidth, reduction = XNOR, seed = Some(seed + 7), increment = ax_random_stall_module.increment)
  req.bits.op    := Mux((FibonacciLFSR.maxPeriod(16, reduction = XNOR, seed = Some(seed + 8), increment = ax_random_stall_module.increment))(0).asBool, OP_READ, OP_WRITE)

  val s1_idx = Reg(idx.cloneType)
  val s1_bits = Reg(req.bits.cloneType)
  val s1_active = RegInit(false.B)
  
  when(s1_active) {
    when(r.valid) {
      assert(r.bits.resp === Mux((s1_bits.op === 1.U) || (s1_bits.op === 2.U), Mux(s1_idx < bramWords.U, OKAY, DECERR), SLVERR), "Incorrect response")
      r.ready := true.B
      for(bytenum <- 0 until ccx.busBytes) {
        when(valid.readwritePorts(0).readData(bytenum) && (s1_bits.op === 1.U)) {
          val datamatch = mirror.readwritePorts(0).readData(bytenum) === r.bits.data(bytenum)
          assert(datamatch)
          failed := failed || !(datamatch)
          coverage := coverage + 1.U
          
        }
      }
      repeat := repeat + 1.U
      s1_active := false.B
    }
  } .otherwise {
    when(repeat < numRepeats.U) {
      req.valid := true.B
      
    }
  }


  when(req.valid && req.ready) {
    s1_idx := idx
    s1_bits := req.bits
    s1_active := true.B
  }


  


  /**************************************************************************/
  /*                                                                        */
  /*  Completion logic                                                      */
  /*                                                                        */
  /**************************************************************************/
  io.done := false.B
  // Completion
  when(repeat >= numRepeats.U) {
    io.success := !failed
    io.done := true.B
  }

}


class BRAMTesterModule(val baseAddr:UInt = "h40000000".asUInt, val bramWords: Int = 2048, val numRepeats: Int = 2000) extends Module {
  val io = IO(new BRAMExerciserIO)

  val ccx = new CCXParams()
  val bram = Module(new BRAM(bramWords, baseAddr, memoryFile = new HexMemoryFile(""))(ccx))
  val exerciser = Module(new BRAMExerciser(
      seed = 10,
      baseAddr = baseAddr, bramWords = bramWords, allowedBramWords = bramWords,
      numRepeats = numRepeats,
      maxLen = 4,
      bram)(ccx))
  bram.io <> exerciser.dbus
  io <> exerciser.io
}

import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec


class BRAMSpec extends AnyFlatSpec {
  behavior of "BRAM"
  it should "Basic BRAM test" in {
    simulate("BasicBRAMTester", new BRAMTesterModule(bramWords = 16)) { harness =>
      for (i <- 0 to 100) {
        harness.clock.step(100)
        if (harness.io.done.peek().litValue == 1) {
          harness.io.success.expect(true.B)
        }
        println("100 cycles done")
      }
      
      harness.io.done.expect(true.B)
      harness.io.success.expect(true.B)
      

    }
  }
}

class BRAMStressSpec extends AnyFlatSpec {
  it should "BRAM Stress test" in {
    simulate("StressBRAMTester", new BRAMTesterModule()) { harness =>
      for (i <- 0 to 100) {
        harness.clock.step(100)
        if (harness.io.done.peek().litValue == 1) {
          harness.io.success.expect(true.B)
        }
        println("100 cycles done")
      }
      
      harness.io.done.expect(true.B)
      harness.io.success.expect(true.B)
      

    }
  }
}
*/