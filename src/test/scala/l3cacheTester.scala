package armleocpu



import chisel3._
import chisel3.util._
import chisel3.util.random._


class L3CacheTesterIO extends Bundle {
  val success = Output(Bool())
  val done = Output(Bool())
  val coverage = Output(UInt(32.W))
}


class L3CacheTesterModule()(override implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends CCXModule {
    val io = IO(new L3CacheTesterIO)

    

    val l3cache = Module(new L3CacheBank())
    val bram = Module(new BRAM(
      sizeInWords = (1 << cbp.addrWidth) / cbp.busBytes,
      bp = cbp,
      memoryFile = new BinaryMemoryFile("")
    ))

    l3cache.io.down <> bram.io

    io.success := false.B
    io.done := false.B
    io.coverage := 0.U(0.W)
}


import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec


class L3CacheSpec extends AnyFlatSpec {
  behavior of "L3Cache"
  it should "L3CacheTest" in {
    implicit val ccx = new CCXParams(
      l3 = new L3CacheParams(
        directoryEntriesLog2 = 2,
        directoryWaysLog2 = 2,
        cacheEntriesLog2 = 4,
        cacheWaysLog2 = 4
      )
    )

    implicit val cbp = new CoherentBusParams(
      addrWidth = ccx.cacheLineLog2
        + Math.max(ccx.l3.directoryEntriesLog2, ccx.l3.cacheEntriesLog2)
        + 1)

    simulate("L3CacheTest", new L3CacheTesterModule()) { harness =>
      for (i <- 0 to 10) {
        harness.clock.step(100)
        if (harness.io.done.peek().litValue == 1) {
          //harness.io.success.expect(true.B)
        }
        println("100 cycles done")
      }
      
      //harness.io.done.expect(true.B)
      //harness.io.success.expect(true.B)
      

    }
  }
}
