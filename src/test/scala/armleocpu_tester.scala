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
        ibus.ar.ready.poke(false)
        ibus.r.valid.poke(false)
        ibus.r.data.poke(0)
        ibus.r.last.poke(false)

        if(ctx.state == 0) {
          ctx.addr = ibus.ar.addr.peek().litValue
          ctx.substate = 0
          ctx.len = ibus.ar.len.peek().litValue + 1
          
          if(ibus.ar.valid.peek().litValue != 0) {
            ctx.state = 1
            println(f"memory_read_step: Memory request addr: ${ctx.addr} len: ${ctx.len}")
          }
          
        } else if(ctx.state == 1) {
          ibus.ar.addr.expect(ctx.addr)
          ibus.ar.valid.expect(true)
          ibus.ar.len.expect(ctx.len - 1)
          ibus.ar.ready.poke(true)

          ctx.state = 2
          println(f"memory_read_step: Memory request wait cycle, addr: ${ctx.addr} len: ${ctx.len}")
        } else if(ctx.state == 2) {
          ibus.ar.ready.poke(false)
          ibus.ar.valid.expect(false)
          ibus.r.valid.poke(false)
          ibus.r.data.poke(0)
          ibus.r.last.poke(false)

          if(ctx.substate == 1) {
            
            ibus.r.valid.poke(true)
            val arr = Array.concat(bArray.slice(ctx.addr.toInt, ctx.addr.toInt + c.bp.data_bytes), new Array[Byte](1))
            ibus.r.data.poke(BigInt(arr.toSeq.reverse.toArray))
            println(f"memory_read_step: Memory data data cycle, addr: ${ctx.addr} len: ${ctx.len} data: ${arr.toSeq}")
            ctx.addr = ctx.addr + c.bp.data_bytes
            ctx.substate = 0

            if(ctx.len == 1) {
              ctx.state = 0
              ibus.r.last.poke(true)
            }

            ctx.len = ctx.len - 1
          } else {
            println(f"memory_read_step: Memory data wait cycle, addr: ${ctx.addr} len: ${ctx.len}")
            ctx.substate = 1
          }
          
          ibus.r.ready.expect(true) 
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
