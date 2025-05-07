package armleocpu



import chisel3._
import chisel3.util._
import chisel3.util.random._


import armleocpu.bus_resp_t._



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

  // Mirror and validity tracker
  val mirror = SyncReadMem(bramWords, dut.io.r.data.cloneType)
  val valid = SyncReadMem(bramWords, Bool())

  val failed = RegInit(false.B)
  val repeat = RegInit(0.U(log2Ceil(numRepeats + 1).W))



  // Defaults
  io.success := false.B

  dut.io <> 0.U.asTypeOf(dut.io.cloneType)


  /**************************************************************************/
  /*                                                                        */
  /*  Track the recorded address                                            */
  /*                                                                        */
  /**************************************************************************/
  // We can do this because the underlying BRAM can accept only one request at a time
  // Otherwise we would have to track each request separately

  val aw_saved_addr_valid = RegInit(false.B)
  val aw_saved_addr = RegInit(0.U.asTypeOf(dut.io.aw.addr))
  val aw_saved_size = RegInit(0.U.asTypeOf(dut.io.aw.size))


  when (dut.io.aw.valid && dut.io.aw.ready) {
    assert(!aw_saved_addr_valid)
    // Keep track that the address is valid
    aw_saved_addr_valid := true.B
    // Save the address
    aw_saved_addr := dut.io.aw.addr
    aw_saved_size := dut.io.aw.size
  }

  when (dut.io.w.valid && dut.io.w.ready) {
    mirror(aw_saved_addr.asUInt + (1.U << aw_saved_size))

    // Write to saved address
  }

  when (dut.io.b.valid && dut.io.b.ready) {
    // Check if address was inside then check the response equals to OKAY
    aw_saved_addr_valid := false.B
  }



  
  val ar_saved_valid = RegInit(false.B)
  val ar_saved_addr = RegInit(0.U.asTypeOf(dut.io.aw.addr))
  val ar_saved_size = RegInit(0.U.asTypeOf(dut.io.ar.size))
  val ar_read_addr = WireDefault((ar_saved_addr.asUInt + (1.U << ar_saved_size)) >> log2Up(c.bp.data_bytes))
  val ar_saved_data = mirror(ar_read_addr)
  val ar_saved_data_valid = valid(ar_read_addr)
  
  
  when (dut.io.ar.valid && dut.io.ar.ready) {
    assert(!ar_saved_valid)
    // Keep track that the address is valid
    ar_saved_valid := true.B
    // Save the address
    ar_saved_addr := dut.io.ar.addr
    // Save the increment count
    ar_saved_size := dut.io.ar.size

    ar_read_addr := dut.io.ar.addr.asUInt >> log2Up(c.bp.data_bytes)
  }

  when (dut.io.r.valid && dut.io.r.ready) {
    ar_read_addr := (ar_saved_addr + (1.S << ar_saved_size)).asUInt >> log2Up(c.bp.data_bytes)
    ar_saved_addr := (ar_saved_addr + (1.S << ar_saved_size))
    when(ar_saved_data_valid) {
      assert(dut.io.r.data === ar_saved_data)
    }
    
    when(dut.io.r.last) {
      ar_saved_valid := false.B
    }
    // Check against the mirror
  }

  
  val stalls = FibonacciLFSR.maxPeriod(8, reduction = XNOR, seed = Some(2))

  /**************************************************************************/
  /*                                                                        */
  /*  Write stress tester                                                   */
  /*                                                                        */
  /**************************************************************************/
  
  val randWriteAddr = (FibonacciLFSR.maxPeriod(bramWords, reduction = XNOR) & ((bramWords) - 1).U) << log2Up(c.bp.data_bytes)

  
  val randWstall = (stalls >> 0) & 1.U
  val randBstall = (stalls >> 1) & 1.U

  val randWrite = FibonacciLFSR.maxPeriod(c.bp.data_bytes * 8, reduction = XNOR)
  val randWriteLen = FibonacciLFSR.maxPeriod(4, reduction = XNOR) //Fixed 4 cycles because more is simply not needed


  // Write state
  val w_state_init = 0.U(2.W)
  val w_state_addr = 1.U(2.W)
  val w_state_data = 2.U(2.W)
  val w_state_resp = 3.U(2.W)

  val w_state = RegInit(w_state_init)
  val w_addr = Reg(UInt(c.apLen.W))
  val w_len = Reg(dut.io.aw.len.cloneType)


  dut.io.aw.size := (log2Up(c.bp.data_bytes)).U
  dut.io.aw.len  := 0.U



  // === WRITE FSM ===
  switch(w_state) {
    is(w_state_init) {
      when(repeat < numRepeats.U) {
        //w_len := randomLen(randWrite)
        w_addr := baseAddr + randWriteAddr
        w_state := w_state_addr
      }
    }
    is(w_state_addr) {
      dut.io.aw.valid := true.B
      dut.io.aw.addr := w_addr.asSInt
      //dut.io.aw.size := 2.U
      when(dut.io.aw.ready) {
        w_state := w_state_data
      }
    }
    is(w_state_data) {
      val bramIdx = ((w_addr - baseAddr) / c.bp.data_bytes.U)
      dut.io.w.valid := true.B
      dut.io.w.strb  := -1.S(dut.io.w.strb.getWidth.W).asTypeOf(dut.io.w.strb)
      dut.io.w.last := true.B
      dut.io.w.data := randWrite

      when(dut.io.w.ready) {
        mirror(bramIdx) := dut.io.w.data
        valid(bramIdx) := true.B
        when(dut.io.w.last) {
          w_state := w_state_resp
        }
      }
    }
    is(w_state_resp) {
      dut.io.b.ready := true.B
      assert(dut.io.b.resp === OKAY, "Incorrect response for B")
      when(dut.io.b.valid) {
        w_state := w_state_init
      }

      when(repeat < numRepeats.U) {
        w_len := randWriteLen
        w_addr := baseAddr + randWriteAddr
        w_state := w_state_addr
      }
    }
  }

  

  /**************************************************************************/
  /*                                                                        */
  /*  Read stress tester                                                    */
  /*                                                                        */
  /**************************************************************************/
  val randReadAddr = (FibonacciLFSR.maxPeriod(bramWords, reduction = XNOR) & ((bramWords) - 1).U) << log2Up(c.bp.data_bytes)
  val randRstall = (stalls >> 2) & 1.U
  val randReadLen = FibonacciLFSR.maxPeriod(4, reduction = XNOR) //Fixed 4 cycles because more is simply not needed
  val randRead = FibonacciLFSR.maxPeriod(c.bp.data_bytes * 8, reduction = XNOR)

  // Read state
  val r_state_init = 0.U
  val r_state_addr = 1.U
  val r_state_data = 2.U
  
  val r_state = RegInit(r_state_init)
  val r_addr = Reg(UInt(c.apLen.W))
  val r_len = Reg(dut.io.ar.len.cloneType)

  dut.io.ar.size := (log2Up(c.bp.data_bytes)).U
  dut.io.ar.len  := 0.U
  
  // === READ FSM ===
  switch(r_state) {
    is(r_state_init) {
      when(repeat < numRepeats.U) {
        //r_len := randomLen(randRead)
        r_addr := baseAddr + randReadAddr
        r_state := r_state_addr
      }
    }
    is(r_state_addr) {
      dut.io.ar.valid := true.B
      dut.io.ar.addr := r_addr.asSInt
      //dut.io.ar.len := r_len - 1.U
      //dut.io.ar.size := 2.U
      //dut.io.ar.burst := 1.U
      when(dut.io.ar.ready) {
        r_state := r_state_data
      }
    }
    is(r_state_data) {
      when(dut.io.r.valid) {
        val bramIdx = ((r_addr - baseAddr) / c.bp.data_bytes.U)
        when(valid(bramIdx)) {
          assert(dut.io.r.data === mirror(bramIdx))
          coverage := coverage + 1.U
        }
        
        assert(dut.io.b.resp === OKAY, "Incorrect response for R")
        when(dut.io.r.last) {
          r_state := r_state_init
          repeat := repeat + 1.U
        }
      }
      dut.io.r.ready := true.B
    }
  }
  



  /**************************************************************************/
  /*                                                                        */
  /*  Completion logic                                                      */
  /*                                                                        */
  /**************************************************************************/
  io.done := false.B
  // Completion
  when(repeat === numRepeats.U && w_state === w_state_init && r_state === r_state_init) {
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
      for (i <- 0 to 120) {
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
      for (i <- 0 to 120) {
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
