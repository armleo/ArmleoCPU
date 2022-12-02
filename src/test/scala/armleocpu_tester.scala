package armleocpu


import chiseltest._
import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.WriteVcdAnnotation
import java.io._
import java.nio.ByteBuffer


class ArmleoCPUSpec extends AnyFreeSpec with ChiselScalatestTester {
  val c = new CoreParams(
    itlb = new TlbParams(entries = 4, ways = 2),
    icache = new CacheParams(entries = 8, entry_bytes = 32),
    bp = new BusParams(data_bytes = 16),
    reset_vector = 0
  )
  "ArmleoCPU should run example programs" in {
    test(new ArmleoCPU(c)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val bis = new BufferedInputStream(new FileInputStream("tests/verif_tests/verif_isa_tests/output/lw.bin"))
      val bArray = LazyList.continually(bis.read).takeWhile(i => -1 != i).map(_.toByte).toArray

      val memory = new Array[Byte](64 * 1024)
      System.arraycopy(bArray, 0, memory, 0, bArray.length)

      class read_ctx {
        var state = 0
        var substate = 0
        var addr: BigInt = 0
        var len = dut.ibus.ar.len.peek().litValue +  1
      }
      

      def memory_read_step(ctx: read_ctx, ibus: ibus_t, dut: ArmleoCPU): Unit = {
        ibus.r.last.poke(false)
        ibus.ar.ready.poke(false)
        ibus.r.valid.poke(false)
        if(ctx.state == 0) {
          if(ibus.ar.valid.peek().litValue != 0) {
            ctx.state = 1
          }
          ctx.addr = ibus.ar.addr.peek().litValue
          ctx.substate = 0
        } else if(ctx.state == 1) {
          ibus.ar.valid.expect(true)
          ibus.ar.len.expect(ctx.len - 1)
          ibus.ar.ready.poke(true)

          ctx.state = 2
        } else if(ctx.state == 2) {
          ibus.ar.ready.poke(false)
          ibus.ar.valid.expect(false)

          if(ctx.substate == 1) {
            ibus.r.valid.poke(true)
            val arr = Array.concat(bArray.slice(ctx.addr.toInt, ctx.addr.toInt + c.bp.data_bytes), new Array[Byte](1))
            ibus.r.data.poke(BigInt(arr.toSeq.reverse.toArray))

            ctx.addr = ctx.addr + c.bp.data_bytes
            ctx.substate = 0
          } else {
            ctx.substate = 1
          }
          
          ibus.r.ready.expect(true)
          
          

          ctx.len = ctx.len - 1
          if(ctx.len == 0) {
            ctx.state = 0
            ibus.r.last.poke(true)
          }
        }
      }


      val rctx: read_ctx = new read_ctx
      for(i <- 0 until 600) {
        memory_read_step(rctx, dut.ibus, dut)
        dut.clock.step(1)
      }
      // FIXME: Add the dbus interface
      // FIXME: Add the check at the end for the fail/pass value in memory
      // FIXME: Load memory once from binary file into memory
    }
  }
}
