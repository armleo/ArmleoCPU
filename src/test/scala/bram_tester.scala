package armleocpu



import chisel3._
import chisel3.util._
import chisel3.util.random._


import armleocpu.bus_resp_t._
import chisel3.reflect.DataMirror



class BRAMStressTester(val baseAddr:UInt = "h40000000".asUInt, val bramWords: Int = 2048, val numRepeats: Int = 2000) extends Module {
  /**************************************************************************/
  /*                                                                        */
  /*  Shared stuff                                                          */
  /*                                                                        */
  /**************************************************************************/

  val io = IO(new Bundle {
    val success = Output(Bool())
    val done = Output(Bool())
    val coverage = UInt(32.W)
  })

  val coverage = RegInit(0.U(32.W))
  io.coverage := coverage

  

  val c = new CoreParams(bp = new BusParams(data_bytes = 8))
  val dut = Module(new BRAM(c = c, baseAddr = baseAddr, sizeInWords = bramWords))

  // TODO: Make it sync read mem
  // Mirror and validity tracker
  val mirror = Seq.tabulate(c.bp.data_bytes) {
    f:Int => SyncReadMem(bramWords, UInt(8.W))
  }
  val valid = Seq.tabulate(c.bp.data_bytes) {
    f:Int => SyncReadMem(bramWords, Bool())
  }

  val failed = RegInit(false.B)
  val w_repeat = RegInit(0.U(log2Ceil(numRepeats + 1).W))
  val r_repeat = RegInit(0.U(log2Ceil(numRepeats + 1).W))



  // Defaults
  io.success := false.B

  dut.io <> 0.U.asTypeOf(dut.io.cloneType)

  /**************************************************************************/
  /*                                                                        */
  /*  Write stress tester state                                             */
  /*                                                                        */
  /**************************************************************************/
  
  val (aw, aw_increment, _) = DecoupledIORandomStall(dut.io.aw)
  val (w, w_increment, _) = DecoupledIORandomStall(dut.io.w)

  val b = dut.io.b.cloneType
  val (dut.io.b, _, _) = DecoupledIORandomStall(b)

  // 64 bits is enough for most cases. Dont want to make it depended on bram words value
  val aw_idx = (FibonacciLFSR.maxPeriod(64, reduction = XNOR, increment = aw_increment) & ((bramWords) - 1).U)
  val aw_addr = baseAddr + (aw_idx * c.bp.data_bytes.U)


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

  aw.bits.addr := aw_addr.asSInt
  aw.bits.len  := FibonacciLFSR.maxPeriod(2, reduction = XNOR, increment = aw_increment) //Fixed 16 cycles because more is simply not needed
  aw.bits.size := (log2Ceil(c.bp.data_bytes)).U
  aw.bits.lock := false.B

  w.bits.data  := FibonacciLFSR.maxPeriod(c.bp.data_bytes * 8, reduction = XNOR, increment = w_increment)
  
  w.bits.strb  := FibonacciLFSR.maxPeriod(w.bits.strb.getWidth, reduction = XNOR, increment = w_increment)
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
        for(bytenum <- 0 until c.bp.data_bytes) {
          when(w.bits.strb(bytenum)) {
            mirror(bytenum)(w_idx) := w.bits.data.asTypeOf(Vec(c.bp.data_bytes, UInt(8.W)))(bytenum)
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
      dut.io.b.ready := true.B
      
      when(dut.io.b.valid && dut.io.b.ready) {
        assert(dut.io.b.bits.resp === Mux(w_idx < bramWords.U, OKAY, DECERR), "Incorrect response for B")

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
  val (ar, ar_increment, _) = DecoupledIORandomStall(dut.io.ar)
  val (r, r_increment, _) = DecoupledIORandomStall(dut.io.ar)

  val ar_idx  = (FibonacciLFSR.maxPeriod(64, reduction = XNOR, increment = ar_increment) & ((bramWords) - 1).U)
  val r_beats = Reg(UInt(12.W))
  val r_idx   = Reg(UInt(64.W))

  // Keep the data from the bus so we can compare it when the result from sync read mem is available
  val check_r = RegNext(dut.io.r.valid && dut.io.r.ready)
  val check_r_bits_data = RegNext(dut.io.r.bits.data)

  // Actual data from syncreadmems
  val mirrorread = Wire(Vec(c.bp.data_bytes, UInt(8.W)))
  val validread = Wire(Vec(c.bp.data_bytes, Bool()))
  for(bytenum <- 0 until c.bp.data_bytes) {
    mirrorread(bytenum) := mirror(bytenum)(r_idx)
    validread(bytenum) := valid(bytenum)(r_idx)
  }

  // Last cycle the r data beat came. Compare the data from bus in previous cycle to the mirror read result
  when(check_r) {
    for(bytenum <- 0 until c.bp.data_bytes) {
      when(validread(bytenum)) {
        val datamatch = check_r_bits_data.asTypeOf(Vec(c.bp.data_bytes, UInt(8.W)))(bytenum) === mirrorread(bytenum)
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
  dut.io.ar.bits.addr := (baseAddr + (ar_idx * c.bp.data_bytes.U)).asSInt
  dut.io.ar.bits.size := (log2Ceil(c.bp.data_bytes)).U
  dut.io.ar.bits.len  := FibonacciLFSR.maxPeriod(2, reduction = XNOR, increment = ar_increment) //Fixed 16 cycles because more is simply not needed
  dut.io.ar.bits.lock := false.B


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
      dut.io.ar.valid := !ar_stall
      when(dut.io.ar.ready && dut.io.ar.valid) {
        r_state := r_state_data
        r_idx := ar_idx
        r_beats := dut.io.ar.bits.len + 1.U
      }
    }
    
    
    is(r_state_data) {
      
      dut.io.r.ready := !r_stall

      when(dut.io.r.valid && dut.io.r.ready) {
        assert(dut.io.r.bits.resp === Mux(r_idx < bramWords.U, OKAY, DECERR), "Incorrect response for R")
        when(r_beats === 1.U) {
          assert(dut.io.r.bits.last)
          failed := failed || !(dut.io.r.bits.last)
        }
        when(dut.io.r.bits.last) {
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
  when(r_repeat === numRepeats.U && w_repeat === numRepeats.U && w_state === w_state_init && r_state === r_state_init) {
    io.success := !failed
    io.done := true.B
  }

}


import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
class BRAMSpec extends AnyFlatSpec {
  behavior of "BRAM"
  it should "Basic BRAM test" in {
    simulate("BasicBRAMTester", new BRAMStressTester(bramWords = 16)) { harness =>
      for (i <- 0 to 200) {
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
    simulate("StressBRAMTester", new BRAMStressTester()) { harness =>
      for (i <- 0 to 200) {
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
