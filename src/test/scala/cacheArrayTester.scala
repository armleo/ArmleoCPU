package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random.FibonacciLFSR
import chisel3.util.random.XNOR


class CacheArrayTesterModuleCCXTestCase extends CCXParams() {
  
  override def busBytes: Int = 2
  override def cacheLineLog2: Int = 2

  override val core = new CoreParams(dcache = new CacheParams(waysLog2 = 1, entriesLog2 = 2))
}

class CacheArrayTesterModule(implicit val ccx: CCXParams, implicit val cp: CacheParams, seed: BigInt = 128) extends Module {
  val io = IO(new Bundle {
    val done      = Output(Bool())
    val success   = Output(Bool()) // Only valid if done
    val coverage  = Output(UInt(32.W))
  })

  val dut = Module(new CacheArray)

  val ways      = 1 << cp.waysLog2
  val lineBytes = 1 << ccx.cacheLineLog2
  val sets      = cp.entries

  // Reference memory: set -> way -> byte
  val refData = RegInit(VecInit(Seq.fill(sets)(VecInit(Seq.fill(ways)(VecInit(Seq.fill(lineBytes)(0.U(8.W))))))))
  val refDataValid = RegInit(VecInit(Seq.fill(sets)(VecInit(Seq.fill(ways)(VecInit(Seq.fill(lineBytes)(false.B)))))))

  val refMeta = RegInit(VecInit(Seq.fill(sets)(VecInit(Seq.fill(ways)(0.U.asTypeOf(new CacheMeta))))))
  val refMetaValid = RegInit(VecInit(Seq.fill(sets)(VecInit(Seq.fill(ways)(false.B)))))

  val totalTests = 1024.U
  val testCount  = RegInit(0.U(64.W))

  // Separate LFSRs
  val lfsrAddr  = FibonacciLFSR.maxPeriod(ccx.apLen, reduction = XNOR, seed = Some(seed + 1))
  val lfsrWay   = FibonacciLFSR.maxPeriod(16, reduction = XNOR, seed = Some(seed + 2)) % ways.U
  val lfsrMeta  = FibonacciLFSR.maxPeriod(100, reduction = XNOR, seed = Some(seed + 3))
  val lfsrMask  = FibonacciLFSR.maxPeriod(16, reduction = XNOR, seed = Some(seed + 4))

  val req        = Wire(new CacheArrayReq)

  // Generate request
  req.addr := lfsrAddr
  req.metaWrite := lfsrMask(0)
  req.metaWdata := VecInit(Seq.fill(ways)(lfsrMeta.asTypeOf(new CacheMeta)))
  req.metaMask  := VecInit(Seq.fill(ways)(lfsrMask(1))).asUInt

  req.dataWrite := lfsrMask(2)
  req.dataWayIdx := lfsrWay(0, cp.waysLog2-1)
  req.dataWdata := VecInit(Seq.tabulate(lineBytes)(b => FibonacciLFSR.maxPeriod(8, reduction = XNOR, seed = Some(seed + b + 6))))
  req.dataMask := VecInit(Seq.tabulate(lineBytes)(b => FibonacciLFSR.maxPeriod(8, reduction = XNOR, seed = Some(seed + b + 14))))

  // Hook DUT
  dut.io.req.bits  := req
  dut.io.req.valid := false.B

  val sIdle :: sWait :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  val errorFlag = RegInit(false.B)
  val preservedReq = Reg(req.cloneType)

  val coverage = RegInit(0.U(32.W))

  switch(state) {
    is(sIdle) {
      // Set request
      dut.io.req.valid := true.B
      state := sWait
      preservedReq := req
    }
    is(sWait) {
      when(dut.io.resp.valid) {
        val setIdx = preservedReq.addr(ccx.cacheLineLog2 + cp.entriesLog2 - 1, ccx.cacheLineLog2)
        val wayIdx = preservedReq.dataWayIdx

        // Update reference memory if write
        when(preservedReq.dataWrite) {
          for (b <- 0 until lineBytes) {
            when(preservedReq.dataMask(b)) {
              refData(setIdx)(wayIdx)(b) := preservedReq.dataWdata(b)
              refDataValid(setIdx)(wayIdx)(b) := true.B
            }
          }
        }

        // Update meta if write
        when(preservedReq.metaWrite) {
          for (w <- 0 until ways) {
            when(preservedReq.metaMask(w)) {
              refMeta(setIdx)(w) := preservedReq.metaWdata(w)
              refMetaValid(setIdx)(w) := true.B
            }
          }
        }

        
        when(!preservedReq.dataWrite) {
          // Check DUT data against reference, only if valid
          val dutData = dut.io.resp.bits.dataRdata
          for (w <- 0 until ways) {
            for (b <- 0 until lineBytes) {
              val flatIdx = w * lineBytes + b
              when(refDataValid(setIdx)(w)(b) && dutData(flatIdx) =/= refData(setIdx)(w)(b)) {
                errorFlag := true.B
              }
            }
          }

          coverage := coverage + 1.U
        }

        when(!preservedReq.metaWrite) {
          // Check DUT meta
          val dutMeta = dut.io.resp.bits.metaRdata
          for (w <- 0 until ways) {
            when(refMetaValid(setIdx)(w) && !(dutMeta(w) === refMeta(setIdx)(w))) {
              errorFlag := true.B
            }
          }

          coverage := coverage + 1.U
        }

        testCount := testCount + 1.U
        when(testCount === totalTests - 1.U) {
          state := sDone
        }.otherwise {
          state := sIdle
        }
      }
    }
    is(sDone) {}
  }

  io.done := (state === sDone)
  io.success := (state === sDone) && !errorFlag

  io.coverage := coverage
}


import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class CacheArrayTest extends AnyFlatSpec {

  implicit val ccx:CCXParams = new CacheArrayTesterModuleCCXTestCase()

  implicit val cp:CacheParams = ccx.core.icache

  it should "Cache array test" in {
    simulate("CacheArrayTester", new CacheArrayTesterModule()) { harness =>
      for (i <- 0 to 50) {
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
