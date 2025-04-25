package armleocpu



import chisel3._
import chisel3.util._
import chisel3.util.random._
import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec


class BRAMSpec extends AnyFlatSpec {

  class BRAMStressTest(val baseAddr:UInt = "h40000000".asUInt, val bramWords: Int = 2048, val numRepeats: Int = 2000) extends Module {
     val io = IO(new Bundle {
      val success = Output(Bool())
      val done = Output(Bool())
    })


    val c = new CoreParams(bp = new BusParams(data_bytes = 8))
    val dut = Module(new BRAM(c = c, baseAddr = baseAddr, size = bramWords * c.bp.data_bytes))

    // Mirror and validity tracker
    val mirror = Mem(bramWords, dut.io.r.data.cloneType)
    val valid = Mem(bramWords, Bool())

    val failed = RegInit(false.B)

    val rand = GaloisLFSR.maxPeriod(bramWords, reduction = XNOR)
    val randWrite = GaloisLFSR.maxPeriod(c.bp.data_bytes * 8, reduction = XNOR)
    val randRead = GaloisLFSR.maxPeriod(c.bp.data_bytes * 8, reduction = XNOR)

    // Random parameters
    def randomAddr(bits: UInt) = bits << log2Up(c.bp.data_bytes)

    // Write state
    val w_state_idle = 0.U
    val w_state_addr = 1.U
    val w_state_data = 2.U
    val w_state_resp = 3.U

    val w_state = RegInit(w_state_idle)
    val w_addr = Reg(UInt(c.apLen.W))

    // Read state
    val r_state_idle = 0.U
    val r_state_addr = 1.U
    val r_state_data = 2.U
    
    val r_state = RegInit(r_state_idle)
    val r_addr = Reg(UInt(c.apLen.W))



    val repeat = RegInit(0.U(log2Ceil(numRepeats + 1).W))


    // Defaults
    io.success := false.B

    dut.io <> 0.U.asTypeOf(dut.io.cloneType)

    dut.io.aw.size := (log2Up(c.bp.data_bytes)).U
    dut.io.ar.size := (log2Up(c.bp.data_bytes)).U
    dut.io.ar.len  := 0.U
    dut.io.aw.len  := 0.U

    // === WRITE FSM ===
    switch(w_state) {
      is(w_state_idle) {
        when(repeat < numRepeats.U) {
          //w_len := randomLen(randWrite)
          w_addr := baseAddr + randomAddr(rand)
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
        when(dut.io.b.valid) {
          w_state := w_state_idle
        }
      }
    }

    // === READ FSM ===
    switch(r_state) {
      is(r_state_idle) {
        when(repeat < numRepeats.U) {
          //r_len := randomLen(randRead)
          r_addr := baseAddr + randomAddr(randRead)
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
          when(valid(bramIdx) && dut.io.r.data =/= mirror(bramIdx)) {
            r_state := r_state_idle
            repeat := numRepeats.U // force finish
            failed := true.B
            printf("AXI4 R data mismatch!")
          }
          when(dut.io.r.last) {
            r_state := r_state_idle
            repeat := repeat + 1.U
          }
        }
        dut.io.r.ready := true.B
      }
    }

    io.done := false.B
    // Completion
    when(repeat === numRepeats.U && w_state === w_state_idle && r_state === r_state_idle) {
      io.success := !failed
      io.done := true.B
    }
  }

  
  behavior of "BRAM"
  it should "Basic BRAM test" in {
    simulate(new BRAMStressTest) { harness =>
      for (i <- 0 to 120) {
        harness.clock.step(100)
        harness.io.success.expect(true.B)
        print("100 cycles done")
      }
      
      

    }
  }
}
