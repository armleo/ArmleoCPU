package armleocpu



import chisel3._
import chisel3.util._
import chisel3.util.random._


import armleocpu.bus_resp_t._
import chisel3.reflect.DataMirror


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
  dut: BRAM, ccx: CCXParams) extends Module {
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

  
  // TODO: Make it sync read mem
  // Mirror and validity tracker
  val mirror = Seq.tabulate(ccx.busBytes) {
    f:Int => SyncReadMem(bramWords, UInt(8.W))
  }
  val valid = Seq.tabulate(ccx.busBytes) {
    f:Int => SyncReadMem(bramWords, Bool())
  }

  val failed = RegInit(false.B)
  val w_repeat = RegInit(0.U(log2Ceil(numRepeats + 1).W))
  val r_repeat = RegInit(0.U(log2Ceil(numRepeats + 1).W))



  // Defaults
  io.success := false.B

  /**************************************************************************/
  /*                                                                        */
  /*  Write stress tester state                                             */
  /*                                                                        */
  /**************************************************************************/
  
  val ax_random_stall_module = Module(new DecoupledIORandomStall(dbus.ax.bits, Some(seed + 1)))
  val ax = Wire(dbus.ax.cloneType)
  ax_random_stall_module.in <> ax
  ax_random_stall_module.out <> dbus.ax
  ax.valid := false.B

  val b_random_stall_module = Module(new DecoupledIORandomStall(dbus.b.bits, Some(seed + 3)))
  val b = Wire(dbus.b.cloneType)
  b_random_stall_module.out <> b
  b_random_stall_module.in <> dbus.b
  b.ready := false.B



  // 64 bits is enough for most cases. Dont want to make it depended on bram words value
  val aw_idx = (FibonacciLFSR.maxPeriod(64, reduction = XNOR, seed = Some(seed + 4), increment = ax_random_stall_module.increment) % (allowedBramWords).U)
  val aw_addr = baseAddr + (aw_idx * ccx.busBytes.U)


  // Write state
  val w_state_init = 0.U(2.W)
  val w_state_addr = 1.U(2.W)
  val w_state_data = 2.U(2.W)
  val w_state_resp = 3.U(2.W)

  val w_state = RegInit(w_state_init)
  val w_beats = Reg(UInt(12.W))
  val w_idx   = Reg(UInt(64.W))


  /**************************************************************************/
  /*                                                                        */
  /*  Write bus IO assignments                                              */
  /*                                                                        */
  /**************************************************************************/

  aw.bits.addr := Cat(0.U(1.W), aw_addr).asSInt
  aw.bits.len  := FibonacciLFSR.maxPeriod(16, reduction = XNOR, seed = Some(seed + 5), increment = aw_random_stall_module.increment) % maxLen.U //Fixed 16 cycles because more is simply not needed
  aw.bits.size := (log2Ceil(ccx.busBytes)).U
  aw.bits.cache := 0.U
  //aw.bits.lock := false.B

  w.bits.data  := FibonacciLFSR.maxPeriod(ccx.busBytes * 8, reduction = XNOR, seed = Some(seed + 6), increment = w_random_stall_module.increment)
  w.bits.strb  := FibonacciLFSR.maxPeriod(w.bits.strb.getWidth, reduction = XNOR, seed = Some(seed + 7), increment = w_random_stall_module.increment)
  w.bits.last  := w_beats === 1.U



  /**************************************************************************/
  /*                                                                        */
  /*  WRITE FSM                                                             */
  /*                                                                        */
  /**************************************************************************/

  switch(w_state) {
    is(w_state_init) {
      when(w_repeat < numRepeats.U) {
        w_state := w_state_addr
      }
    }
    is(w_state_addr) {
      aw.valid := true.B
      
      when(aw.valid && aw.ready) {
        w_beats := aw.bits.len + 1.U
        w_state := w_state_data
        w_idx   := aw_idx
      }
    }
    is(w_state_data) {
      w.valid := true.B
      

      when(w.ready && w.valid) {
        for(bytenum <- 0 until ccx.busBytes) {
          when(w.bits.strb(bytenum)) {
            mirror(bytenum)(w_idx) := w.bits.data.asTypeOf(Vec(ccx.busBytes, UInt(8.W)))(bytenum)
            valid(bytenum)(w_idx) := true.B
          }
        }
        
        
        when(w.bits.last) {
          w_state := w_state_resp
        } .otherwise {
          w_idx := w_idx + 1.U
          w_beats := w_beats - 1.U
        }
      }
    }
    is(w_state_resp) {
      w_repeat := w_repeat + 1.U
      b.ready := true.B
      
      when(b.valid && b.ready) {
        assert(b.bits.resp === Mux(w_idx < bramWords.U, OKAY, DECERR), "Incorrect response for B")

        w_state := w_state_init
      }
    }
  }

  
  
  /**************************************************************************/
  /*                                                                        */
  /*  Read stress tester state                                              */
  /*                                                                        */
  /**************************************************************************/

    
  // Read state
  val r_state_init = 0.U
  val r_state_addr = 1.U
  val r_state_data = 2.U
  
  val r_state = RegInit(r_state_init)

  // Make sure that the read addr only changes when it was accepted
  val ar_random_stall_module = Module(new DecoupledIORandomStall(dbus.ar.bits))
  val ar = Wire(dbus.ar.cloneType)
  ar_random_stall_module.in <> ar
  ar_random_stall_module.out <> dbus.ar
  ar.valid := false.B

  // Make sure that the read addr only changes when it was accepted
  val r_random_stall_module = Module(new DecoupledIORandomStall(dbus.r.bits))
  val r = Wire(dbus.r.cloneType)
  r_random_stall_module.in <> dbus.r
  r_random_stall_module.out <> r
  r.ready := false.B


  val ar_idx  = (FibonacciLFSR.maxPeriod(64, reduction = XNOR, seed = Some(seed + 8), increment = ar_random_stall_module.increment) % (allowedBramWords).U)
  val r_beats = Reg(UInt(12.W))
  val r_idx   = Reg(UInt(64.W))

  // Keep the data from the bus so we can compare it when the result from sync read mem is available
  val check_r = RegNext(r.valid && r.ready)
  val check_r_bits_data = RegNext(r.bits.data)

  // Actual data from syncreadmems
  val mirrorread = Wire(Vec(ccx.busBytes, UInt(8.W)))
  val validread = Wire(Vec(ccx.busBytes, Bool()))
  for(bytenum <- 0 until ccx.busBytes) {
    mirrorread(bytenum) := mirror(bytenum)(r_idx)
    validread(bytenum) := valid(bytenum)(r_idx)
  }

  // Last cycle the r data beat came. Compare the data from bus in previous cycle to the mirror read result
  when(check_r) {
    for(bytenum <- 0 until ccx.busBytes) {
      when(validread(bytenum)) {
        val datamatch = check_r_bits_data.asTypeOf(Vec(ccx.busBytes, UInt(8.W)))(bytenum) === mirrorread(bytenum)
        assert(datamatch)
        failed := failed || !(datamatch)
        coverage := coverage + 1.U
      }
    }
  }
  
  /**************************************************************************/
  /*                                                                        */
  /*  Read stress tester state IO                                           */
  /*                                                                        */
  /**************************************************************************/
  ar.bits.addr := Cat(0.U(1.W), (baseAddr + (ar_idx * ccx.busBytes.U))).asSInt
  ar.bits.size := (log2Ceil(ccx.busBytes)).U
  ar.bits.len  := FibonacciLFSR.maxPeriod(16, reduction = XNOR, seed = Some(seed + 9), increment = ar_random_stall_module.increment) % maxLen.U
  //ar.bits.lock := false.B
  ar.bits.cache := 0.U


  /**************************************************************************/
  /*                                                                        */
  /*  Read FSM                                                              */
  /*                                                                        */
  /**************************************************************************/

  switch(r_state) {
    is(r_state_init) {
      when(r_repeat < numRepeats.U) {
        r_state := r_state_addr
      }
    }
    is(r_state_addr) {
      ar.valid := true.B
      when(ar.ready && ar.valid) {
        r_state := r_state_data
        r_idx := ar_idx
        r_beats := ar.bits.len + 1.U
      }
    }
    
    
    is(r_state_data) {
      
      r.ready := true.B

      when(r.valid && r.ready) {
        assert(r.bits.resp === Mux(r_idx < bramWords.U, OKAY, DECERR), "Incorrect response for R")
        when(r_beats === 1.U) {
          assert(r.bits.last)
          failed := failed || !(r.bits.last)
        }
        when(r.bits.last) {
          r_state := r_state_init
          r_repeat := r_repeat + 1.U
        } .otherwise {
          r_idx := r_idx + 1.U
          r_beats := r_beats - 1.U
        }
      }
    }
  }
  
  


  /**************************************************************************/
  /*                                                                        */
  /*  Completion logic                                                      */
  /*                                                                        */
  /**************************************************************************/
  io.done := false.B
  // Completion
  when(r_repeat >= numRepeats.U && w_repeat >= numRepeats.U && w_state === w_state_init && r_state === r_state_init) {
    io.success := !failed
    io.done := true.B
  }

}


class BRAMTesterModule(val baseAddr:UInt = "h40000000".asUInt, val bramWords: Int = 2048, val numRepeats: Int = 2000) extends Module {
  val io = IO(new BRAMExerciserIO)

  val ccx = new CCXParams(busBytes = 8)
  val bram = Module(new BRAM(bramWords, baseAddr, ccx))
  val exerciser = Module(new BRAMExerciser(
      seed = 10,
      baseAddr = baseAddr, bramWords = bramWords, allowedBramWords = bramWords,
      numRepeats = numRepeats,
      maxLen = 4,
      bram, ccx))
  bram.io <> exerciser.dbus
  io <> exerciser.io
}

import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
class BRAMSpec extends AnyFlatSpec {
  behavior of "BRAM"
  it should "Basic BRAM test" in {
    simulate("BasicBRAMTester", new BRAMTesterModule(bramWords = 16)) { harness =>
      for (i <- 0 to 300) {
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
      for (i <- 0 to 300) {
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
